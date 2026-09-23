package org.example;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

public class CommunityPanel extends JPanel implements CommunityListener {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JLabel totalMembersLabel;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Set<Long> displayedIds = new HashSet<>();
    private final Color defaultSelectionBackground = UIManager.getColor("Table.selectionBackground");
    private final Timer highlightTimer;

    public CommunityPanel(CommunityManager communityManager) {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        totalMembersLabel = UiFactory.styled(
                new JLabel("👥  סה\"כ חברים בקהילה: 0", SwingConstants.CENTER),
                Font.BOLD, UiTheme.FONT_TITLE, UiTheme.BRAND_DARK_BLUE);
        totalMembersLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        add(totalMembersLabel, BorderLayout.NORTH);

        tableModel = UiFactory.readOnlyModel(new Object[]{"שם", "שם משתמש בטלגרם", "שעת הצטרפות"});
        table = UiFactory.readOnlyTable(tableModel);
        table.setDefaultRenderer(Object.class, new StripedRowRenderer());
        add(UiFactory.titledScroll("חברי הקהילה", table), BorderLayout.CENTER);

        highlightTimer = createHighlightTimer();

        for (CommunityUser user : communityManager.getAllMembers()) {
            addRow(user);
        }
        updateTotalLabel(communityManager.getCommunitySize());
    }

    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        SwingUtilities.invokeLater(() -> {
            boolean added = addRow(newUser);
            updateTotalLabel(newCommunitySize);
            if (added) {
                highlightNewRow();
                Toast.show(this, MessageTemplates.joinToast(newUser), Toast.Type.INFO);
            }
        });
    }

    private boolean addRow(CommunityUser user) {
        if (!displayedIds.add(user.getTelegramId())) {
            return false;
        }
        tableModel.insertRow(0, new Object[]{
                user.getFirstName(),
                user.getUsernameDisplay(),
                user.getJoinedAt().format(TIME_FORMAT)
        });
        return true;
    }

    private void updateTotalLabel(int communitySize) {
        totalMembersLabel.setText("👥  סה\"כ חברים בקהילה: " + communitySize);
    }

    private Timer createHighlightTimer() {
        Timer timer = new Timer(AppConfig.HIGHLIGHT_MILLIS, e -> {
            table.clearSelection();
            table.setSelectionBackground(defaultSelectionBackground);
        });
        timer.setRepeats(false);
        return timer;
    }

    private void highlightNewRow() {
        table.setSelectionBackground(UiTheme.HIGHLIGHT_GREEN);
        table.setRowSelectionInterval(0, 0);
        highlightTimer.restart();
    }

    private static class StripedRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? Color.WHITE : UiTheme.TABLE_STRIPE);
            }
            return c;
        }
    }
}