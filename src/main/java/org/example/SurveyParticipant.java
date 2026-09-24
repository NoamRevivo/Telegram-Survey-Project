package org.example;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SurveyParticipant {
    private final CommunityUser user;
    private volatile ParticipantStatus status;
    private volatile boolean unreachable;
    private final Map<String, String> answersByQuestionId = new ConcurrentHashMap<>();

    public SurveyParticipant(CommunityUser user) {
        this.user = user;
        this.status = ParticipantStatus.NOT_STARTED;
    }

    public CommunityUser getUser() {
        return user;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public boolean isUnreachable() {
        return unreachable;
    }

    /** ההודעות לא הגיעו אליו (חסם את הבוט וכו') — אינו חוסם סגירה מוקדמת ואינו מקבל תזכורת. */
    public void markUnreachable() {
        unreachable = true;
    }

    public int getAnsweredQuestionsCount() {
        return answersByQuestionId.size();
    }

    public boolean hasAnswered(String questionId) {
        return answersByQuestionId.containsKey(questionId);
    }

    public boolean isCompleted() {
        return status == ParticipantStatus.COMPLETED;
    }

    public Map<String, String> getAnswers() {
        return Collections.unmodifiableMap(answersByQuestionId);
    }

    public void recordAnswer(String questionId, String answer, int totalQuestionsInSurvey) {
        answersByQuestionId.put(questionId, answer);
        if (answersByQuestionId.size() >= totalQuestionsInSurvey) {
            this.status = ParticipantStatus.COMPLETED;
        } else {
            this.status = ParticipantStatus.IN_PROGRESS;
        }
    }
}
