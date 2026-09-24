package org.example;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


public class SurveyManager {
    public enum AnswerResult { RECORDED, SURVEY_NOT_ACTIVE, ALREADY_ANSWERED, UNKNOWN_PARTICIPANT, INVALID_ANSWER }

    private static final long ANY_GENERATION = -1L;

    private final CommunityManager communityManager;
    private final SurveyScheduler scheduler;
    private final Listeners<SurveyListener> listeners = new Listeners<>();

    private SurveyState state;
    private SurveyScheduler.Cancellable countdownTask;
    private SurveyScheduler.Cancellable reminderTask;
    private SurveyScheduler.Cancellable timeoutTask;
    private SurveyScheduler.Cancellable distributionWatchdog;
    private long deadlineMillis;
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
        long generation;
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
            generation = state.generation();
            startImmediately = survey.getDelayMinutes() <= 0;
            if (!startImmediately) {
                startCountdown(survey.getDelayMinutes());
            }
        }
        if (startImmediately) {
            startSurvey(generation);
        }
    }

    private void startCountdown(int delayMinutes) {
        state.status(SurveyStatus.PENDING);
        deadlineMillis = scheduler.nowMillis() + delayMinutes * AppConfig.SECONDS_PER_MINUTE * 1000L;
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
            left = secondsUntilDeadline();
            surveyId = state.survey().getId();
            if (left <= 0) {
                cancel(countdownTask);
                countdownTask = null;
                reachedZero = true;
            }
        }
        listeners.fire(l -> l.onCountdownTick(surveyId, left, true));
        if (reachedZero) {
            startSurvey(generation);
        }
    }


    private void startSurvey(long generation) {
        Survey survey;
        List<SurveyParticipant> snapshot;
        synchronized (this) {
            if (state == null || state.generation() != generation || state.status() != SurveyStatus.PENDING) {
                return;
            }
            state.seed(communityManager.getAllMembers());
            state.status(SurveyStatus.ACTIVE);
            state.survey().setStartTime(LocalDateTime.now());

            distributionWatchdog = scheduler.scheduleOnce(
                    () -> startTimers(generation),
                    Duration.ofSeconds(AppConfig.DISTRIBUTION_WATCHDOG_SECONDS));

            survey = state.survey();
            snapshot = state.snapshot();
        }
        listeners.fire(l -> l.onSurveyStarted(survey, snapshot));
    }

    public void markDistributionComplete(String surveyId) {
        long generation;
        synchronized (this) {
            if (state == null || !state.survey().getId().equals(surveyId)) {
                return;
            }
            generation = state.generation();
        }
        startTimers(generation);
    }

    public synchronized boolean isSurveyRunning() {
        return state != null && state.status() == SurveyStatus.ACTIVE;
    }


    public void markDeliveryFailed(String surveyId, long telegramId, boolean failed) {
        SurveyParticipant changed = null;
        synchronized (this) {
            if (!isActive(surveyId)) {
                return;
            }
            SurveyParticipant participant = state.find(telegramId);
            if (participant != null && !participant.isUnreachable()
                    && participant.isDeliveryFailed() != failed) {
                participant.setDeliveryFailed(failed);
                changed = participant;
            }
        }
        if (changed != null) {
            SurveyParticipant updated = changed;
            listeners.fire(l -> l.onParticipantDeliveryChanged(updated));
        }
    }

    public synchronized boolean isActive(String surveyId) {
        return state != null
                && state.status() == SurveyStatus.ACTIVE
                && state.survey().getId().equals(surveyId);
    }


    public void markUnreachable(String surveyId, long telegramId) {
        SurveyParticipant newlyUnreachable = null;
        boolean closeNow;
        long generation;
        synchronized (this) {
            if (!isActive(surveyId)) {
                return;
            }
            SurveyParticipant participant = state.find(telegramId);
            if (participant == null) {
                return;
            }
            if (!participant.isUnreachable()) {
                participant.markUnreachable();
                newlyUnreachable = participant;
            }
            closeNow = state.anyCompleted() && state.allCompleted();
            generation = state.generation();
        }
        if (newlyUnreachable != null) {
            SurveyParticipant unreachable = newlyUnreachable;
            listeners.fire(l -> l.onParticipantUnreachable(unreachable));
        }
        if (closeNow) {
            closeSurvey(generation);
        }
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

        deadlineMillis = scheduler.nowMillis() + AppConfig.SURVEY_DURATION_SECONDS * 1000L;
        countdownTask = scheduler.scheduleTicks(() -> onActiveTick(generation), Duration.ofSeconds(1));
        reminderTask = scheduler.scheduleOnce(
                () -> sendRemindersIfNeeded(generation),
                Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS));
        timeoutTask = scheduler.scheduleOnce(
                () -> closeSurvey(generation), Duration.ofSeconds(AppConfig.SURVEY_DURATION_SECONDS));
    }

    private int secondsUntilDeadline() {
        long millisLeft = deadlineMillis - scheduler.nowMillis();
        return (int) Math.max(0, (millisLeft + 999) / 1000);
    }

    private void onActiveTick(long generation) {
        int left;
        String surveyId;
        synchronized (this) {
            if (state == null || state.generation() != generation || state.status() != SurveyStatus.ACTIVE) {
                return;
            }
            left = secondsUntilDeadline();
            surveyId = state.survey().getId();
        }
        listeners.fire(l -> l.onCountdownTick(surveyId, left, false));
    }


    public AnswerResult recordAnswer(String surveyId, long telegramId, int questionIndex, int optionIndex) {
        SurveyParticipant participant;
        boolean everyoneFinished;
        long generation;
        synchronized (this) {
            if (!isActive(surveyId)) {
                return AnswerResult.SURVEY_NOT_ACTIVE;
            }
            List<Question> questions = state.survey().getQuestions();
            if (questionIndex < 0 || questionIndex >= questions.size()) {
                return AnswerResult.INVALID_ANSWER;
            }
            Question question = questions.get(questionIndex);
            if (optionIndex < 0 || optionIndex >= question.getOptions().size()) {
                return AnswerResult.INVALID_ANSWER;
            }
            participant = state.find(telegramId);
            if (participant == null) {
                return AnswerResult.UNKNOWN_PARTICIPANT;
            }
            if (participant.hasAnswered(question.getId())) {
                return AnswerResult.ALREADY_ANSWERED;
            }
            participant.recordAnswer(question.getId(), question.getOptions().get(optionIndex), questions.size());
            everyoneFinished = state.allCompleted();
            generation = state.generation();
        }
        SurveyParticipant recorded = participant;
        listeners.fire(l -> l.onAnswerRecorded(recorded));
        if (everyoneFinished) {
            closeSurvey(generation);
        }
        return AnswerResult.RECORDED;
    }

    public void closeSurvey() {
        closeSurvey(ANY_GENERATION);
    }


    private void closeSurvey(long expectedGeneration) {
        Survey closed;
        List<SurveyParticipant> snapshot;
        synchronized (this) {
            if (state == null || state.status() != SurveyStatus.ACTIVE) {
                return;
            }
            if (expectedGeneration != ANY_GENERATION && state.generation() != expectedGeneration) {
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


    public boolean cancelPendingSurvey() {
        Survey cancelled;
        synchronized (this) {
            if (state == null || state.status() != SurveyStatus.PENDING) {
                return false;
            }
            cancelAllTasks();
            state.status(SurveyStatus.CANCELLED);
            cancelled = state.survey();
            state = null;
        }
        listeners.fire(l -> l.onSurveyCancelled(cancelled));
        return true;
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