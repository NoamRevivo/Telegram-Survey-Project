package org.example;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * המימוש האמיתי מעל ScheduledExecutorService.
 * כל משימה עטופה ב-try/catch — חריגה בטיק אינה מבטלת בשקט את התזמון.
 */
public final class DefaultSurveyScheduler implements SurveyScheduler {
    private static final Logger LOG = Logger.getLogger(DefaultSurveyScheduler.class.getName());

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(AppConfig.SCHEDULER_POOL_SIZE);

    @Override
    public Cancellable scheduleOnce(Runnable task, Duration delay) {
        ScheduledFuture<?> future = scheduler.schedule(
                guard(task), delay.toMillis(), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public Cancellable scheduleTicks(Runnable task, Duration period) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                guard(task), period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public long nowMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(
                    AppConfig.SCHEDULER_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    private Runnable guard(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                LOG.log(Level.SEVERE, "משימת תזמון נכשלה", e);
            }
        };
    }
}