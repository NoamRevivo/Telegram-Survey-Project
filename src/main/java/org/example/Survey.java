package org.example;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** סקר: שאלות, זמן דחייה וסטטוס. הוולידציה בבנאי. */
public class Survey {
    public static final int MIN_QUESTIONS = 1;
    public static final int MAX_QUESTIONS = 3;

    private final List<Question> questions;
    private final String id = UUID.randomUUID().toString();
    private final int delayMinutes;
    private volatile SurveyStatus status;
    private volatile LocalDateTime startTime;

    public Survey(List<Question> questions, int delayMinutes) {
        if (questions == null || questions.size() < MIN_QUESTIONS || questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException(
                    "סקר צריך להכיל " + MIN_QUESTIONS + "-" + MAX_QUESTIONS + " שאלות");
        }
        if (delayMinutes < 0 || delayMinutes > AppConfig.MAX_DELAY_MINUTES) {
            throw new IllegalArgumentException(
                    "זמן עיכוב חייב להיות בין 0 ל-" + AppConfig.MAX_DELAY_MINUTES + " דקות");
        }
        this.questions = new ArrayList<>(questions);
        this.delayMinutes = delayMinutes;
        this.status = SurveyStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public int getDelayMinutes() {
        return delayMinutes;
    }

    public SurveyStatus getStatus() {
        return status;
    }

    public void setStatus(SurveyStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
}
