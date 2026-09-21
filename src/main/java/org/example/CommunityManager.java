package org.example;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CommunityManager
{
    private static final Logger LOG = Logger.getLogger(CommunityManager.class.getName());
    private final Map<Long, CommunityUser> members = new ConcurrentHashMap<>();
    private final List<CommunityListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(CommunityListener listener)
    {
        listeners.add(listener);
    }
    public void removeListener(CommunityListener listener)
    {
        listeners.remove(listener);
    }

    public synchronized boolean addMember(long telegramId, String firstName, String username)
    {
        CommunityUser user = new CommunityUser(telegramId, firstName, username);
        if (members.putIfAbsent(telegramId, user) != null)
        {
            return false;
        }
        notifyMemberAdded(user, members.size());
        return true;
    }

    private void notifyMemberAdded(CommunityUser user, int size)
    {
        for (CommunityListener listener : listeners)
        {
            try
            {
                listener.onMemberAdded(user, size);
            }
            catch (RuntimeException e)
            {
                LOG.log(Level.WARNING, "מאזין קהילה נכשל", e);
            }
        }
    }

    public List<CommunityUser> getAllMembers()
    {
        return List.copyOf(members.values());
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