package org.example;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


public class SurveyManager {

    public enum AnswerResult { RECORDED, SURVEY_NOT_ACTIVE, ALREADY_ANSWERED, UNKNOWN_PARTICIPANT }

    private final CommunityManager communityManager;
    private final SurveyScheduler scheduler;
    private final Listeners<SurveyListener> listeners = new Listeners<>();

    private SurveyState state;
    private SurveyScheduler.Cancellable countdownTask;
    private SurveyScheduler.Cancellable reminderTask;
    private SurveyScheduler.Cancellable timeoutTask;
    private SurveyScheduler.Cancellable distributionWatchdog;
    private int secondsRemaining;
    private long generationCounter;

    public SurveyManager(CommunityManager communityManager) {
        this(communityManager, new DefaultSurveyScheduler());
    }

    public SurveyManager(CommunityManager communityManager, SurveyScheduler scheduler) {
        this.communityManager = communityManager;
        this.scheduler = scheduler;
    }


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
            state.seed(communityManager.getAllMembers());
            state.status(SurveyStatus.ACTIVE);
            state.survey().setStartTime(LocalDateTime.now());
            secondsRemaining = AppConfig.SURVEY_DURATION_SECONDS;

            long generation = state.generation();
            distributionWatchdog = scheduler.scheduleOnce(
                    () -> startTimers(generation),
                    Duration.ofSeconds(AppConfig.DISTRIBUTION_WATCHDOG_SECONDS));

            survey = state.survey();
            snapshot = state.snapshot();
        }
        listeners.fire(l -> l.onSurveyStarted(survey, snapshot));
    }


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
        // R6-C01: תזכורת יחידה בלבד — נשלחת AppConfig.REMINDER_DELAY_SECONDS (3 דקות) מתחילת הסקר
        reminderTask = scheduler.scheduleOnce(
                () -> sendRemindersIfNeeded(generation),
                Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS));
        timeoutTask = scheduler.scheduleOnce(
                this::closeSurvey, Duration.ofSeconds(AppConfig.SURVEY_DURATION_SECONDS));
    }

    private void onActiveTick(long generation) {
        int left;
        String surveyId;
        synchronized (this) {
            if (state == null || state.generation() != generation || state.status() != SurveyStatus.ACTIVE) {
                return;
            }
            secondsRemaining = Math.max(0, secondsRemaining - 1);
            left = secondsRemaining;
            surveyId = state.survey().getId();
        }
        listeners.fire(l -> l.onCountdownTick(surveyId, left, false));
    }

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

    private void sendRemindersIfNeeded(long generation) {
        Survey survey;
        List<SurveyParticipant> pending;
        synchronized (this) {
            if (state == null || state.generation() != generation || state.status() != SurveyStatus.ACTIVE) {
                return;
            }
            if (!state.markRemindersSent()) {
                return;
            }
            pending = state.notCompleted();
            if (pending.isEmpty()) {
                return;
            }
            survey = state.survey();
        }
        listeners.fire(l -> l.onReminderSent(survey, pending));
    }

    private void cancelAllTasks() {
        cancel(countdownTask);
        cancel(reminderTask);
        cancel(timeoutTask);
        cancel(distributionWatchdog);
        countdownTask = null;
        reminderTask = null;
        timeoutTask = null;
        distributionWatchdog = null;
    }

    private void cancel(SurveyScheduler.Cancellable task) {
        if (task != null) {
            task.cancel();
        }
    }


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