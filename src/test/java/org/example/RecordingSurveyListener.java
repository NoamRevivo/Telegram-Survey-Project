package org.example;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** R5-M12: מאזין שמתעד מה קרה, כדי שהבדיקות יוכלו לקבוע עובדות. */
public class RecordingSurveyListener implements SurveyListener {

    public int startedCount;
    public int closedCount;
    public int cancelledCount;
    public int ticksAfterClose;
    public int totalTicks;
    public final List<String> reminderRounds = new ArrayList<>();
    public final Set<Long> remindedIds = new LinkedHashSet<>();

    private boolean closed;

    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        startedCount++;
        closed = false;
    }

    @Override
    public void onCountdownTick(String surveyId, int secondsRemaining, boolean isPendingPhase) {
        totalTicks++;
        if (closed) {
            ticksAfterClose++;
        }
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
    public void onReminderSent(Survey survey, List<SurveyParticipant> notCompleted, boolean isFinalWarning) {
        reminderRounds.add(isFinalWarning ? "final" : "mid");
        for (SurveyParticipant participant : notCompleted) {
            remindedIds.add(participant.getUser().getTelegramId());
        }
    }
}