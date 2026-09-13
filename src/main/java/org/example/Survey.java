package org.example;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class Survey {
    private final List<Question> questions;
    private final int delayMinutes;
    private SurveyStatus status;
    private LocalDateTime startTime;

    public Survey(List<Question> questions, int delayMinutes) {
        this.questions = questions;
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

