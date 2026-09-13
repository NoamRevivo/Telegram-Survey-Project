package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;

public class CommunityPanel extends JPanel implements CommunityListener {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JLabel totalMembersLabel;
    private final DefaultTableModel tableModel;
    private final JTable table;

    public CommunityPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        totalMembersLabel = new JLabel("סה\"כ חברים בקהילה: 0", SwingConstants.CENTER);
        totalMembersLabel.setFont(totalMembersLabel.getFont().deriveFont(Font.BOLD, 18f));
        add(totalMembersLabel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new Object[]{"שם", "יוזרניים", "שעת הצטרפות"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(26);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        // האירוע מגיע מ-thread של הבוט / הרשת - חובה לעדכן Swing רק דרך ה-EDT.
        SwingUtilities.invokeLater(() -> {
            tableModel.insertRow(0, new Object[]{
                    newUser.getFirstName(),
                    "@" + newUser.getUsername(),
                    newUser.getJoinedAt().format(TIME_FORMAT)
            });
            totalMembersLabel.setText("סה\"כ חברים בקהילה: " + newCommunitySize);
            highlightNewRow();
        });
    }

    private void highlightNewRow() {
        table.setRowSelectionInterval(0, 0);
        table.setSelectionBackground(new Color(200, 255, 200));
        Timer timer = new Timer(1500, e -> table.clearSelection());
        timer.setRepeats(false);
        timer.start();
    }
}
