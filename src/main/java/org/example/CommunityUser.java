package org.example;
import java.time.LocalDateTime;
import java.util.Objects;

public class CommunityUser {
    private final long telegramId;
    private final String firstName;
    private final String username;
    private final LocalDateTime joinedAt;

    public CommunityUser(long telegramId, String firstName, String username) {
        this.telegramId = telegramId;
        this.firstName = firstName;
        this.username = username;
        this.joinedAt = LocalDateTime.now();
    }
    public long getTelegramId() {
        return telegramId;
    }
    public String getFirstName() {
        return firstName;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
    public String getUsernameDisplay() {
    if (username == null || username.isEmpty()) {
        return "ללא שם משתמש בטלגרם";
    }
    return "@" + username;
}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommunityUser)) return false;
        CommunityUser that = (CommunityUser) o;
        return telegramId == that.telegramId;
    }
    @Override
    public int hashCode() {
        return Objects.hash(telegramId);
    }
    @Override
    public String toString() {
        return firstName + " (@" + getUsernameDisplay() + ")";
    }
}