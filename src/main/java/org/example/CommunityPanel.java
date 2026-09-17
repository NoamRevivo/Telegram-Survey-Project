package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;

public class CommunityPanel extends JPanel implements CommunityListener {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color STRIPE_COLOR = new Color(0xF3F6FA);

    private final JLabel totalMembersLabel;
    private final DefaultTableModel tableModel;
    private final JTable table;

    public CommunityPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        totalMembersLabel = new JLabel("👥  סה\"כ חברים בקהילה: 0", SwingConstants.CENTER);
        totalMembersLabel.setFont(totalMembersLabel.getFont().deriveFont(Font.BOLD, 20f));
        totalMembersLabel.setForeground(UiTheme.BRAND_DARK_BLUE);
        totalMembersLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        add(totalMembersLabel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new Object[]{"שם", "שם משתמש בטלגרם", "שעת הצטרפות"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(table.getFont().deriveFont(14f));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 14f));
        table.setDefaultRenderer(Object.class, new StripedRowRenderer());

        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setBorder(BorderFactory.createTitledBorder("חברי הקהילה"));
        tableWrapper.add(new JScrollPane(table), BorderLayout.CENTER);
        add(tableWrapper, BorderLayout.CENTER);
    }

    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        SwingUtilities.invokeLater(() -> {
            tableModel.insertRow(0, new Object[]{
                    newUser.getFirstName(),
                    newUser.getUsernameDisplay(),
                    newUser.getJoinedAt().format(TIME_FORMAT)
            });
            totalMembersLabel.setText("👥  סה\"כ חברים בקהילה: " + newCommunitySize);
            highlightNewRow();
            showJoinToast(newUser);
        });
    }

    private void highlightNewRow() {
        table.setRowSelectionInterval(0, 0);
        table.setSelectionBackground(new Color(200, 255, 200));
        Timer timer = new Timer(1500, e -> table.clearSelection());
        timer.setRepeats(false);
        timer.start();
    }

    private void showJoinToast(CommunityUser newUser) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner != null) {
            new MemberJoinToast(owner, "🎉 " + newUser.getFirstName() + " הצטרף/ה לקהילה!").showAnimated();
        }
    }

    private static class StripedRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? Color.WHITE : STRIPE_COLOR);
            }
            return c;
        }
    }
}