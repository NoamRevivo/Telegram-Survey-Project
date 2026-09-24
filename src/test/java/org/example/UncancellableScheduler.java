package org.example;

import java.time.Duration;

/**
 * מדמה משימה שכבר רצה או ממתינה למנעול ברגע הביטול: cancel(false) אינו עוצר אותה,
 * ולכן רק הגנת ה-generation בתוך המנהל מונעת ממנה לפגוע בסקר הבא.
 */
public class UncancellableScheduler implements SurveyScheduler {
    private final SurveyScheduler delegate;

    public UncancellableScheduler(SurveyScheduler delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cancellable scheduleOnce(Runnable task, Duration delay) {
        delegate.scheduleOnce(task, delay);
        return () -> { };
    }

    @Override
    public Cancellable scheduleTicks(Runnable task, Duration period) {
        delegate.scheduleTicks(task, period);
        return () -> { };
    }

    @Override
    public long nowMillis() {
        return delegate.nowMillis();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }
}
