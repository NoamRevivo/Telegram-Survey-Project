package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveSurveyPanel extends JPanel implements SurveyListener
{
    private static final Color RED = new Color(255, 205, 205);
    private static final Color YELLOW = new Color(255, 245, 180);
    private static final Color GREEN = new Color(205, 255, 205);
    private final JLabel countdownLabel = new JLabel("--:--", SwingConstants.CENTER);
    private final JLabel totalLabel = new JLabel("👥 סה\"כ משתתפים: 0");
    private final JLabel finishedLabel = new JLabel("✅ סיימו: 0");
    private final JLabel pendingLabel = new JLabel("⏳ טרם סיימו: 0");
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Map<Long, Integer> rowByTelegramId = new HashMap<>();

    private int totalQuestions;
    private java.util.List<SurveyParticipant> currentParticipants;

    public ActiveSurveyPanel()
    {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        countdownLabel.setFont(countdownLabel.getFont().deriveFont(Font.BOLD, 38f));
        countdownLabel.setForeground(UiTheme.BRAND_DARK_BLUE);
        countdownLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        add(countdownLabel, BorderLayout.NORTH);

        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 12, 10));
        for (JLabel label : new JLabel[]{totalLabel, finishedLabel, pendingLabel})
        {
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0xD0D7E2), 1, true),
                    BorderFactory.createEmptyBorder(10, 6, 10, 6)));
            statsPanel.add(label);
        }
        tableModel = new DefaultTableModel(new Object[]{"שם", "התקדמות", "סטטוס"}, 0)
        {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(table.getFont().deriveFont(14f));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 14f));
        table.setDefaultRenderer(Object.class, new StatusRowRenderer());

        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setBorder(BorderFactory.createTitledBorder("משתתפי הסקר"));
        tableWrapper.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.add(statsPanel, BorderLayout.NORTH);
        centerPanel.add(tableWrapper, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    @Override
    public void onCountdownTick(int secondsRemaining, boolean isPendingPhase) {
        SwingUtilities.invokeLater(() -> {
            String mmss = String.format("%02d:%02d", secondsRemaining / 60, secondsRemaining % 60);
            countdownLabel.setText(isPendingPhase
                    ? "⏳ הסקר יישלח בעוד: " + mmss
                    : "⏱ זמן לסיום הסקר: " + mmss);
            countdownLabel.setForeground(!isPendingPhase && secondsRemaining <= 30
                    ? UiTheme.ERROR_RED : UiTheme.BRAND_DARK_BLUE);
        });
    }

    @Override
    public void onSurveyStarted(Survey survey, java.util.List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> {
            this.totalQuestions = survey.getQuestions().size();
            this.currentParticipants = participants;
            tableModel.setRowCount(0);
            rowByTelegramId.clear();
            int row = 0;
            for (SurveyParticipant p : participants) {
                tableModel.addRow(new Object[]{p.getUser().toString(), "0/" + totalQuestions, "טרם ענה"});
                rowByTelegramId.put(p.getUser().getTelegramId(), row++);
            }
            refreshStats();
        });
    }

    @Override
    public void onAnswerRecorded(SurveyParticipant participant) {
        SwingUtilities.invokeLater(() -> {
            Integer row = rowByTelegramId.get(participant.getUser().getTelegramId());
            if (row != null) {
                tableModel.setValueAt(participant.getAnsweredQuestionsCount() + "/" + totalQuestions, row, 1);
                tableModel.setValueAt(statusLabelFor(participant), row, 2);
            }
            refreshStats();
        });
    }

    @Override
    public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> countdownLabel.setText("הסקר הסתיים"));
    }

    private String statusLabelFor(SurveyParticipant participant) {
        if (participant.isCompleted()) {
            return "השלים";
        }
        return participant.getAnsweredQuestionsCount() > 0 ? "בתהליך" : "טרם ענה";
    }

    private void refreshStats() {
        if (currentParticipants == null) {
            return;
        }
        long finished = currentParticipants.stream().filter(SurveyParticipant::isCompleted).count();
        totalLabel.setText("👥 סה\"כ משתתפים: " + currentParticipants.size());
        finishedLabel.setText("✅ סיימו: " + finished);
        pendingLabel.setText("⏳ טרם סיימו: " + (currentParticipants.size() - finished));
    }

    private static class StatusRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Object status = table.getModel().getValueAt(table.convertRowIndexToModel(row), 2);
            if ("השלים".equals(status)) {
                c.setBackground(GREEN);
            } else if ("בתהליך".equals(status)) {
                c.setBackground(YELLOW);
            } else {
                c.setBackground(RED);
            }
            if (isSelected) {
                c.setBackground(c.getBackground().darker());
            }
            return c;
        }
    }
}