package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * מגביל תשובות אוטומטיות לצ'אט: לכל היותר אחת בכל פרק זמן.
 * משתמש שמציף את הבוט בהודעות לא יוצר תור תשובות אינסופי ולא מביא 429 מטלגרם.
 */
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

    /** @return true אם מותר לענות לצ'אט הזה עכשיו (וסופר את התשובה); false אם צריך להתעלם. */
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
