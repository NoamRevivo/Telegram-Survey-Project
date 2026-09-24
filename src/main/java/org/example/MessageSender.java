package org.example;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;

/**
 * מה ש-{@link BotNotifier} צריך מהעולם שמעבר לו: שליחה, המתנה ותורי עבודה.
 * הממשק מאפשר לבדוק אותו בלי מחלקת הבוט של הספרייה (ובלי חוטים אמיתיים),
 * ואת {@link TelegramGateway} להחליף בלי לגעת בלוגיקת ההפצה.
 */
public interface MessageSender {
    boolean sendText(long chatId, String text);

    TelegramGateway.SendResult trySendText(long chatId, String text);

    boolean send(BotApiMethod<?> method);

    TelegramGateway.SendResult trySend(BotApiMethod<?> method);

    void sleepMillis(long millis);

    void runOnNotificationPool(String description, Runnable task);

    void runOnPriorityPool(String description, Runnable task);

    void runOnReplyPool(String description, Runnable task);
}
