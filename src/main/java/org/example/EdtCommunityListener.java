package org.example;

import javax.swing.SwingUtilities;

/** מעביר אירועי קהילה ל-EDT — ראו {@link EdtSurveyListener}. */
final class EdtCommunityListener implements CommunityListener {
    private final CommunityListener delegate;

    private EdtCommunityListener(CommunityListener delegate) {
        this.delegate = delegate;
    }

    static CommunityListener wrap(CommunityListener delegate) {
        return new EdtCommunityListener(delegate);
    }

    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        SwingUtilities.invokeLater(() -> delegate.onMemberAdded(newUser, newCommunitySize));
    }
}
