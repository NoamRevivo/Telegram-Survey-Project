package org.example;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * מתאם הסקר: מחזיק את המצב ({@link SurveyState}), מתזמן דרך {@link SurveyScheduler}
 * ומודיע למאזינים דרך {@link Listeners}.
 * <p>
 * R5-M01: כל הודעה למאזינים נשלחת <b>מחוץ</b> למנעול (copy-then-notify) —
 * ה-EDT לעולם אינו מחכה למנעול שמוחזק בזמן שמריצים קוד זר.
 */
public class SurveyManager {

    public enum AnswerResult { RECORDED, SURVEY_NOT_ACTIVE, ALREADY_ANSWERED, UNKNOWN_PARTICIPANT }

    /* ===== גשר זמני עד סוף חלק 3 — למחוק! ראה "ניקוי" בהמשך ===== */
    public static final int SURVEY_DURATION_SECONDS = AppConfig.SURVEY_DURATION_SECONDS;
    public static final int FINAL_WARNING_SECONDS_BEFORE_END = AppConfig.FINAL_WARNING_SECONDS_BEFORE_END;
    public static final int MIN_COMMUNITY_SIZE = AppConfig.MIN_COMMUNITY_SIZE;

    private final CommunityManager communityManager;
    private final SurveyScheduler scheduler;
    private final Listeners<SurveyListener> listeners = new Listeners<>();

    /* כל השדות הבאים מוגנים על ידי this */
    private SurveyState state;
    private SurveyScheduler.Cancellable countdownTask;
    private SurveyScheduler.Cancellable reminderTask;
    private SurveyScheduler.Cancellable finalWarningTask;
    private SurveyScheduler.Cancellable timeoutTask;
    private SurveyScheduler.Cancellable distributionWatchdog;
    private int secondsRemaining;
    private long generationCounter;

    public SurveyManager(CommunityManager communityManager) {
        this(communityManager, new DefaultSurveyScheduler());
    }

    /** R5-M12: הבנאי הזה מאפשר להזריק TestScheduler ולבדוק את כל הלוגיקה בלי להמתין. */
    public SurveyManager(CommunityManager communityManager, SurveyScheduler scheduler) {
        this.communityManager = communityManager;
        this.scheduler = scheduler;
    }

    /* ===================== יצירה ===================== */

    public void createSurvey(List<Question> questions, int delayMinutes) {
        boolean startImmediately;
        synchronized (this) {
            if (inProgress()) {
                throw new IllegalStateException("סקר פעיל כבר קיים. סיים אותו קודם.");
            }
            if (communityManager.getCommunitySize() < AppConfig.MIN_COMMUNITY_SIZE) {
                throw new IllegalStateException(
                        "צריכים לפחות " + AppConfig.MIN_COMMUNITY_SIZE + " חברים בקהילה כדי להתחיל סקר.");
            }
            Survey survey = new Survey(questions, delayMinutes);
            state = new SurveyState(survey, ++generationCounter);
            startImmediately = delayMinutes <= 0;
            if (!startImmediately) {
                startCountdown(delayMinutes);
            }
        }
        if (startImmediately) {
            startSurvey();
        }
    }

    /** נקרא תחת המנעול — רק מתזמן, לא מודיע. */
    private void startCountdown(int delayMinutes) {
        state.status(SurveyStatus.PENDING);
        secondsRemaining = delayMinutes * 60;
        long generation = state.generation();
        countdownTask = scheduler.scheduleTicks(() -> onPendingTick(generation), Duration.ofSeconds(1));
    }

    private void onPendingTick(long generation) {
        int left;
        String surveyId;
        boolean reachedZero = false;
        synchronized (this) {
            if (state == null || state.generation() != generation || state.status() != SurveyStatus.PENDING) {
                return;
            }
            secondsRemaining = Math.max(0, secondsRemaining - 1);
            left = secondsRemaining;
            surveyId = state.survey().getId();
            if (left <= 0) {
                cancel(countdownTask);
                countdownTask = null;
                reachedZero = true;
            }
        }
        listeners.fire(l -> l.onCountdownTick(surveyId, left, true));
        if (reachedZero) {
            startSurvey();
        }
    }

    private void startSurvey() {
        Survey survey;
        List<SurveyParticipant> snapshot;
        synchronized (this) {
            if (state == null || state.status() != SurveyStatus.PENDING) {
                return;
            }
            // דרישה 5: המשתתפים הם חברי הקהילה ברגע שהסקר יוצא בפועל
            state.seed(communityManager.getAllMembers());
            state.status(SurveyStatus.ACTIVE);
            state.survey().setStartTime(LocalDateTime.now());
            secondsRemaining = AppConfig.SURVEY_DURATION_SECONDS;

            // R5-M15: השעון מתחיל רק כשההפצה הסתיימה; הוואצ'דוג הוא רשת הביטחון
            long generation = state.generation();
            distributionWatchdog = scheduler.scheduleOnce(
                    () -> startTimers(generation),
                    Duration.ofSeconds(AppConfig.DISTRIBUTION_WATCHDOG_SECONDS));

            survey = state.survey();
            snapshot = state.snapshot();
        }
        listeners.fire(l -> l.onSurveyStarted(survey, snapshot));
    }

    /**
     * R5-M15: הבוט מדווח שכל השאלות נשלחו — רק עכשיו מתחילות 5 הדקות.
     * קריאה חוזרת אינה עושה דבר.
     */
    public void markDistributionComplete() {
        long generation;
        synchronized (this) {
            if (state == null) {
                return;
            }
            generation = state.generation();
        }
        startTimers(generation);
    }

