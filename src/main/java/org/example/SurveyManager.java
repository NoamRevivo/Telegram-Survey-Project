package org.example;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SurveyManager {

    private static final Logger LOG = Logger.getLogger(SurveyManager.class.getName());

    public static final int SURVEY_DURATION_SECONDS = 300;
    private static final int REMINDER_DELAY_SECONDS = 180;
    /** אזהרה אחרונה בטלגרם ב-30 השניות שלפני סגירת הסקר */
    public static final int FINAL_WARNING_SECONDS_BEFORE_END = 30;
    public static final int MIN_COMMUNITY_SIZE = 3;

    private final CommunityManager communityManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private volatile Survey currentSurvey;
    private volatile List<SurveyParticipant> currentParticipants;

    private final AtomicReference<ScheduledFuture<?>> countdownTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> reminderTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> finalWarningTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> timeoutTask = new AtomicReference<>();

    private final CopyOnWriteArrayList<SurveyListener> surveyListeners = new CopyOnWriteArrayList<>();
    private volatile int secondsRemaining;
    private volatile boolean reminderSent;
    private volatile boolean finalWarningSent;
    private volatile boolean isPendingPhase;

    public SurveyManager(CommunityManager communityManager) {
        this.communityManager = communityManager;
    }

    public synchronized void createSurvey(List<Question> questions, int delayMinutes) {
        if (isSurveyInProgress()) {
            throw new IllegalStateException("סקר פעיל כבר קיים. סיים אותו קודם.");
        }
        if (communityManager.getCommunitySize() < MIN_COMMUNITY_SIZE) {
            throw new IllegalStateException("צריכים לפחות " + MIN_COMMUNITY_SIZE + " חברים בקהילה כדי להתחיל סקר.");
        }
        currentSurvey = new Survey(questions, delayMinutes);
        currentParticipants = new CopyOnWriteArrayList<>();

        if (delayMinutes > 0) {
            startCountdown(delayMinutes);
        } else {
            startSurvey();
        }
    }

    private void startCountdown(int delayMinutes) {
        currentSurvey.setStatus(SurveyStatus.PENDING);
        secondsRemaining = delayMinutes * 60;
        isPendingPhase = true;

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                secondsRemaining--;
                notifyCountdownTick(secondsRemaining, true);
                if (secondsRemaining <= 0) {
                    ScheduledFuture<?> t = countdownTask.get();
                    if (t != null) {
                        t.cancel(false);
                    }
                    startSurvey();
                }
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "טיק ספירה לאחור נכשל", e);
            }
        }, 1, 1, TimeUnit.SECONDS);

        countdownTask.set(task);
    }

    private synchronized void startSurvey() {
        if (currentSurvey == null || currentSurvey.getStatus() != SurveyStatus.PENDING) {
            return;
        }
        // M-02: המשתתפים = חברי הקהילה ברגע שהסקר יוצא בפועל (דרישה 5)
        for (CommunityUser user : communityManager.getAllMembers()) {
            currentParticipants.add(new SurveyParticipant(user));
        }
        currentSurvey.setStatus(SurveyStatus.ACTIVE);
        currentSurvey.setStartTime(LocalDateTime.now());

        reminderSent = false;
        finalWarningSent = false;
        secondsRemaining = SURVEY_DURATION_SECONDS;
        isPendingPhase = false;

        notifyListenersOnSurveyStarted();

        // C-01: טיק לתצוגה בלבד — לא אחראי על סגירת הסקר, ועטוף ב-try/catch
        countdownTask.set(scheduler.scheduleAtFixedRate(() -> {
            try {
                secondsRemaining = Math.max(0, secondsRemaining - 1);
                notifyCountdownTick(secondsRemaining, false);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "טיק ספירה לאחור נכשל", e);
            }
        }, 1, 1, TimeUnit.SECONDS));

        // C-01: סגירה קשיחה אחרי 5 דקות — לא תלויה בטיקים ולא במאזינים
        timeoutTask.set(scheduler.schedule(this::closeSurvey, SURVEY_DURATION_SECONDS, TimeUnit.SECONDS));
        reminderTask.set(scheduler.schedule(
                () -> sendRemindersIfNeeded(false), REMINDER_DELAY_SECONDS, TimeUnit.SECONDS));
        finalWarningTask.set(scheduler.schedule(
                () -> sendRemindersIfNeeded(true),
                SURVEY_DURATION_SECONDS - FINAL_WARNING_SECONDS_BEFORE_END, TimeUnit.SECONDS));
    }

    private void notifyListenersOnSurveyStarted() {
        Survey survey = currentSurvey;
        List<SurveyParticipant> snapshot = new ArrayList<>(currentParticipants);
        fire(l -> l.onSurveyStarted(survey, snapshot));
    }

    private void notifyCountdownTick(int secondsLeft, boolean isPending) {
        fire(l -> l.onCountdownTick(secondsLeft, isPending));
    }

    /** C-01: מאזין שזורק חריגה לא מפיל את שאר המאזינים ולא את הטיימר. */
    private void fire(Consumer<SurveyListener> event) {
        for (SurveyListener listener : surveyListeners) {
            try {
                event.accept(listener);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "מאזין סקר נכשל: " + listener.getClass().getName(), e);
            }
        }
    }

    public enum AnswerResult { RECORDED, SURVEY_NOT_ACTIVE, ALREADY_ANSWERED, UNKNOWN_PARTICIPANT }
    public synchronized AnswerResult recordAnswer(long telegramId, String questionId, String answer) {
        if (currentSurvey == null || currentSurvey.getStatus() != SurveyStatus.ACTIVE) {
            return AnswerResult.SURVEY_NOT_ACTIVE;
        }
        SurveyParticipant participant = findParticipant(telegramId);
        if (participant == null) {
            return AnswerResult.UNKNOWN_PARTICIPANT;
        }
        if (participant.hasAnswered(questionId)) {
            return AnswerResult.ALREADY_ANSWERED;
        }

        participant.recordAnswer(questionId, answer, currentSurvey.getQuestions().size());
        notifyListenersAnswerRecorded(participant);

        if (allParticipantsCompleted()) {
            closeSurvey();
        }
        return AnswerResult.RECORDED;
    }

    public synchronized void closeSurvey() {
        if (currentSurvey == null || currentSurvey.getStatus() == SurveyStatus.COMPLETED) {
            return;
        }

        cancel(countdownTask);
        cancel(reminderTask);
        cancel(finalWarningTask);
        cancel(timeoutTask);

        currentSurvey.setStatus(SurveyStatus.COMPLETED);

        Survey closed = currentSurvey;
        List<SurveyParticipant> snapshot = new ArrayList<>(currentParticipants);
        fire(l -> l.onSurveyClosed(closed, snapshot));

        currentSurvey = null;
        currentParticipants = null;
    }

    private void cancel(AtomicReference<ScheduledFuture<?>> taskRef) {
        ScheduledFuture<?> task = taskRef.getAndSet(null);
        if (task != null) {
            task.cancel(false);
        }
    }

    /**
     * שולח תזכורת למי שטרם השלים את הסקר.
     * isFinalWarning מבדיל בין התזכורת בדקה ה-3 לאזהרה האחרונה שלפני הסגירה,
     * וכל אחת מהן נשלחת לכל היותר פעם אחת בסקר.
     */
    private synchronized void sendRemindersIfNeeded(boolean isFinalWarning) {
        if (!isSurveyInProgress() || currentSurvey.getStatus() != SurveyStatus.ACTIVE) {
            return;
        }
        if (isFinalWarning ? finalWarningSent : reminderSent) {
            return;
        }
        if (isFinalWarning) {
            finalWarningSent = true;
        } else {
            reminderSent = true;
        }

        List<SurveyParticipant> notCompleted = new ArrayList<>();
        for (SurveyParticipant p : currentParticipants) {
            if (p.getStatus() != ParticipantStatus.COMPLETED) {
                notCompleted.add(p);
            }
        }
        if (notCompleted.isEmpty()) {
            return;
        }
        Survey survey = currentSurvey;
        fire(l -> l.onReminderSent(survey, notCompleted, isFinalWarning));
    }

    public synchronized boolean isSurveyInProgress() {
        return currentSurvey != null &&
                (currentSurvey.getStatus() == SurveyStatus.PENDING ||
                        currentSurvey.getStatus() == SurveyStatus.ACTIVE);
    }

    private SurveyParticipant findParticipant(long telegramId) {
        for (SurveyParticipant p : currentParticipants) {
            if (p.getUser().getTelegramId() == telegramId) {
                return p;
            }
        }
        return null;
    }

    private boolean allParticipantsCompleted() {
        for (SurveyParticipant p : currentParticipants) {
            if (p.getStatus() != ParticipantStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private void notifyListenersAnswerRecorded(SurveyParticipant participant) {
        fire(l -> l.onAnswerRecorded(participant));
    }

    public void addSurveyListener(SurveyListener listener) {
        surveyListeners.add(listener);
    }

    public void removeSurveyListener(SurveyListener listener) {
        surveyListeners.remove(listener);
    }

    public synchronized Survey getCurrentSurvey() {
        return currentSurvey;
    }

    public synchronized List<SurveyParticipant> getCurrentParticipants() {
        return currentParticipants != null ? new ArrayList<>(currentParticipants) : new ArrayList<>();
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}