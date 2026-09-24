package org.example;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * שער טלגרם מדומה: אין רשת, אין חוטים ואין המתנות — כל משימה רצה מיד, באותו חוט,
 * והתוצאה של כל צ'אט נקבעת מראש.
 */
public class FakeGateway extends TelegramGateway {
    private final Map<Long, SendResult> resultsByChat = new HashMap<>();
    private final List<SendMessage> sent = Collections.synchronizedList(new ArrayList<>());

    public FakeGateway() {
        super("bot", "token", new NoopHandler());
    }

    public void respondWith(long chatId, SendResult result) {
        resultsByChat.put(chatId, result);
    }

    public List<SendMessage> sentTo(long chatId) {
        List<SendMessage> forChat = new ArrayList<>();
        synchronized (sent) {
            for (SendMessage message : sent) {
                if (message.getChatId().equals(String.valueOf(chatId))) {
                    forChat.add(message);
                }
            }
        }
        return forChat;
    }

    @Override
    public SendResult trySend(BotApiMethod<?> method) {
        if (!(method instanceof SendMessage message)) {
            return SendResult.DELIVERED;
        }
        SendResult result = resultsByChat.getOrDefault(Long.parseLong(message.getChatId()), SendResult.DELIVERED);
        if (result == SendResult.DELIVERED) {
            sent.add(message);
        }
        return result;
    }

    @Override
    public void sleepMillis(long millis) {
    }

    @Override
    public void runOnNotificationPool(String description, Runnable task) {
        task.run();
    }

    @Override
    public void runOnPriorityPool(String description, Runnable task) {
        task.run();
    }

    private static final class NoopHandler implements UpdateHandler {
        @Override
        public void onMessage(Message message) {
        }

        @Override
        public void onCallback(CallbackQuery callbackQuery) {
        }
    }
}
