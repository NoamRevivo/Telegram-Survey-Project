package org.example;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** חבר קהילה בתוך סקר מסוים: תשובותיו, התקדמותו ומצב ההפצה אליו. */
public class SurveyParticipant {
    private final CommunityUser user;
    private volatile ParticipantStatus status;
    private volatile boolean unreachable;
    private final Map<String, String> answersByQuestionId = new ConcurrentHashMap<>();

    /* מעקב הפצה: מה כבר נמסר לו — כדי שניסיון חוזר ישלח רק את מה שחסר ולא יכפיל הודעות */
    private volatile boolean introDelivered;
    private volatile int questionsDelivered;
    private volatile boolean deliveryFailed;
    private final AtomicBoolean deliveryInProgress = new AtomicBoolean();

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

    /** ההפצה אליו נכשלה בכשל זמני/דחייה — אינו חסום, ולכן ננסה שוב לשלוח את מה שחסר. */
    public boolean isDeliveryFailed() {
        return deliveryFailed;
    }

    void setDeliveryFailed(boolean failed) {
        this.deliveryFailed = failed;
    }

    boolean isIntroDelivered() {
        return introDelivered;
    }

    void markIntroDelivered() {
        introDelivered = true;
    }

    int getQuestionsDelivered() {
        return questionsDelivered;
    }

    void markQuestionDelivered() {
        questionsDelivered++;
    }

    /** מונע שתי משימות הפצה במקביל לאותו משתתף (הפצה מקורית וניסיון חוזר). */
    boolean tryBeginDelivery() {
        return deliveryInProgress.compareAndSet(false, true);
    }

    void endDelivery() {
        deliveryInProgress.set(false);
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
