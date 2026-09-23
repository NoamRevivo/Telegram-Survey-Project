package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** לוגיקת הסקר מקצה לקצה על שעון וירטואלי: תזכורת, סגירה, הפצה ומשתתפים שלא ניתן להשיג. */
class SurveyManagerTest {
    private CommunityManager communityManager;
    private TestScheduler scheduler;
    private SurveyManager manager;
    private RecordingSurveyListener listener;

    @BeforeEach
    void setUp() {
        communityManager = new CommunityManager();
        communityManager.addMember(1L, "אורי", "uri");
        communityManager.addMember(2L, "דנה", "dana");
        communityManager.addMember(3L, "יוסי", "yossi");

        scheduler = new TestScheduler();
        manager = new SurveyManager(communityManager, scheduler);
        listener = new RecordingSurveyListener();
        manager.addSurveyListener(listener);
    }

    private List<Question> oneQuestion() {
        return List.of(new Question("מה השעה?", List.of("בוקר", "ערב")));
    }

    /** מדמה את מה שהבוט עושה: מפיץ את השאלות ואז מדווח שסיים. */
    private void startImmediateSurvey() {
        manager.createSurvey(oneQuestion(), 0);
        manager.markDistributionComplete(manager.getCurrentSurvey().getId());
    }

    private String firstQuestionId() {
        Survey survey = manager.getCurrentSurvey();
        assertNotNull(survey, "הסקר אמור להיות פעיל");
        return survey.getQuestions().get(0).getId();
    }

    @Test
    void participantJoiningDuringActiveSurveyCannotAnswer() {
        startImmediateSurvey();
        String questionId = firstQuestionId();

        communityManager.addMember(4L, "מצטרף מאוחר", "late");

        assertEquals(SurveyManager.AnswerResult.UNKNOWN_PARTICIPANT,
                manager.recordAnswer(4L, questionId, "בוקר"));
        assertEquals(SurveyManager.AnswerResult.RECORDED,
                manager.recordAnswer(1L, questionId, "בוקר"));
    }

    @Test
    void surveyClosesEarlyWhenEveryoneFinished() {
        startImmediateSurvey();
        String questionId = firstQuestionId();

        manager.recordAnswer(1L, questionId, "בוקר");
        manager.recordAnswer(2L, questionId, "ערב");
        manager.recordAnswer(3L, questionId, "בוקר");

        assertFalse(manager.isSurveyInProgress());
        assertEquals(1, listener.closedCount, "הסקר אמור להיסגר בדיוק פעם אחת");
    }

    /** טיק שהיה באוויר בזמן הסגירה לא נמסר אחריה. */
    @Test
    void noTickIsDeliveredAfterSurveyClosed() {
        startImmediateSurvey();
        scheduler.advance(Duration.ofSeconds(3));
        int ticksBeforeClose = listener.totalTicks;
        assertTrue(ticksBeforeClose > 0, "בזמן הסקר אמורים להתקבל טיקים");

        manager.closeSurvey();
        scheduler.advance(Duration.ofSeconds(5));

        assertEquals(0, listener.ticksAfterClose);
        assertEquals(ticksBeforeClose, listener.totalTicks);
    }

    @Test
    void reminderGoesOnlyToUnfinishedParticipants() {
        startImmediateSurvey();
        String questionId = firstQuestionId();
        manager.recordAnswer(1L, questionId, "בוקר");

        scheduler.advance(Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS));

