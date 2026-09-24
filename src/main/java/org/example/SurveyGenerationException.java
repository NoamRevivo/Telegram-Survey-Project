package org.example;

/** כשל ביצירת שאלות אוטומטית; ההודעה מיועדת להצגה למנהל. */
public class SurveyGenerationException extends Exception {
    public SurveyGenerationException(String message) {
        super(message);
    }

    public SurveyGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}