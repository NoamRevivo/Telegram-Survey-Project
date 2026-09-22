package org.example;

import java.util.List;

public interface SurveyListener
{
    default void onCountdownTick(int secondsRemaining, boolean isPendingPhase)
    {
    }
    default void onSurveyStarted(Survey survey, List<SurveyParticipant> participants)
    {
    }
    default void onAnswerRecorded(SurveyParticipant participant)
    {
    }
    default void onSurveyClosed(Survey survey, List<SurveyParticipant> participants)
    {
    }

    default void onReminderSent(Survey survey, List<SurveyParticipant> notCompleted, boolean isFinalWarning) {
    }
}