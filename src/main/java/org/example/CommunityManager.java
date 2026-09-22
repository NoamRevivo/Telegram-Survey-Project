package org.example;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommunityManager {

    private final Map<Long, CommunityUser> members = new ConcurrentHashMap<>();
    private final Listeners<CommunityListener> listeners = new Listeners<>();

    public void addListener(CommunityListener listener) {
        listeners.add(listener);
    }

    /** R5-M16: נקרא מ-MainFrame.dispose() — חלון שנסגר מפסיק לקבל אירועים. */
    public void removeListener(CommunityListener listener) {
        listeners.remove(listener);
    }

    /** R5-M01: ההודעה למאזינים יוצאת מחוץ למנעול (copy-then-notify). */
    public boolean addMember(long telegramId, String firstName, String username) {
        CommunityUser user = new CommunityUser(telegramId, firstName, username);
        CommunityUser existing = members.putIfAbsent(telegramId, user);
        if (existing != null) {
            return false;
        }
        int size = members.size();
        listeners.fire(l -> l.onMemberAdded(user, size));
        return true;
    }

    /**
     * R5-M17: סדר הצטרפות יציב — ConcurrentHashMap.values() מחזיר סדר hash שרירותי,
     * וכך אותה קהילה הוצגה בשני סדרים שונים בשתי לשוניות.
     */
    public List<CommunityUser> getAllMembers() {
        List<CommunityUser> sorted = new ArrayList<>(members.values());
        sorted.sort(Comparator.comparing(CommunityUser::getJoinedAt)
                .thenComparingLong(CommunityUser::getTelegramId));
        return List.copyOf(sorted);
    }

    public int getCommunitySize() {
        return members.size();
    }

    public CommunityUser getMember(long telegramId) {
        return members.get(telegramId);
    }
}