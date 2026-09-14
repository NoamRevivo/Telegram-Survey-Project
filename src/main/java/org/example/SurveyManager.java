package org.example;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class SurveyManager {

    private static final int MINIMUM_MEMBERS_REQUIRED = 3;
    private static final int SURVEY_DURATION_SECONDS = 5 * 60;
    private static final int REMINDER_DELAY_SECONDS = 3 * 60;

    private final CommunityManager communityManager;
    private final List<SurveyListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<Long, SurveyParticipant> participants = new ConcurrentHashMap<>();

    private Survey currentSurvey;
    private ScheduledFuture<?> countdownTask;
    private ScheduledFuture<?> reminderTask;
    private ScheduledFuture<?> timeoutTask;
    private volatile int secondsRemaining;

    public SurveyManager(CommunityManager communityManager) {
        this.communityManager = communityManager;
    }

    public void addListener(SurveyListener listener) {
        listeners.add(listener);
    }

    public boolean isSurveyInProgress()
    {
        return currentSurvey != null
                && (currentSurvey.getStatus() == SurveyStatus.PENDING
                || currentSurvey.getStatus() == SurveyStatus.ACTIVE);
    }

    public synchronized Survey createSurvey(List<Question> questions, int delayMinutes)
    {
        if (isSurveyInProgress()) {
            throw new IllegalStateException("קיים כבר סקר פעיל/ממתין - לא ניתן ליצור סקר נוסף במקביל.");
        }
        if (communityManager.getCommunitySize() < MINIMUM_MEMBERS_REQUIRED)
        {
            throw new IllegalStateException(
                    "נדרשים לפחות " + MINIMUM_MEMBERS_REQUIRED + " חברי קהילה כדי להתחיל סקר.");
        }

        this.currentSurvey = new Survey(questions, delayMinutes);
        this.participants.clear();
        for (CommunityUser user : communityManager.getAllMembers())
        {
            participants.put(user.getTelegramId(), new SurveyParticipant(user));
        }
        if (delayMinutes > 0)
        {
            startCountdown(delayMinutes * 60, true, this::startSurvey);
        } else
        {
            startSurvey();
        }
        return currentSurvey;
    }
    private void startCountdown(int totalSeconds, boolean isPendingPhase, Runnable onFinish)
    {
        secondsRemaining = totalSeconds;
        countdownTask = scheduler.scheduleAtFixedRate(() ->
        {
            secondsRemaining--;
            notifyCountdownTick(secondsRemaining, isPendingPhase);
            if (secondsRemaining <= 0)
            {
                countdownTask.cancel(false);
                onFinish.run();
            }
        }
        , 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void startSurvey()
    {
        currentSurvey.setStatus(SurveyStatus.ACTIVE);
        currentSurvey.setStartTime(LocalDateTime.now());
        List<SurveyParticipant> snapshot = new ArrayList<>(participants.values());
        for (SurveyListener listener : listeners) {
            listener.onSurveyStarted(currentSurvey, snapshot);
        }

        reminderTask = scheduler.schedule(this::sendReminders, REMINDER_DELAY_SECONDS, TimeUnit.SECONDS);
        timeoutTask = scheduler.schedule(this::closeSurvey, SURVEY_DURATION_SECONDS, TimeUnit.SECONDS);
        startCountdown(SURVEY_DURATION_SECONDS, false, this::closeSurvey);
    }
    private void sendReminders()
    {
        List<SurveyParticipant> unfinished = new ArrayList<>();
        for (SurveyParticipant p : participants.values())
        {
            if (!p.isCompleted())
            {
                unfinished.add(p);
            }
        }
        for (SurveyListener listener : listeners)
        {
            listener.onReminderDue(unfinished);
        }
    }
    public synchronized void recordAnswer(long userId, String questionId, String answer)
    {
        if (currentSurvey == null || currentSurvey.getStatus() != SurveyStatus.ACTIVE)
        {
            return;
        }
        SurveyParticipant participant = participants.get(userId);
        if (participant == null || participant.hasAnswered(questionId))
        {
            return;
        }
        participant.recordAnswer(questionId, answer, currentSurvey.getQuestions().size());
        for (SurveyListener listener : listeners)
        {
            listener.onAnswerRecorded(participant);
        }
        if (allParticipantsCompleted())
        {
            closeSurvey();
        }
    }
    private boolean allParticipantsCompleted()
    {
        return participants.values().stream().allMatch(SurveyParticipant::isCompleted);
    }
    public synchronized void closeSurvey()
    {
        if (currentSurvey == null || currentSurvey.getStatus() == SurveyStatus.COMPLETED)
        {
            return;
        }
        cancelIfRunning(reminderTask);
        cancelIfRunning(countdownTask);
        cancelIfRunning(timeoutTask);
        currentSurvey.setStatus(SurveyStatus.COMPLETED);
        List<SurveyParticipant> finalParticipants = new ArrayList<>(participants.values());
        for (SurveyListener listener : listeners)
        {
            listener.onSurveyClosed(currentSurvey, finalParticipants);
        }
    }
    private void cancelIfRunning(ScheduledFuture<?> task)
    {
        if (task != null && !task.isDone())
        {
            task.cancel(false);
        }
    }
    private void notifyCountdownTick(int seconds, boolean isPendingPhase)
    {
        for (SurveyListener listener : listeners)
        {
            listener.onCountdownTick(seconds, isPendingPhase);
        }
    }
    public Survey getCurrentSurvey() {
        return currentSurvey;
    }
    public Collection<SurveyParticipant> getParticipants() {
        return participants.values();
    }
    public void shutdown() {
        scheduler.shutdownNow();
    }
}