package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;


final class ChatThrottle {
    private final long minIntervalNanos;
    private final LongSupplier nanoClock;
    private final Map<Long, Long> lastAllowedByChat = new ConcurrentHashMap<>();

    ChatThrottle(long minIntervalMillis, LongSupplier nanoClock) {
        this.minIntervalNanos = minIntervalMillis * 1_000_000L;
        this.nanoClock = nanoClock;
    }

    ChatThrottle(long minIntervalMillis) {
        this(minIntervalMillis, System::nanoTime);
    }

    boolean tryAcquire(long chatId) {
        long now = nanoClock.getAsLong();
        boolean[] allowed = {false};
        lastAllowedByChat.compute(chatId, (id, previous) -> {
            if (previous == null || now - previous >= minIntervalNanos) {
                allowed[0] = true;
                return now;
            }
            return previous;
        });
        return allowed[0];
    }
}
