package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotNotifierTest {
    private FakeGateway gateway;
    private TestScheduler scheduler;
    private SurveyManager manager;
    private RecordingSurveyListener listener;

    @BeforeEach
    void setUp() {
        CommunityManager community = new CommunityManager();
        community.addMember(1L, "אורי", "uri");
        community.addMember(2L, "דנה", "dana");
        community.addMember(3L, "יוסי", "yossi");

        gateway = new FakeGateway();
        scheduler = new TestScheduler();
        manager = new SurveyManager(community, scheduler);
        listener = new RecordingSurveyListener();
        manager.addSurveyListener(listener);
        manager.addSurveyListener(new BotNotifier(gateway, community, manager));
    }

    private void startSurvey() {
        manager.createSurvey(List.of(
                new Question("שאלה א", List.of("כן", "לא")),
                new Question("שאלה ב", List.of("כן", "לא", "אולי"))), 0);
    }

    @Test
    void blockedParticipantIsMarkedUnreachable() {
        gateway.respondWith(3L, TelegramGateway.SendResult.BLOCKED);

        startSurvey();

        assertEquals(List.of(3L), listener.unreachableIds);
    }

    @Test
    void transientFailureDoesNotMarkParticipantUnreachable() {
        gateway.respondWith(3L, TelegramGateway.SendResult.TRANSIENT_FAILURE);

        startSurvey();

        assertTrue(listener.unreachableIds.isEmpty());
    }

    @Test
    void rejectedContentDoesNotMarkParticipantUnreachable() {
        gateway.respondWith(2L, TelegramGateway.SendResult.REJECTED);

        startSurvey();

        assertTrue(listener.unreachableIds.isEmpty());
    }

    @Test
    void clockStartsEvenWhenSomeParticipantsFailed() {
        gateway.respondWith(1L, TelegramGateway.SendResult.BLOCKED);
        gateway.respondWith(2L, TelegramGateway.SendResult.TRANSIENT_FAILURE);

        startSurvey();
        scheduler.advance(Duration.ofSeconds(3));

        assertTrue(listener.totalTicks > 0, "השעון אמור להתחיל אחרי שההפצה דיווחה שהסתיימה");
    }

    @Test
    void everyReachableParticipantReceivesIntroAndAllQuestions() {
        startSurvey();

        for (long chatId = 1; chatId <= 3; chatId++) {
            assertEquals(3, gateway.sentTo(chatId).size(), "פתיחה ושתי שאלות למשתתף " + chatId);
        }
        assertTrue(listener.unreachableIds.isEmpty());
    }

    @Test
    void questionButtonsCarryTheSurveyIdAndIndexes() {
        startSurvey();
        String surveyId = manager.getCurrentSurvey().getId();

        SendMessage secondQuestion = gateway.sentTo(1L).get(2);
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) secondQuestion.getReplyMarkup();
        String data = markup.getKeyboard().get(2).get(0).getCallbackData();

        assertEquals(new CallbackData(surveyId, 1, 2), CallbackData.parse(data));
    }

    @Test
    void surveyClosesEarlyWhenTheOnlyOutstandingParticipantIsUnreachable() {
        gateway.respondWith(3L, TelegramGateway.SendResult.BLOCKED);
        startSurvey();
        String surveyId = manager.getCurrentSurvey().getId();
        assertTrue(manager.isSurveyInProgress());

        for (long telegramId = 1; telegramId <= 2; telegramId++) {
            manager.recordAnswer(surveyId, telegramId, 0, 0);
            manager.recordAnswer(surveyId, telegramId, 1, 1);
        }

        assertFalse(manager.isSurveyInProgress());
        assertEquals(1, listener.closedCount);
    }

    /** כשל באמצע הרצף: הפצה חוזרת שולחת רק את מה שחסר, בלי כפילויות, ולא תזכורת על שאלה שלא הגיעה. */
    @Test
    void failureMidSequenceIsResumedWithOnlyTheMissingQuestion() {
        gateway.respondWithSequence(2L,
                TelegramGateway.SendResult.DELIVERED,
                TelegramGateway.SendResult.DELIVERED,
                TelegramGateway.SendResult.REJECTED);

        startSurvey();

        assertEquals(2, gateway.sentTo(2L).size(), "פתיחה ושאלה ראשונה בלבד");
        assertEquals(List.of(2L), listener.deliveryChangedIds);
        assertTrue(listener.unreachableIds.isEmpty());

        scheduler.advance(Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS + 1));

        assertEquals(3, gateway.sentTo(2L).size(), "השאלה השנייה נשלחה פעם אחת, ללא תזכורת");
        assertTrue(gateway.sentTo(2L).get(2).getText().contains("שאלה ב"));
        assertEquals(List.of(2L, 2L), listener.deliveryChangedIds, "הכשל נוקה אחרי הצלחה");
        assertEquals(4, gateway.sentTo(1L).size(), "3 הודעות הסקר ועוד תזכורת");
    }

    @Test
    void introFailureRetriesFromTheIntroOnReminder() {
        gateway.respondWithSequence(3L, TelegramGateway.SendResult.TRANSIENT_FAILURE);

        startSurvey();
        assertEquals(0, gateway.sentTo(3L).size());

        scheduler.advance(Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS + 1));

        assertEquals(3, gateway.sentTo(3L).size(), "פתיחה ושתי שאלות");
    }

    @Test
    void blockedDuringResendMakesParticipantUnreachable() {
        gateway.respondWithSequence(3L,
                TelegramGateway.SendResult.TRANSIENT_FAILURE,
                TelegramGateway.SendResult.BLOCKED);

        startSurvey();
        scheduler.advance(Duration.ofSeconds(AppConfig.REMINDER_DELAY_SECONDS + 1));

        assertEquals(List.of(3L), listener.unreachableIds);
    }

    @Test
    void newMemberBroadcastReachesEveryoneExceptTheNewMember() {
        CommunityManager community = new CommunityManager();
        community.addMember(1L, "אורי", "uri");
        community.addMember(2L, "דנה", "dana");
        community.addListener(new BotNotifier(gateway, community, manager));

        community.addMember(3L, "יוסי", "yossi");

        assertEquals(1, gateway.sentTo(1L).size());
        assertEquals(1, gateway.sentTo(2L).size());
        assertEquals(0, gateway.sentTo(3L).size());
    }

    @Test
    void oneFailingRecipientDoesNotStopTheBroadcast() {
        CommunityManager community = new CommunityManager();
        community.addMember(1L, "אורי", "uri");
        community.addMember(2L, "דנה", "dana");
        community.addListener(new BotNotifier(gateway, community, manager));
        gateway.respondWith(1L, TelegramGateway.SendResult.BLOCKED);

        community.addMember(3L, "יוסי", "yossi");

        assertEquals(1, gateway.sentTo(2L).size());
    }
}
