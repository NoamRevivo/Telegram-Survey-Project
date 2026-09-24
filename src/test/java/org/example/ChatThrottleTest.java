package org.example;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatThrottleTest {
    private final AtomicLong nanos = new AtomicLong();
    private final ChatThrottle throttle = new ChatThrottle(1_000L, nanos::get);

    @Test
    void firstRequestIsAllowedAndImmediateRepeatIsNot() {
        assertTrue(throttle.tryAcquire(1L));
        assertFalse(throttle.tryAcquire(1L));
    }

    @Test
    void requestIsAllowedAgainAfterTheInterval() {
        assertTrue(throttle.tryAcquire(1L));

        nanos.addAndGet(999_000_000L);
        assertFalse(throttle.tryAcquire(1L));

        nanos.addAndGet(1_000_000L);
        assertTrue(throttle.tryAcquire(1L));
    }

    @Test
    void chatsAreThrottledIndependently() {
        assertTrue(throttle.tryAcquire(1L));
        assertTrue(throttle.tryAcquire(2L));
        assertFalse(throttle.tryAcquire(1L));
    }

    @Test
    void ignoredRequestDoesNotExtendTheBlockedWindow() {
        assertTrue(throttle.tryAcquire(1L));
        nanos.addAndGet(600_000_000L);
        assertFalse(throttle.tryAcquire(1L));
        nanos.addAndGet(500_000_000L);

        assertTrue(throttle.tryAcquire(1L), "עברה שנייה מהתשובה האחרונה שנענתה");
    }
}
