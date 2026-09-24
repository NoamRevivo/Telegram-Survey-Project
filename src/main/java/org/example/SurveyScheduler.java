package org.example;

import java.time.Duration;

/** תזמון משימות לסקר — ניתן להחלפה בשעון וירטואלי בבדיקות. */
public interface SurveyScheduler {
    Cancellable scheduleOnce(Runnable task, Duration delay);

    Cancellable scheduleTicks(Runnable task, Duration period);

    /** שעון מונוטוני במילישניות — הספירה לאחור נגזרת ממנו ולא מספירת טיקים. */
    long nowMillis();

    void shutdown();

    interface Cancellable {
        void cancel();
    }
}