package org.example;

import java.time.Duration;


public final class AppConfig {
    public static final String ENV_BOT_USERNAME = "BOT_USERNAME";
    public static final String ENV_BOT_TOKEN = "BOT_TOKEN";
    public static final String ENV_SURVEY_API_TOKEN = "SURVEY_API_TOKEN";
    public static final String ENV_SURVEY_API_URL = "SURVEY_API_URL";

    public static final String DEFAULT_SURVEY_API_URL =
            "https://shaitest-production-3066.up.railway.app/api-request";

    public static final int SECONDS_PER_MINUTE = 60;
    public static final int SURVEY_DURATION_SECONDS = 300;
    public static final int REMINDER_DELAY_SECONDS = 180;
    public static final int URGENT_SECONDS_BEFORE_END = 30;
    public static final int MIN_COMMUNITY_SIZE = 3;
    public static final int SCHEDULER_POOL_SIZE = 2;
    public static final int DISTRIBUTION_WATCHDOG_SECONDS = 60;
    public static final int MAX_DELAY_MINUTES = 240;

    public static final int NOTIFICATION_POOL_SIZE = 4;
    public static final int ACK_POOL_SIZE = 2;
    public static final int REPLY_POOL_SIZE = 2;
    public static final long REPLY_MIN_INTERVAL_MILLIS = 1_000L;
    public static final int SEND_MAX_ATTEMPTS = 3;
    public static final long SEND_BACKOFF_BASE_MILLIS = 500L;
    public static final long SEND_MAX_BACKOFF_MILLIS = 10_000L;
    public static final long INTRO_DELAY_MILLIS = 100L;
    public static final long QUESTION_DELAY_MILLIS = 100L;
    public static final Duration PRIORITY_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration NOTIFICATION_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration SCHEDULER_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    public static final Duration API_TIMEOUT = Duration.ofSeconds(20);
    public static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(30);
    public static final int RESPONSE_SNIPPET_LENGTH = 200;
    public static final long MAX_RESPONSE_BYTES = 256L * 1024L;
    public static final int MAX_TOPIC_CHARS = 200;

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
    public static final int MAX_STACKED_TOASTS = 3;
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