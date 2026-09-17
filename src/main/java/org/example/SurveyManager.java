package org.example;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class SurveyManager {

    private static final int SURVEY_DURATION_SECONDS = 300;
    private static final int REMINDER_DELAY_SECONDS = 180;

    private final CommunityManager communityManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private Survey currentSurvey;
    private List<SurveyParticipant> currentParticipants;

    private final AtomicReference<ScheduledFuture<?>> countdownTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> reminderTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> timeoutTask = new AtomicReference<>();

    private final CopyOnWriteArrayList<SurveyListener> surveyListeners = new CopyOnWriteArrayList<>();
    private int secondsRemaining;
    private boolean reminderSent;
    private boolean isPendingPhase;

    public SurveyManager(CommunityManager communityManager) {
        this.communityManager = communityManager;
    }

    public synchronized void createSurvey(List<Question> questions, int delayMinutes) {
        if (isSurveyInProgress()) {
            throw new IllegalStateException("סקר פעיל כבר קיים. סיים אותו קודם.");
        }

        if (communityManager.getCommunitySize() < 3) {
            throw new IllegalStateException("צריכים לפחות 3 חברים בקהילה כדי להתחיל סקר.");
        }
        currentSurvey = new Survey(questions, delayMinutes);
        currentParticipants = new CopyOnWriteArrayList<>();

        for (CommunityUser user : communityManager.getAllMembers()) {
            currentParticipants.add(new SurveyParticipant(user));
        }

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
            secondsRemaining--;
            notifyCountdownTick(secondsRemaining, isPendingPhase);

            if (secondsRemaining <= 0) {
                ScheduledFuture<?> t = countdownTask.get();
                if (t != null) {
                    t.cancel(false);
                }
                startSurvey();
            }
        }, 1, 1, TimeUnit.SECONDS);

        countdownTask.set(task);
    }

    private synchronized void startSurvey() {
        currentSurvey.setStatus(SurveyStatus.ACTIVE);
        currentSurvey.setStartTime(LocalDateTime.now());

        reminderSent = false;
        secondsRemaining = SURVEY_DURATION_SECONDS;
        isPendingPhase = false;

        notifyListenersOnSurveyStarted();

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            secondsRemaining--;
            notifyCountdownTick(secondsRemaining, false);

            if (secondsRemaining <= 0) {
                closeSurvey();
            }
        }, 1, 1, TimeUnit.SECONDS);

        countdownTask.set(task);

        ScheduledFuture<?> reminderTaskRef = scheduler.schedule(
                this::sendRemindersIfNeeded, REMINDER_DELAY_SECONDS, TimeUnit.SECONDS);

        reminderTask.set(reminderTaskRef);
    }

    private void notifyListenersOnSurveyStarted() {
        for (SurveyListener listener : surveyListeners) {
            listener.onSurveyStarted(currentSurvey, new ArrayList<>(currentParticipants));
        }
    }

    private void notifyCountdownTick(int secondsLeft, boolean isPending) {
        for (SurveyListener listener : surveyListeners) {
            listener.onCountdownTick(secondsLeft, isPending);
        }
    }

    public enum AnswerResult { RECORDED, SURVEY_NOT_ACTIVE, ALREADY_ANSWERED, UNKNOWN_PARTICIPANT }
    public synchronized AnswerResult recordAnswer(long telegramId, String questionId, String answer) {
        if (!isSurveyInProgress()) {
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

        ScheduledFuture<?> countdownRef = countdownTask.get();
        if (countdownRef != null) {
            countdownRef.cancel(false);
            countdownTask.set(null);
        }

        ScheduledFuture<?> reminderRef = reminderTask.get();
        if (reminderRef != null) {
            reminderRef.cancel(false);
            reminderTask.set(null);
        }

        ScheduledFuture<?> timeoutRef = timeoutTask.get();
        if (timeoutRef != null) {
            timeoutRef.cancel(false);
            timeoutTask.set(null);
        }

        currentSurvey.setStatus(SurveyStatus.COMPLETED);

        for (SurveyListener listener : surveyListeners) {
            listener.onSurveyClosed(currentSurvey, new ArrayList<>(currentParticipants));
        }

        currentSurvey = null;
        currentParticipants = null;
    }

    private synchronized void sendRemindersIfNeeded() {
        if (reminderSent || !isSurveyInProgress()) {
            return;
        }
        reminderSent = true;

        List<SurveyParticipant> notCompleted = new ArrayList<>();
        for (SurveyParticipant p : currentParticipants) {
            if (p.getStatus() != ParticipantStatus.COMPLETED) {
                notCompleted.add(p);
            }
        }
        for (SurveyListener listener : surveyListeners) {
            listener.onReminderSent(notCompleted);
        }
    }

    public boolean isSurveyInProgress() {
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
        for (SurveyListener listener : surveyListeners) {
            listener.onAnswerRecorded(participant);
        }
    }

    public void addSurveyListener(SurveyListener listener) {
        surveyListeners.add(listener);
    }

    public void removeSurveyListener(SurveyListener listener) {
        surveyListeners.remove(listener);
    }

    public Survey getCurrentSurvey() {
        return currentSurvey;
    }

    public List<SurveyParticipant> getCurrentParticipants() {
        return currentParticipants != null ? new ArrayList<>(currentParticipants) : new ArrayList<>();
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}