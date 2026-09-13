package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CommunityManager {

    private final Map<Long, CommunityUser> members = new ConcurrentHashMap<>();
    private final List<CommunityListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(CommunityListener listener) {
        listeners.add(listener);
    }

    public boolean addMember(long telegramId, String firstName, String username) {
        if (members.containsKey(telegramId)) {
            return false;
        }
        CommunityUser user = new CommunityUser(telegramId, firstName, username);
        CommunityUser previous = members.putIfAbsent(telegramId, user);
        if (previous != null) {
            return false;
        }
        notifyMemberAdded(user);
        return true;
    }
    private void notifyMemberAdded(CommunityUser user)
    {
        int size = getCommunitySize();
        for (CommunityListener listener : listeners) {
            listener.onMemberAdded(user, size);
        }
    }

    public List<CommunityUser> getAllMembers()
    {
        return Collections.unmodifiableList(new ArrayList<>(members.values()));
    }
    public int getCommunitySize()
    {
        return members.size();
    }
    public CommunityUser getMember(long telegramId)
    {
        return members.get(telegramId);
    }
}
