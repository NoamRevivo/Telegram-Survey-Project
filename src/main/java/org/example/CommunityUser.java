package org.example;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class CommunityUser {
    private static final String NO_NAME = "חבר/ה";
    private static final String NO_USERNAME = "ללא שם משתמש בטלגרם";
    private static final AtomicLong JOIN_SEQUENCE = new AtomicLong();

    private final long telegramId;
    private final String firstName;
    private final String username;
    private final LocalDateTime joinedAt;
    private final long joinSequence = JOIN_SEQUENCE.incrementAndGet();

    public CommunityUser(long telegramId, String firstName, String username) {
        this.telegramId = telegramId;
        this.firstName = (firstName == null || firstName.isBlank()) ? NO_NAME : firstName.trim();
        this.username = (username == null || username.isBlank()) ? null : username.trim();
        this.joinedAt = LocalDateTime.now();
    }

    public long getTelegramId() {
        return telegramId;
    }

    public String getFirstName() {
        return firstName;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    long getJoinSequence() {
        return joinSequence;
    }

    public String getUsernameDisplay() {
        return username == null ? NO_USERNAME : "@" + username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CommunityUser)) {
            return false;
        }
        CommunityUser that = (CommunityUser) o;
        return telegramId == that.telegramId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(telegramId);
    }

    @Override
    public String toString() {
        return firstName + " (" + getUsernameDisplay() + ")";
    }
}