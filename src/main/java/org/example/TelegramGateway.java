package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * שכבת התעבורה מול טלגרם בלבד — שליחה, retry, תורי עבודה וכיבוי מסודר.
 * היא אינה יודעת דבר על סקרים, קהילה או ניסוחים.
 */
public class TelegramGateway extends TelegramLongPollingBot {
    /** מי שמטפל בעדכונים הנכנסים — הפרדה בין התעבורה לבין הלוגיקה. */
    public interface UpdateHandler {
        void onMessage(Message message);

        void onCallback(CallbackQuery callbackQuery);
    }

    private static final Logger LOG = Logger.getLogger(TelegramGateway.class.getName());

    private final String botUsername;
    private final String botToken;
    private final UpdateHandler handler;

    /** הפצה במקביל למשתתפים, ותור נפרד לתזכורות ולהודעות סיום */
    private final ExecutorService notificationExecutor =
            Executors.newFixedThreadPool(AppConfig.NOTIFICATION_POOL_SIZE);
    private final ExecutorService priorityExecutor = Executors.newSingleThreadExecutor();

    public TelegramGateway(String botUsername, String botToken, UpdateHandler handler) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.handler = handler;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    /** עדכון פגום לא מפיל את חוט ה-polling של הספרייה. */
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handler.onMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handler.onCallback(update.getCallbackQuery());
            }
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "טיפול בעדכון מטלגרם נכשל", e);
        }
    }

    public boolean sendText(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        return send(message);
    }

    /**
     * שליחה עם ניסיון חוזר אחד כשטלגרם מחזיר 429 עם retryAfter.
     *
     * @return האם ההודעה נמסרה — הקורא מחליט מה לעשות בכישלון
     */
    public boolean send(BotApiMethod<?> method) {
        try {
            execute(method);
            return true;
        } catch (TelegramApiRequestException e) {
            Integer retryAfter = e.getParameters() != null ? e.getParameters().getRetryAfter() : null;
            if (retryAfter != null && retryAfter > 0) {
                LOG.warning("הגעה למגבלת קצב טלגרם, ממתין " + retryAfter + " שניות ומנסה שוב...");
                sleepMillis(retryAfter * 1000L);
                try {
                    execute(method);
                    return true;
                } catch (TelegramApiException retryEx) {
                    LOG.log(Level.WARNING, "שליחת הודעה נכשלה גם בניסיון החוזר", retryEx);
                }
            } else {
                LOG.log(Level.WARNING, "שליחת הודעה בטלגרם נכשלה", e);
            }
        } catch (TelegramApiException e) {
            LOG.log(Level.WARNING, "שליחת הודעה בטלגרם נכשלה", e);
        }
        return false;
    }

    public void runOnNotificationPool(String description, Runnable task) {
        runAsync(notificationExecutor, description, task);
    }

    public void runOnPriorityPool(String description, Runnable task) {
        runAsync(priorityExecutor, description, task);
    }

    /**
     * execute ולא submit.
     * submit קובר כל חריגה בתוך Future שאיש לא קורא — משתתף שלא קיבל את הסקר
     * בגלל NPE היה נראה «טרם ענה» לנצח, בלי שורת לוג אחת.
     */
    private void runAsync(ExecutorService executor, String description, Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException | Error e) {
                    LOG.log(Level.SEVERE, "משימה אסינכרונית נכשלה: " + description, e);
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "לא ניתן לתזמן משימה (המערכת בכיבוי?): " + description, e);
        }
    }

    public void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * shutdownNow קטע את הודעות הסיום באוויר.
     * כאן נותנים להן להסתיים, ורק אז כופים כיבוי.
     */
    public void shutdownGracefully(Duration timeout) {
        notificationExecutor.shutdown();
        priorityExecutor.shutdown();
        try {
            if (!priorityExecutor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                priorityExecutor.shutdownNow();
            }
            if (!notificationExecutor.awaitTermination(
                    AppConfig.NOTIFICATION_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                notificationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            priorityExecutor.shutdownNow();
            notificationExecutor.shutdownNow();
        }
    }
}