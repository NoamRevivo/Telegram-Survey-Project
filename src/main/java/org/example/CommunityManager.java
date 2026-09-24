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

    public void removeListener(CommunityListener listener) {
        listeners.remove(listener);
    }

    public boolean addMember(long telegramId, String firstName, String username) {
        CommunityUser user = new CommunityUser(telegramId, firstName, username);
        int size;
        synchronized (this) {
            if (members.putIfAbsent(telegramId, user) != null) {
                return false;
            }
            size = members.size();
        }
        listeners.fire(l -> l.onMemberAdded(user, size));
        return true;
    }


    public List<CommunityUser> getAllMembers() {
        List<CommunityUser> sorted = new ArrayList<>(members.values());
        sorted.sort(Comparator.comparingLong(CommunityUser::getJoinSequence));
        return List.copyOf(sorted);
    }

    public int getCommunitySize() {
        return members.size();
    }

    public CommunityUser getMember(long telegramId) {
        return members.get(telegramId);
    }
}