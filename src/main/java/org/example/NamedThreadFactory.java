package org.example;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class NamedThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger();

    NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        return new Thread(runnable, prefix + "-" + counter.incrementAndGet());
    }
}
