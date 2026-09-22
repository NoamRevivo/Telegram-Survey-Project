package org.example;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Survey {

    public static final int MIN_QUESTIONS = 1;
    public static final int MAX_QUESTIONS = 3;

    private final List<Question> questions;
    private final String id = String.format("%08x", ThreadLocalRandom.current().nextInt());
    private volatile SurveyStatus status;
    private volatile LocalDateTime startTime;

    public Survey(List<Question> questions, int delayMinutes) {
        if (questions == null || questions.size() < MIN_QUESTIONS || questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException(
                    "סקר צריך להכיל " + MIN_QUESTIONS + "-" + MAX_QUESTIONS + " שאלות");
        }
        if (delayMinutes < 0) {
            throw new IllegalArgumentException("זמן עיכוב לא יכול להיות שלילי");
        }
        this.questions = new ArrayList<>(questions);
        this.status = SurveyStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
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