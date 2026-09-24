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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * שכבת התעבורה מול טלגרם בלבד — שליחה, retry, תורי עבודה וכיבוי מסודר.
 * היא אינה יודעת דבר על סקרים, קהילה או ניסוחים.
 */
public class TelegramGateway extends TelegramLongPollingBot implements MessageSender {
    /** תוצאת שליחה מפורטת — הקורא מבדיל בין חסימה קבועה לבין תקלה זמנית. */
    public enum SendResult {
        DELIVERED,
        BLOCKED,
        REJECTED,
        TRANSIENT_FAILURE
    }

    /** מי שמטפל בעדכונים הנכנסים — הפרדה בין התעבורה לבין הלוגיקה. */
    public interface UpdateHandler {
        void onMessage(Message message);

        void onCallback(CallbackQuery callbackQuery);

        /** הודעה שאינה טקסט (מדבקה, תמונה...) בשיחה עם הבוט. ברירת המחדל: להתעלם. */
        default void onUnsupportedMessage(Message message) {
        }
    }

    private record Outcome(SendResult result, Integer retryAfterSeconds) {
    }

    private static final Logger LOG = Logger.getLogger(TelegramGateway.class.getName());
    private static final int CODE_BAD_REQUEST = 400;
    private static final int CODE_FORBIDDEN = 403;
    private static final long MILLIS_PER_SECOND = 1000L;

    private final String botUsername;
    private final String botToken;
    private final UpdateHandler handler;

