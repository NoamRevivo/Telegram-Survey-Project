package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * מצב הסקר בלבד — הסקר, משתתפיו והשאילתות עליהם.
 * אין כאן תזמון ואין מאזינים; כל הגישה נעשית תחת המנעול של SurveyManager.
 */
final class SurveyState {
    private final Survey survey;
    private final List<SurveyParticipant> participants = new CopyOnWriteArrayList<>();
    /** מזהה דור — טיק של סקר קודם מזוהה ונזרק */
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

    /** המשתתפים נקבעים ברגע שהסקר יוצא בפועל. */
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

    /** כולם סיימו — משתתף שלא ניתן להשיג אינו נספר, כי לא יכול לסיים. */
    boolean allCompleted() {
        for (SurveyParticipant p : participants) {
            if (!p.isUnreachable() && p.getStatus() != ParticipantStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    boolean anyCompleted() {
        for (SurveyParticipant p : participants) {
            if (p.getStatus() == ParticipantStatus.COMPLETED) {
                return true;
            }
        }
        return false;
    }

    List<SurveyParticipant> notCompleted() {
        List<SurveyParticipant> pending = new ArrayList<>();
        for (SurveyParticipant p : participants) {
            if (!p.isUnreachable() && p.getStatus() != ParticipantStatus.COMPLETED) {
                pending.add(p);
            }
        }
        return pending;
    }

    /** התצלום שנמסר למאזינים — מחוץ למנעול הם עובדים על עותק. */
    List<SurveyParticipant> snapshot() {
        return new ArrayList<>(participants);
    }

    /** תזכורת יחידה בלבד לכל סקר — מונע כפל תזכורות לאותו משתתף. */
    boolean markRemindersSent() {
        if (remindersSent) {
            return false;
        }
        remindersSent = true;
        return true;
    }

    /** השעון מופעל פעם אחת בלבד, אחרי שההפצה הסתיימה. */
    boolean markTimersStarted() {
        if (timersStarted) {
            return false;
        }
        timersStarted = true;
        return true;
    }
}