package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;



final class SurveyState {
    private final Survey survey;
    private final List<SurveyParticipant> participants = new CopyOnWriteArrayList<>();
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

    void seed(List<CommunityUser> members) {
        List<SurveyParticipant> seeded = new ArrayList<>(members.size());
        for (CommunityUser user : members) {
            seeded.add(new SurveyParticipant(user));
        }
        participants.clear();
        participants.addAll(seeded);
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

    List<SurveyParticipant> snapshot() {
        return new ArrayList<>(participants);
    }

    boolean markRemindersSent() {
        if (remindersSent) {
            return false;
        }
        remindersSent = true;
        return true;
    }

    boolean markTimersStarted() {
        if (timersStarted) {
            return false;
        }
        timersStarted = true;
        return true;
    }
}