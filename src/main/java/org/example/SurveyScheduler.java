package org.example;

import java.time.Duration;

/**
 * R5-M08 / R5-M12: ממשק תזמון מינימלי, כדי ש-SurveyManager לא יהיה קשור
 * ל-ScheduledExecutorService ושאפשר יהיה לבדוק את לוגיקת 5 הדקות בלי להמתין 5 דקות.
 */
public interface SurveyScheduler {

    /** מריץ את המשימה פעם אחת אחרי ההשהיה. */
    Cancellable scheduleOnce(Runnable task, Duration delay);

    /** מריץ את המשימה שוב ושוב בקצב הנתון. */
    Cancellable scheduleTicks(Runnable task, Duration period);

    /** משחרר את משאבי התזמון. */
    void shutdown();

    /** ידית ביטול אחת, בלי לחשוף את ScheduledFuture החוצה. */
    interface Cancellable {
        void cancel();
    }
}