        assertEquals(1, listener.reminderRoundsCount);
        assertEquals(Set.of(2L, 3L), Set.copyOf(listener.remindedIds));
    }

    @Test
    void reminderIsSentAtMostOnce() {
        startImmediateSurvey();
        scheduler.advance(Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS + 30));

        assertEquals(1, listener.reminderRoundsCount);
    }

    /** השעון מתחיל רק אחרי שההפצה דיווחה שהסתיימה. */
    @Test
    void clockStartsOnlyAfterDistributionCompletes() {
        manager.createSurvey(oneQuestion(), 0);
        scheduler.advance(Duration.ofSeconds(5));
        assertEquals(0, listener.totalTicks, "לפני סיום ההפצה אין ספירה לאחור");

        manager.markDistributionComplete(manager.getCurrentSurvey().getId());
        scheduler.advance(Duration.ofSeconds(5));
        assertTrue(listener.totalTicks > 0);
    }

    /** גם אם ההפצה לא דיווחה, רשת הביטחון מפעילה את השעון. */
    @Test
    void watchdogStartsClockWhenDistributionNeverReports() {
        manager.createSurvey(oneQuestion(), 0);
        scheduler.advance(Duration.ofSeconds(AppConfig.DISTRIBUTION_WATCHDOG_SECONDS + 2));
        assertTrue(listener.totalTicks > 0);
    }

    /** הסגירה הקשיחה אחרי 5 דקות אינה תלויה בטיקים ואינה תלויה במאזינים. */
    @Test
    void surveyClosesAfterFullDuration() {
        startImmediateSurvey();
        scheduler.advance(Duration.ofSeconds(AppConfig.SURVEY_DURATION_SECONDS + 1));

        assertFalse(manager.isSurveyInProgress());
        assertEquals(1, listener.closedCount);
    }

    /** ביטול בשלב ההמתנה אינו "סקר שהסתיים". */
    @Test
    void cancellingPendingSurveyIsNotAClose() {
        manager.createSurvey(oneQuestion(), 5);
        manager.cancelPendingSurvey();

        assertEquals(1, listener.cancelledCount);
        assertEquals(0, listener.closedCount);
        assertEquals(0, listener.startedCount);
        assertFalse(manager.isSurveyInProgress());
    }

    @Test
    void delayedSurveyStartsWhenCountdownReachesZero() {
        manager.createSurvey(oneQuestion(), 1);
        assertEquals(0, listener.startedCount);

        scheduler.advance(Duration.ofSeconds(61));

        assertEquals(1, listener.startedCount);
        assertEquals(3, manager.getCurrentParticipants().size());
    }

    @Test
    void secondSurveyCannotStartWhileOneIsRunning() {
        startImmediateSurvey();
        assertThrows(IllegalStateException.class, () -> manager.createSurvey(oneQuestion(), 0));
    }

    @Test
    void surveyRequiresMinimumCommunitySize() {
        CommunityManager small = new CommunityManager();
        small.addMember(1L, "לבד", null);
        SurveyManager smallManager = new SurveyManager(small, new TestScheduler());

        assertThrows(IllegalStateException.class, () -> smallManager.createSurvey(oneQuestion(), 0));
    }

    @Test
    void noReminderBeforeThreeMinutes() {
        startImmediateSurvey();
        scheduler.advance(Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS - 1));
        assertEquals(0, listener.reminderRoundsCount);
    }

    @Test
    void reminderIsNeverSentTwiceToTheSameParticipant() {
        startImmediateSurvey();
        scheduler.advance(Duration.ofSeconds(AppConfig.SURVEY_DURATION_SECONDS - 1));
        assertEquals(1, listener.reminderRoundsCount);
        assertEquals(List.of(1L, 2L, 3L), listener.remindedIds);
    }

    @Test
    void staleDistributionReportDoesNotStartTheNextSurveyClock() {
        manager.createSurvey(oneQuestion(), 0);
        String firstId = manager.getCurrentSurvey().getId();
        manager.closeSurvey();

        manager.createSurvey(oneQuestion(), 0);
        manager.markDistributionComplete(firstId);
        scheduler.advance(Duration.ofSeconds(5));
        assertEquals(0, listener.totalTicks, "דיווח של הסקר הקודם אינו מפעיל את השעון של הנוכחי");

        manager.markDistributionComplete(manager.getCurrentSurvey().getId());
        scheduler.advance(Duration.ofSeconds(5));
        assertTrue(listener.totalTicks > 0);
    }

    @Test
    void distributionStopsWhenSurveyIsClosed() {
        startImmediateSurvey();
        String id = manager.getCurrentSurvey().getId();
        assertTrue(manager.isActive(id));

        manager.closeSurvey();
        assertFalse(manager.isActive(id));
    }

    @Test
    void unreachableParticipantDoesNotBlockEarlyClose() {
        startImmediateSurvey();
        String questionId = firstQuestionId();
        String id = manager.getCurrentSurvey().getId();

        manager.recordAnswer(1L, questionId, "בוקר");
        manager.recordAnswer(2L, questionId, "ערב");
        assertTrue(manager.isSurveyInProgress());

        manager.markUnreachable(id, 3L);

        assertFalse(manager.isSurveyInProgress());
        assertEquals(1, listener.closedCount);
    }

    @Test
    void unreachableParticipantGetsNoReminder() {
        startImmediateSurvey();
        manager.markUnreachable(manager.getCurrentSurvey().getId(), 3L);

        scheduler.advance(Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS));

        assertEquals(Set.of(1L, 2L), Set.copyOf(listener.remindedIds));
    }

    @Test
    void countdownIsDerivedFromTheDeadline() {
        startImmediateSurvey();
        scheduler.advance(Duration.ofSeconds(60));
        assertEquals(AppConfig.SURVEY_DURATION_SECONDS - 60, listener.lastSecondsRemaining);
    }
}