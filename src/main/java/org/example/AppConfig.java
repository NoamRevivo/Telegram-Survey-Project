package org.example;

import java.time.Duration;

/**
 * כל הערך הקשיח של המערכת במקום אחד.
 * מה שניתן להגדיר מבחוץ נקרא ממשתנה סביבה עם ברירת מחדל.
 */
public final class AppConfig {
    /* ---------- משתני סביבה ---------- */
    public static final String ENV_BOT_USERNAME = "BOT_USERNAME";
    public static final String ENV_BOT_TOKEN = "BOT_TOKEN";
    public static final String ENV_SURVEY_API_TOKEN = "SURVEY_API_TOKEN";
    public static final String ENV_SURVEY_API_URL = "SURVEY_API_URL";

    public static final String DEFAULT_SURVEY_API_URL =
            "https://shaitest-production-3066.up.railway.app/api-request";

    /* ---------- לוגיקת הסקר ---------- */
    public static final int SECONDS_PER_MINUTE = 60;
    public static final int SURVEY_DURATION_SECONDS = 300;
    public static final int REMINDER_DELAY_SECONDS = 180;
    /** תצוגה בלבד: פאנל הניהול מהבהב באדום כשנותרו פחות מהזמן הזה. לא נשלחת הודעה למשתתפים. */
    public static final int URGENT_SECONDS_BEFORE_END = 30;
    public static final int MIN_COMMUNITY_SIZE = 3;
    public static final int SCHEDULER_POOL_SIZE = 2;
    /** אם ההפצה לא דיווחה שהסתיימה, השעון מתחיל בכל מקרה אחרי הזמן הזה */
    public static final int DISTRIBUTION_WATCHDOG_SECONDS = 60;
    /** תקרת הדחייה (בדקות) שניתן להזין בשדה הדחייה החופשי ביצירת סקר. */
    public static final int MAX_DELAY_MINUTES = 240;

    /* ---------- טלגרם ---------- */
    public static final int NOTIFICATION_POOL_SIZE = 4;
    /** קצב ההפצה — 100ms מספיקים ורחוקים ממגבלת 30 הודעות/שנייה */
    public static final long INTRO_DELAY_MILLIS = 100L;
    public static final long QUESTION_DELAY_MILLIS = 100L;
    /** כמה להמתין לסיום הודעות שנמצאות באוויר לפני כיבוי כפוי */
    public static final Duration PRIORITY_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration NOTIFICATION_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration SCHEDULER_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    /* ---------- שירות יצירת השאלות ---------- */
    public static final Duration API_TIMEOUT = Duration.ofSeconds(20);
    public static final int RESPONSE_SNIPPET_LENGTH = 200;

    /* ---------- ממשק המשתמש ---------- */
    public static final int WINDOW_WIDTH = 1050;
    public static final int WINDOW_HEIGHT = 740;
    public static final int WINDOW_MIN_WIDTH = 880;
    public static final int WINDOW_MIN_HEIGHT = 640;
    public static final int TABLE_ROW_HEIGHT = 28;
    public static final int HIGHLIGHT_MILLIS = 1500;
    public static final int ELAPSED_TICK_MILLIS = 1000;
    public static final int OPTION_LABEL_WIDTH = 170;
    public static final int OPTION_LABEL_HEIGHT = 22;
    public static final int PROGRESS_BAR_HEIGHT = 14;
    public static final int LOADING_BAR_WIDTH = 320;
    public static final int TOPIC_DISPLAY_MAX_CHARS = 40;
    public static final int SLOW_RESPONSE_SECONDS = 12;
    public static final float BAR_WARN_RATIO = 0.5f;
    public static final float BAR_DANGER_RATIO = 0.2f;

    private AppConfig() {
    }

    public static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    public static String env(String name) {
        return env(name, null);
    }
}