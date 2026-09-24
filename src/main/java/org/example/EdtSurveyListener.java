package org.example;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * מעביר כל אירוע סקר ל-EDT לפני שהוא מגיע ל-{@code delegate}.
 * ההעברה נעשית במקום אחד, כך שמסכי הממשק כותבים רק את לוגיקת התצוגה ואינם חוזרים על {@code invokeLater}.
 */
final class EdtSurveyListener implements SurveyListener {
    private final SurveyListener delegate;

    private EdtSurveyListener(SurveyListener delegate) {
        this.delegate = delegate;
    }

    static SurveyListener wrap(SurveyListener delegate) {
        return new EdtSurveyListener(delegate);
    }

    @Override
    public void onCountdownTick(String surveyId, int secondsRemaining, boolean isPendingPhase) {
        SwingUtilities.invokeLater(() -> delegate.onCountdownTick(surveyId, secondsRemaining, isPendingPhase));
    }

    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> delegate.onSurveyStarted(survey, participants));
    }

    @Override
    public void onAnswerRecorded(SurveyParticipant participant) {
        SwingUtilities.invokeLater(() -> delegate.onAnswerRecorded(participant));
    }

    @Override
    public void onParticipantUnreachable(SurveyParticipant participant) {
        SwingUtilities.invokeLater(() -> delegate.onParticipantUnreachable(participant));
    }

    @Override
    public void onParticipantDeliveryChanged(SurveyParticipant participant) {
        SwingUtilities.invokeLater(() -> delegate.onParticipantDeliveryChanged(participant));
    }

    @Override
    public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> delegate.onSurveyClosed(survey, participants));
    }

    @Override
    public void onReminderSent(Survey survey, List<SurveyParticipant> notCompleted) {
        SwingUtilities.invokeLater(() -> delegate.onReminderSent(survey, notCompleted));
    }

    @Override
    public void onSurveyCancelled(Survey survey) {
        SwingUtilities.invokeLater(() -> delegate.onSurveyCancelled(survey));
    }
}
