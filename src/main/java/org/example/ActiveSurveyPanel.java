package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
public class ActiveSurveyPanel extends JPanel implements SurveyListener
{
    private static final Color RED = new Color(255, 205, 205);
    private static final Color YELLOW = new Color(255, 245, 180);
    private static final Color GREEN = new Color(205, 255, 205);
    private final JLabel countdownLabel = new JLabel("--:--", SwingConstants.CENTER);
    private final JLabel totalLabel = new JLabel("סה\"כ משתתפים: 0");
    private final JLabel finishedLabel = new JLabel("סיימו: 0");
    private final JLabel pendingLabel = new JLabel("טרם סיימו: 0");
    private final DefaultTableModel tableModel;
    private final JTable table;

    private int totalQuestions;
    private java.util.List<SurveyParticipant> currentParticipants;
    private final Map<Long, Integer> rowByTelegramId = new HashMap<>();
    public ActiveSurveyPanel()
    {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        countdownLabel.setFont(countdownLabel.getFont().deriveFont(Font.BOLD, 36f));
        add(countdownLabel, BorderLayout.NORTH);

        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        for (JLabel label : new JLabel[]{totalLabel, finishedLabel, pendingLabel})
        {
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setBorder(BorderFactory.createLineBorder(Color.GRAY));
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
        table.setRowHeight(26);
        table.setDefaultRenderer(Object.class, new StatusRowRenderer());
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.add(statsPanel, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }
    @Override
    public void onCountdownTick(int secondsRemaining, boolean isPendingPhase) {
        SwingUtilities.invokeLater(() -> {
            String mmss = String.format("%02d:%02d", secondsRemaining / 60, secondsRemaining % 60);
            countdownLabel.setText(isPendingPhase
                    ? "הסקר יישלח בעוד: " + mmss
                    : "זמן לסיום הסקר: " + mmss);
        });
    }
    @Override
    public void onSurveyStarted(Survey survey, java.util.List<SurveyParticipant> participants) {
        this.totalQuestions = survey.getQuestions().size();
        this.currentParticipants = participants;
        SwingUtilities.invokeLater(() -> {
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
        totalLabel.setText("סה\"כ משתתפים: " + currentParticipants.size());
        finishedLabel.setText("סיימו: " + finished);
        pendingLabel.setText("טרם סיימו: " + (currentParticipants.size() - finished));
    }

    private static class StatusRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Object status = table.getModel().getValueAt(row, 2);
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