    private synchronized void startTimers(long generation) {
        if (state == null || state.generation() != generation || state.status() != SurveyStatus.ACTIVE) {
            return;
        }
        if (!state.markTimersStarted()) {
            return;
        }
        cancel(distributionWatchdog);
        distributionWatchdog = null;

        secondsRemaining = AppConfig.SURVEY_DURATION_SECONDS;
        countdownTask = scheduler.scheduleTicks(() -> onActiveTick(generation), Duration.ofSeconds(1));
        reminderTask = scheduler.scheduleOnce(
                () -> sendRemindersIfNeeded(generation, false),
                Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS));
        finalWarningTask = scheduler.scheduleOnce(
                () -> sendRemindersIfNeeded(generation, true),
                Duration.ofSeconds(AppConfig.SURVEY_DURATION_SECONDS
                        - AppConfig.FINAL_WARNING_SECONDS_BEFORE_END));
        // רשת הביטחון הקשיחה: אינה תלויה בטיקים ואינה תלויה במאזינים
        timeoutTask = scheduler.scheduleOnce(
                this::closeSurvey, Duration.ofSeconds(AppConfig.SURVEY_DURATION_SECONDS));
    }

    private void onActiveTick(long generation) {
        int left;
        String surveyId;
        synchronized (this) {
            if (state == null || state.generation() != generation || state.status() != SurveyStatus.ACTIVE) {
                return;   // R5-C01, שכבה 1: טיק של סקר שכבר נסגר — נזרק כאן
            }
            secondsRemaining = Math.max(0, secondsRemaining - 1);
            left = secondsRemaining;
            surveyId = state.survey().getId();
        }
        listeners.fire(l -> l.onCountdownTick(surveyId, left, false));
    }

    /* ===================== תשובות ===================== */

    public AnswerResult recordAnswer(long telegramId, String questionId, String answer) {
        SurveyParticipant participant;
        boolean everyoneFinished;
        synchronized (this) {
            if (state == null || state.status() != SurveyStatus.ACTIVE) {
                return AnswerResult.SURVEY_NOT_ACTIVE;
            }
            participant = state.find(telegramId);
            if (participant == null) {
                return AnswerResult.UNKNOWN_PARTICIPANT;
            }
            if (participant.hasAnswered(questionId)) {
                return AnswerResult.ALREADY_ANSWERED;
            }
            participant.recordAnswer(questionId, answer, state.survey().getQuestions().size());
            everyoneFinished = state.allCompleted();
        }
        SurveyParticipant recorded = participant;
        listeners.fire(l -> l.onAnswerRecorded(recorded));
        if (everyoneFinished) {
            closeSurvey();
        }
        return AnswerResult.RECORDED;
    }

    /* ===================== סגירה וביטול ===================== */

    public void closeSurvey() {
        Survey closed;
        List<SurveyParticipant> snapshot;
        synchronized (this) {
            if (state == null || state.status() != SurveyStatus.ACTIVE) {
                return;
            }
            cancelAllTasks();
            state.status(SurveyStatus.COMPLETED);
            closed = state.survey();
            snapshot = state.snapshot();
            state = null;
        }
        listeners.fire(l -> l.onSurveyClosed(closed, snapshot));
    }

    /** R5-M13: ביטול לפני השליחה — אין משתתפים, אין תוצאות ואין הודעת סיום. */
    public void cancelPendingSurvey() {
        Survey cancelled;
        synchronized (this) {
            if (state == null || state.status() != SurveyStatus.PENDING) {
                return;
            }
            cancelAllTasks();
            state.status(SurveyStatus.CANCELLED);
            cancelled = state.survey();
            state = null;
        }
        listeners.fire(l -> l.onSurveyCancelled(cancelled));
    }

    private void sendRemindersIfNeeded(long generation, boolean isFinalWarning) {
        Survey survey;
        List<SurveyParticipant> pending;
        synchronized (this) {
            if (state == null || state.generation() != generation || state.status() != SurveyStatus.ACTIVE) {
                return;
            }
            if (!state.markRemindersSent(isFinalWarning)) {
                return;
            }
            pending = state.notCompleted();
            if (pending.isEmpty()) {
                return;
            }
            survey = state.survey();
        }
        listeners.fire(l -> l.onReminderSent(survey, pending, isFinalWarning));
    }

    private void cancelAllTasks() {
        cancel(countdownTask);
        cancel(reminderTask);
        cancel(finalWarningTask);
        cancel(timeoutTask);
        cancel(distributionWatchdog);
        countdownTask = null;
        reminderTask = null;
        finalWarningTask = null;
        timeoutTask = null;
        distributionWatchdog = null;
    }

    private void cancel(SurveyScheduler.Cancellable task) {
        if (task != null) {
            task.cancel();
        }
    }

    /* ===================== שאילתות ===================== */

    public synchronized boolean isSurveyInProgress() {
        return inProgress();
    }

    private boolean inProgress() {
        return state != null
                && (state.status() == SurveyStatus.PENDING || state.status() == SurveyStatus.ACTIVE);
    }

    public synchronized Survey getCurrentSurvey() {
        return state == null ? null : state.survey();
    }

    public synchronized List<SurveyParticipant> getCurrentParticipants() {
        return state == null ? new ArrayList<>() : state.snapshot();
    }


    public void addSurveyListener(SurveyListener listener) {
        listeners.add(listener);
    }

    public void removeSurveyListener(SurveyListener listener) {
        listeners.remove(listener);
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}