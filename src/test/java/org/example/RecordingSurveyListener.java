package org.example;

import java.util.ArrayList;
import java.util.List;

/** מאזין שמתעד מה קרה, כדי שהבדיקות יוכלו לקבוע עובדות. */
public class RecordingSurveyListener implements SurveyListener {
    public int startedCount;
    public int closedCount;
    public int cancelledCount;
    public int ticksAfterClose;
    public int totalTicks;
    public int reminderRoundsCount;
    public int lastSecondsRemaining = -1;
    public final List<Long> remindedIds = new ArrayList<>();
    public final List<Long> unreachableIds = new ArrayList<>();

    private boolean closed;

    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        startedCount++;
        closed = false;
    }

    @Override
    public void onCountdownTick(String surveyId, int secondsRemaining, boolean isPendingPhase) {
        totalTicks++;
        lastSecondsRemaining = secondsRemaining;
        if (closed) {
            ticksAfterClose++;
        }
    }

    @Override
    public void onParticipantUnreachable(SurveyParticipant participant) {
        unreachableIds.add(participant.getUser().getTelegramId());
    }

    @Override
    public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
        closedCount++;
        closed = true;
    }

    @Override
    public void onSurveyCancelled(Survey survey) {
        cancelledCount++;
        closed = true;
    }

    @Override
    public void onReminderSent(Survey survey, List<SurveyParticipant> notCompleted) {
        reminderRoundsCount++;
        for (SurveyParticipant participant : notCompleted) {
            remindedIds.add(participant.getUser().getTelegramId());
        }
    }
}