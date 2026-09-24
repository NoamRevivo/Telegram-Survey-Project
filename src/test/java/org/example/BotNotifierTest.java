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
}
