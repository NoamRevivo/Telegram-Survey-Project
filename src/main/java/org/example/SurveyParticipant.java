package org.example;
public class SurveyParticipant
{
    private CommunityUser user;
    private ParticipantStatus status;
    private int answeredQuestionsCount;

    public SurveyParticipant(CommunityUser user)
    {
        this.user = user;
        this.status = ParticipantStatus.NOT_STARTED;
        this.answeredQuestionsCount = 0;
    }

    public CommunityUser getUser() { return user; }
    public ParticipantStatus getStatus() { return status; }
    public int getAnsweredQuestionsCount() { return answeredQuestionsCount; }

    public void recordAnswer(int totalQuestionsInSurvey) {
        this.answeredQuestionsCount++;
        if (this.answeredQuestionsCount >= totalQuestionsInSurvey) {
            this.status = ParticipantStatus.COMPLETED;
        }
        else
        {
            this.status = ParticipantStatus.IN_PROGRESS;
        }
    }
}