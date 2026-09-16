package org.example;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Survey {
    private final List<Question> questions;
    private final int delayMinutes;
    private SurveyStatus status;
    private LocalDateTime startTime;

    public Survey(List<Question> questions, int delayMinutes) {
        if (questions == null || questions.size() < 1 || questions.size() > 3) {
            throw new IllegalArgumentException("סקר צריך להכיל 1-3 שאלות");
        }
        if (delayMinutes < 0) {
            throw new IllegalArgumentException("זמן עיכוב לא יכול להיות שלילי");
        }
        this.questions = new ArrayList<>(questions);
        this.delayMinutes = delayMinutes;
        this.status = SurveyStatus.PENDING;
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

    public Question getQuestionById(String id) {
        return questions.stream()
                .filter(q -> q.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}