    /** הפצה במקביל למשתתפים, ותור נפרד לתזכורות ולהודעות סיום */
    private final ExecutorService notificationExecutor =
            Executors.newFixedThreadPool(AppConfig.NOTIFICATION_POOL_SIZE, new NamedThreadFactory("bot-notify"));
    private final ExecutorService priorityExecutor =
            Executors.newSingleThreadExecutor(new NamedThreadFactory("bot-priority"));
    private final ExecutorService ackExecutor =
            Executors.newFixedThreadPool(AppConfig.ACK_POOL_SIZE, new NamedThreadFactory("bot-ack"));
    /** תשובות לפקודות — לא על חוט ה-polling, כדי שהמתנה ל-429 לא תעכב לחיצות על כפתורי הסקר */
    private final ExecutorService replyExecutor =
            Executors.newFixedThreadPool(AppConfig.REPLY_POOL_SIZE, new NamedThreadFactory("bot-reply"));

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
            } else if (update.hasMessage()) {
                handler.onUnsupportedMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handler.onCallback(update.getCallbackQuery());
            }
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "טיפול בעדכון מטלגרם נכשל", e);
        }
    }

    @Override
    public boolean sendText(long chatId, String text) {
        return trySendText(chatId, text) == SendResult.DELIVERED;
    }

    @Override
    public SendResult trySendText(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        return trySend(message);
    }

    @Override
    public boolean send(BotApiMethod<?> method) {
        return trySend(method) == SendResult.DELIVERED;
    }

    /**
     * שליחה עם ניסיונות חוזרים על כישלון זמני בלבד (429, 5xx, תקלת רשת), עם המתנה גדלה.
     * חסימה של המשתתף (403) ותוכן פסול (400) אינם נחזרים — ניסיון נוסף לא ישנה אותם.
     * <p>
     * סמנטיקה של "לפחות פעם אחת": אם הבקשה הגיעה לטלגרם אבל התשובה נקטעה (timeout בקריאה), הניסיון החוזר
     * עלול לשלוח את ההודעה פעמיים. לכפתורי הסקר זה בטוח — שני הכפתורים נושאים אותם נתונים, והתשובה השנייה נדחית כ"כבר ענית".
     */
    @Override
    public SendResult trySend(BotApiMethod<?> method) {
        for (int attempt = 1; attempt <= AppConfig.SEND_MAX_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                return SendResult.TRANSIENT_FAILURE;
            }
            Outcome outcome = attemptOnce(method);
            if (outcome.result() != SendResult.TRANSIENT_FAILURE) {
                return outcome.result();
            }
            if (attempt == AppConfig.SEND_MAX_ATTEMPTS) {
                break;
            }
            long wait = backoffMillis(outcome.retryAfterSeconds(), attempt);
            if (wait > AppConfig.SEND_MAX_BACKOFF_MILLIS) {
                LOG.warning("טלגרם ביקש להמתין יותר מהתקרה (" + wait + " מילישניות) — מוותר על ההודעה");
                break;
            }
            LOG.warning("שליחה נכשלה זמנית (ניסיון " + attempt + " מתוך "
                    + AppConfig.SEND_MAX_ATTEMPTS + "), ממתין " + wait + " מילישניות");
            sleepMillis(wait);
        }
        return SendResult.TRANSIENT_FAILURE;
    }

    /** ניסיון יחיד ללא המתנה — לאישורי לחיצה, שאסור שיחסמו את חוט ה-polling. */
    public SendResult sendOnce(BotApiMethod<?> method) {
        return attemptOnce(method).result();
    }

    /** נקודת החיבור היחידה לספרייה — ניתנת להחלפה בבדיקות של לוגיקת ה-retry. */
    protected void executeApi(BotApiMethod<?> method) throws TelegramApiException {
        execute(method);
    }

    private Outcome attemptOnce(BotApiMethod<?> method) {
        try {
            executeApi(method);
            return new Outcome(SendResult.DELIVERED, null);
        } catch (TelegramApiRequestException e) {
            Integer code = e.getErrorCode();
            if (code != null && code == CODE_FORBIDDEN) {
                LOG.log(Level.INFO, "ההודעה לא נמסרה: המשתמש חסם את הבוט או עזב את השיחה");
                return new Outcome(SendResult.BLOCKED, null);
            }
            if (code != null && code == CODE_BAD_REQUEST) {
                LOG.log(Level.WARNING, "טלגרם דחה את ההודעה (תוכן או יעד לא תקינים)", e);
                return new Outcome(SendResult.REJECTED, null);
            }
            Integer retryAfter = e.getParameters() != null ? e.getParameters().getRetryAfter() : null;
            LOG.log(Level.WARNING, "שליחת הודעה בטלגרם נכשלה, קוד " + code, e);
            return new Outcome(SendResult.TRANSIENT_FAILURE, retryAfter);
        } catch (TelegramApiException e) {
            LOG.log(Level.WARNING, "שליחת הודעה בטלגרם נכשלה", e);
            return new Outcome(SendResult.TRANSIENT_FAILURE, null);
        }
    }

    private long backoffMillis(Integer retryAfterSeconds, int attempt) {
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            return retryAfterSeconds * MILLIS_PER_SECOND;
        }
        return AppConfig.SEND_BACKOFF_BASE_MILLIS * (1L << (attempt - 1));
    }

    @Override
    public void runOnNotificationPool(String description, Runnable task) {
        runAsync(notificationExecutor, description, task);
    }

    @Override
    public void runOnPriorityPool(String description, Runnable task) {
        runAsync(priorityExecutor, description, task);
    }

    @Override
    public void runOnReplyPool(String description, Runnable task) {
        runAsync(replyExecutor, description, task);
    }

    /** אישורי לחיצה בתור נפרד: 429 באישור אחד לא יעצור את חוט ה-polling ואת שאר העדכונים. */
    public void runOnAckPool(String description, Runnable task) {
        runAsync(ackExecutor, description, task);
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
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "משימה אסינכרונית נכשלה: " + description, e);
                } catch (Error e) {
                    // שגיאת JVM (זיכרון, מחסנית) אינה מוסתרת: נרשמת וממשיכה למעלה
                    LOG.log(Level.SEVERE, "שגיאה חמורה במשימה: " + description, e);
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "לא ניתן לתזמן משימה (המערכת בכיבוי?): " + description, e);
        }
    }

    @Override
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
        List<ExecutorService> executors =
                List.of(priorityExecutor, notificationExecutor, ackExecutor, replyExecutor);
        executors.forEach(ExecutorService::shutdown);
        try {
            awaitOrForce(priorityExecutor, timeout);
            awaitOrForce(notificationExecutor, AppConfig.NOTIFICATION_SHUTDOWN_TIMEOUT);
            awaitOrForce(ackExecutor, AppConfig.NOTIFICATION_SHUTDOWN_TIMEOUT);
            awaitOrForce(replyExecutor, AppConfig.NOTIFICATION_SHUTDOWN_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executors.forEach(ExecutorService::shutdownNow);
        }
    }

    private static void awaitOrForce(ExecutorService executor, Duration timeout) throws InterruptedException {
        if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            executor.shutdownNow();
        }
    }
}
