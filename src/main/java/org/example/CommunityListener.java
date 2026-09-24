package org.example;

/** מאזין לשינויים בקהילה. */
public interface CommunityListener {
    void onMemberAdded(CommunityUser newUser, int newCommunitySize);
}
