package org.example;

import java.util.List;

public interface SurveyListener {
    /**
     * מזהה הסקר נשלח יחד עם הטיק, כדי שהתצוגה תוכל לזרוק טיק
     * שהיה באוויר ברגע הסגירה במקום לדרוס איתו את מסך הסיום.
     */
    default void onCountdownTick(String surveyId, int secondsRemaining, boolean isPendingPhase) {
    }

    default void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
    }

    default void onAnswerRecorded(SurveyParticipant participant) {
    }

    /** ההודעות לא הגיעו למשתתף (חסם את הבוט) — הוא אינו חוסם סגירה ואינו מקבל תזכורת. */
    default void onParticipantUnreachable(SurveyParticipant participant) {
    }

    default void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
    }

    /** תזכורת יחידה בלבד לכל סקר. */
    default void onReminderSent(Survey survey, List<SurveyParticipant> notCompleted) {
    }

    /** ביטול בשלב ההמתנה — אין תוצאות ואין הודעת סיום למשתתפים. */
    default void onSurveyCancelled(Survey survey) {
    }
}
