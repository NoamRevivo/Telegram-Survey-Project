package org.example;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** לוגיקת הניסיונות החוזרים והגיבוי של השער, בלי רשת ובלי המתנה אמיתית. */
class TelegramGatewayTest {

    private static final class ScriptedGateway extends TelegramGateway {
        private final int failuresBeforeSuccess;
        final List<Long> sleeps = new ArrayList<>();
        int calls;

        ScriptedGateway(int failuresBeforeSuccess) {
            super("bot", "token", new UpdateHandler() {
                @Override
                public void onMessage(Message message) {
                }

                @Override
                public void onCallback(CallbackQuery callbackQuery) {
                }
            });
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        protected void executeApi(BotApiMethod<?> method) throws TelegramApiException {
            calls++;
            if (calls <= failuresBeforeSuccess) {
                throw new TelegramApiException("timeout");
            }
        }

        @Override
        public void sleepMillis(long millis) {
            sleeps.add(millis);
        }
    }

    private static SendMessage message() {
        SendMessage message = new SendMessage();
        message.setChatId("1");
        message.setText("שלום");
        return message;
    }

    @Test
    void transientFailuresAreRetriedWithGrowingBackoffUntilDelivered() {
        ScriptedGateway gateway = new ScriptedGateway(2);

        TelegramGateway.SendResult result = gateway.trySend(message());

        assertEquals(TelegramGateway.SendResult.DELIVERED, result);
        assertEquals(3, gateway.calls);
        assertEquals(List.of(AppConfig.SEND_BACKOFF_BASE_MILLIS, AppConfig.SEND_BACKOFF_BASE_MILLIS * 2),
                gateway.sleeps);
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttempts() {
        ScriptedGateway gateway = new ScriptedGateway(Integer.MAX_VALUE);

        TelegramGateway.SendResult result = gateway.trySend(message());

        assertEquals(TelegramGateway.SendResult.TRANSIENT_FAILURE, result);
        assertEquals(AppConfig.SEND_MAX_ATTEMPTS, gateway.calls);
        assertEquals(AppConfig.SEND_MAX_ATTEMPTS - 1, gateway.sleeps.size());
    }

    @Test
    void sendOnceNeverRetriesOrSleeps() {
        ScriptedGateway gateway = new ScriptedGateway(Integer.MAX_VALUE);

        TelegramGateway.SendResult result = gateway.sendOnce(message());

        assertEquals(TelegramGateway.SendResult.TRANSIENT_FAILURE, result);
        assertEquals(1, gateway.calls);
        assertTrue(gateway.sleeps.isEmpty());
    }

    @Test
    void sendTextReportsTrueOnlyWhenDelivered() {
        assertTrue(new ScriptedGateway(1).sendText(1L, "שלום"));
        assertEquals(false, new ScriptedGateway(Integer.MAX_VALUE).sendText(1L, "שלום"));
    }

    @Test
    void gracefulShutdownLetsQueuedWorkFinish() {
        ScriptedGateway gateway = new ScriptedGateway(0);
        AtomicInteger done = new AtomicInteger();
        gateway.runOnPriorityPool("א", done::incrementAndGet);
        gateway.runOnNotificationPool("ב", done::incrementAndGet);
        gateway.runOnReplyPool("ג", done::incrementAndGet);
        gateway.runOnAckPool("ד", done::incrementAndGet);

        gateway.shutdownGracefully(java.time.Duration.ofSeconds(5));

        assertEquals(4, done.get());
    }
}
