package org.example;

import java.time.Duration;

public interface SurveyScheduler
{

    Cancellable scheduleOnce(Runnable task, Duration delay);

    Cancellable scheduleTicks(Runnable task, Duration period);

    void shutdown();
    interface Cancellable
    {
        void cancel();
    }
}