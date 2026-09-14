package org.example;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SurveyParticipant
{
    private final CommunityUser user;
    private ParticipantStatus status;
    private final Map<String, String> answersByQuestionId = new LinkedHashMap<>();

    public SurveyParticipant(CommunityUser user)
    {
        this.user = user;
        this.status = ParticipantStatus.NOT_STARTED;
    }

    public CommunityUser getUser() { return user;}
    public ParticipantStatus getStatus() { return status; }
    public int getAnsweredQuestionsCount() { return answersByQuestionId.size(); }

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