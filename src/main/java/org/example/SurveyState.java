package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * R5-M08: מצב הסקר בלבד — הסקר, משתתפיו והשאילתות עליהם.
 * אין כאן תזמון ואין מאזינים; כל הגישה נעשית תחת המנעול של SurveyManager.
 */
final class SurveyState {

    private final Survey survey;
    private final List<SurveyParticipant> participants = new CopyOnWriteArrayList<>();
    /** R5-C01: מזהה דור — טיק של סקר קודם מזוהה ונזרק */
    private final long generation;
    private boolean remindersSent;
    private boolean timersStarted;

    SurveyState(Survey survey, long generation) {
        this.survey = survey;
        this.generation = generation;
    }

    Survey survey() {
        return survey;
    }

    long generation() {
        return generation;
    }

    SurveyStatus status() {
        return survey.getStatus();
    }

    void status(SurveyStatus status) {
        survey.setStatus(status);
    }

    /** R5-C02 / דרישה 5: המשתתפים נקבעים ברגע שהסקר יוצא בפועל. */
    void seed(List<CommunityUser> members) {
        participants.clear();
        for (CommunityUser user : members) {
            participants.add(new SurveyParticipant(user));
        }
    }

    SurveyParticipant find(long telegramId) {
        for (SurveyParticipant p : participants) {
            if (p.getUser().getTelegramId() == telegramId) {
                return p;
            }
        }
        return null;
    }

    boolean allCompleted() {
        for (SurveyParticipant p : participants) {
            if (p.getStatus() != ParticipantStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    List<SurveyParticipant> notCompleted() {
        List<SurveyParticipant> pending = new ArrayList<>();
        for (SurveyParticipant p : participants) {
            if (p.getStatus() != ParticipantStatus.COMPLETED) {
                pending.add(p);
            }
        }
        return pending;
    }

    /** R5-M01: התצלום שנמסר למאזינים — מחוץ למנעול הם עובדים על עותק. */
    List<SurveyParticipant> snapshot() {
        return new ArrayList<>(participants);
    }

    /** R6-C01: תזכורת יחידה בלבד לכל סקר — מונע כפל תזכורות לאותו משתתף. */
    boolean markRemindersSent() {
        if (remindersSent) {
            return false;
        }
        remindersSent = true;
        return true;
    }

    /** R5-M15: השעון מופעל פעם אחת בלבד, אחרי שההפצה הסתיימה. */
    boolean markTimersStarted() {
        if (timersStarted) {
            return false;
        }
        timersStarted = true;
        return true;
    }
}