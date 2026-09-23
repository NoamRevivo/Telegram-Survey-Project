package org.example;

public class SurveyGenerationException extends Exception {
    public SurveyGenerationException(String message) {
        super(message);
    }

    public SurveyGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}