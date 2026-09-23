package org.example;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * שעון וירטואלי — מאפשר לבדוק את כל לוגיקת 5 הדקות
 * בלי להמתין 5 דקות אמיתיות.
 */
public class TestScheduler implements SurveyScheduler {
    private static final class Task {
        private final Runnable runnable;
        private final long periodMillis;
        private final boolean repeating;
        private long dueMillis;
        private boolean cancelled;

        private Task(Runnable runnable, long dueMillis, long periodMillis, boolean repeating) {
            this.runnable = runnable;
            this.dueMillis = dueMillis;
            this.periodMillis = periodMillis;
            this.repeating = repeating;
        }
    }

    private final List<Task> tasks = new ArrayList<>();
    private long nowMillis;
    private boolean shutdown;

    @Override
    public Cancellable scheduleOnce(Runnable task, Duration delay) {
        return register(new Task(task, nowMillis + delay.toMillis(), 0L, false));
    }

    @Override
    public Cancellable scheduleTicks(Runnable task, Duration period) {
        return register(new Task(task, nowMillis + period.toMillis(), period.toMillis(), true));
    }

    @Override
    public long nowMillis() {
        return nowMillis;
    }

    private Cancellable register(Task task) {
        if (shutdown) {
            return () -> { };
        }
        tasks.add(task);
        return () -> {
            task.cancelled = true;
            tasks.remove(task);
        };
    }

    /** מקדם את השעון ומריץ כל משימה שהגיע זמנה, בסדר הנכון. */
    public void advance(Duration duration) {
        long target = nowMillis + duration.toMillis();
        while (true) {
            Task next = null;
            for (Task task : new ArrayList<>(tasks)) {
                if (task.cancelled || task.dueMillis > target) {
                    continue;
                }
                if (next == null || task.dueMillis < next.dueMillis) {
                    next = task;
                }
            }
            if (next == null) {
                break;
            }
            nowMillis = next.dueMillis;
            if (next.repeating) {
                next.dueMillis = nowMillis + next.periodMillis;
            } else {
                next.cancelled = true;
                tasks.remove(next);
            }
            next.runnable.run();
        }
        nowMillis = target;
    }

    @Override
    public void shutdown() {
        shutdown = true;
        tasks.clear();
    }
}