package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

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

    private List<Question> threeQuestions() {
        return List.of(
                new Question("שאלה א", List.of("כן", "לא")),
                new Question("שאלה ב", List.of("כן", "לא", "אולי")),
                new Question("שאלה ג", List.of("א", "ב")));
    }

    private String surveyId() {
        Survey survey = manager.getCurrentSurvey();
        assertNotNull(survey, "הסקר אמור להיות פעיל");
        return survey.getId();
    }

    private static CommunityManager communityOf(int size) {
        CommunityManager community = new CommunityManager();
        for (long id = 1; id <= size; id++) {
            community.addMember(id, "חבר " + id, "user" + id);
        }
        return community;
    }

    private static void runConcurrently(int threads, IntConsumer body) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Void> job = () -> {
                    ready.countDown();
                    go.await();
                    body.accept(index);
                    return null;
                };
                futures.add(pool.submit(job));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "כל החוטים אמורים להתחיל");
            go.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void participantJoiningDuringActiveSurveyCannotAnswer() {
        startImmediateSurvey();
        String id = surveyId();

        communityManager.addMember(4L, "מצטרף מאוחר", "late");

        assertEquals(SurveyManager.AnswerResult.UNKNOWN_PARTICIPANT, manager.recordAnswer(id, 4L, 0, 0));
        assertEquals(SurveyManager.AnswerResult.RECORDED, manager.recordAnswer(id, 1L, 0, 0));
    }

    @Test
    void surveyClosesEarlyWhenEveryoneFinished() {
        startImmediateSurvey();
        String id = surveyId();

        manager.recordAnswer(id, 1L, 0, 0);
        manager.recordAnswer(id, 2L, 0, 1);
        manager.recordAnswer(id, 3L, 0, 0);

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
        manager.recordAnswer(surveyId(), 1L, 0, 0);

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
        String id = surveyId();

        manager.recordAnswer(id, 1L, 0, 0);
        manager.recordAnswer(id, 2L, 0, 1);
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

    @Test
    void invalidIndexesAreRejectedAndNothingIsRecorded() {
        startImmediateSurvey();
        String id = surveyId();

        assertEquals(SurveyManager.AnswerResult.INVALID_ANSWER, manager.recordAnswer(id, 1L, -1, 0));
        assertEquals(SurveyManager.AnswerResult.INVALID_ANSWER, manager.recordAnswer(id, 1L, 1, 0));
        assertEquals(SurveyManager.AnswerResult.INVALID_ANSWER, manager.recordAnswer(id, 1L, 0, -1));
        assertEquals(SurveyManager.AnswerResult.INVALID_ANSWER, manager.recordAnswer(id, 1L, 0, 2));

        SurveyParticipant first = manager.getCurrentParticipants().get(0);
        assertEquals(0, first.getAnsweredQuestionsCount());
        assertTrue(manager.isSurveyInProgress());
    }

    @Test
    void recordedAnswerIsTheOptionAtTheChosenIndex() {
        startImmediateSurvey();
        String id = surveyId();

        manager.recordAnswer(id, 1L, 0, 1);

        SurveyParticipant first = manager.getCurrentParticipants().get(0);
        Question question = manager.getCurrentSurvey().getQuestions().get(0);
        assertEquals("ערב", first.getAnswers().get(question.getId()));
    }

    @Test
    void answerAddressedToAnotherSurveyIsNotRecorded() {
        startImmediateSurvey();
        String oldId = surveyId();
        manager.closeSurvey();
        startImmediateSurvey();

        assertEquals(SurveyManager.AnswerResult.SURVEY_NOT_ACTIVE, manager.recordAnswer(oldId, 1L, 0, 0));
        assertEquals(0, manager.getCurrentParticipants().get(0).getAnsweredQuestionsCount());
    }

    @Test
    void unreachableEventIsFiredOncePerParticipant() {
        startImmediateSurvey();
        String id = surveyId();

        manager.markUnreachable(id, 3L);
        manager.markUnreachable(id, 3L);
        manager.markUnreachable(id, 99L);

        assertEquals(List.of(3L), listener.unreachableIds);
    }

    @Test
    void unreachableReportOfAnotherSurveyIsIgnored() {
        startImmediateSurvey();
        String oldId = surveyId();
        manager.closeSurvey();
        startImmediateSurvey();

        manager.markUnreachable(oldId, 3L);

        assertTrue(listener.unreachableIds.isEmpty());
        assertFalse(manager.getCurrentParticipants().get(2).isUnreachable());
    }

    @Test
    void timeoutOfPreviousSurveyDoesNotCloseTheNextOne() {
        TestScheduler clock = new TestScheduler();
        SurveyManager leaky = new SurveyManager(communityManager, new UncancellableScheduler(clock));

        leaky.createSurvey(oneQuestion(), 0);
        leaky.markDistributionComplete(leaky.getCurrentSurvey().getId());
        clock.advance(Duration.ofSeconds(100));
        leaky.closeSurvey();

        leaky.createSurvey(oneQuestion(), 0);
        leaky.markDistributionComplete(leaky.getCurrentSurvey().getId());
        clock.advance(Duration.ofSeconds(AppConfig.SURVEY_DURATION_SECONDS - 100 + 1));

        assertTrue(leaky.isSurveyInProgress(),
                "הטיימאאוט של הסקר הקודם אינו אמור לסגור את הסקר החדש");
    }

    @Test
    void concurrentAnswersAreRecordedExactlyOnce() throws Exception {
        SurveyManager crowd = new SurveyManager(communityOf(300), new TestScheduler());
        RecordingSurveyListener crowdListener = new RecordingSurveyListener();
        crowd.addSurveyListener(crowdListener);
        crowd.createSurvey(threeQuestions(), 0);
        Survey survey = crowd.getCurrentSurvey();
        crowd.markDistributionComplete(survey.getId());

        AtomicInteger recorded = new AtomicInteger();
        runConcurrently(300, index -> {
            long telegramId = index + 1L;
            for (int question = 0; question < survey.getQuestions().size(); question++) {
                if (crowd.recordAnswer(survey.getId(), telegramId, question, 0)
                        == SurveyManager.AnswerResult.RECORDED) {
                    recorded.incrementAndGet();
                }
            }
        });

        assertEquals(900, recorded.get());
        assertFalse(crowd.isSurveyInProgress());
        assertEquals(1, crowdListener.closedCount);
        crowd.shutdown();
    }

    @Test
    void concurrentDuplicateAnswersAreRecordedOnce() throws Exception {
        SurveyManager crowd = new SurveyManager(communityOf(3), new TestScheduler());
        crowd.createSurvey(threeQuestions(), 0);
        Survey survey = crowd.getCurrentSurvey();
        crowd.markDistributionComplete(survey.getId());

        AtomicInteger recorded = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        runConcurrently(300, index -> {
            SurveyManager.AnswerResult result = crowd.recordAnswer(survey.getId(), 1L, 0, index % 2);
            if (result == SurveyManager.AnswerResult.RECORDED) {
                recorded.incrementAndGet();
            } else if (result == SurveyManager.AnswerResult.ALREADY_ANSWERED) {
                duplicates.incrementAndGet();
            }
        });

        assertEquals(1, recorded.get());
        assertEquals(299, duplicates.get());
        assertEquals(1, crowd.getCurrentParticipants().get(0).getAnsweredQuestionsCount());
        crowd.shutdown();
    }

    @Test
    void surveyIdIsAUuidThatFitsInCallbackData() {
        Survey survey = new Survey(threeQuestions(), 0);

        assertEquals(36, survey.getId().length());
        String data = CallbackData.encode(survey.getId(), 2, 3);
        assertTrue(data.getBytes(StandardCharsets.UTF_8).length <= CallbackData.MAX_BYTES);
    }

    @Test
    void surveyKeepsItsDelayMinutes() {
        assertEquals(7, new Survey(threeQuestions(), 7).getDelayMinutes());
    }
}
