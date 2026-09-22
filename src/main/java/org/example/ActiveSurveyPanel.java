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
    private static final String CARD_IDLE = "idle";
    private static final String CARD_LIVE = "live";

    private static final String STATUS_WAITING = "טרם ענה";
    private static final String STATUS_IN_PROGRESS = "בתהליך";
    private static final String STATUS_COMPLETED = "השלים";
    private static final String STATUS_MISSED = "לא השלים";

    private final SurveyManager surveyManager;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHolder = new JPanel(cards);

    private final JLabel countdownLabel = new JLabel("--:--", SwingConstants.CENTER);
    private final JProgressBar countdownBar = new JProgressBar();
    private final JLabel phaseNoteLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton stopButton = new JButton("⏹ סיים סקר עכשיו");

    private final JLabel totalLabel = new JLabel("👥 סה\"כ משתתפים: 0");
    private final JLabel finishedLabel = new JLabel("✅ סיימו: 0");
    private final JLabel pendingLabel = new JLabel("⏳ טרם סיימו: 0");
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Map<Long, Integer> rowByTelegramId = new HashMap<>();

    private int totalQuestions;
    private java.util.List<SurveyParticipant> currentParticipants;

    /** בסיס פס ההתקדמות נקבע לפי השלב הנוכחי (המתנה מול סקר פעיל) */
    private int phaseMaxSeconds = 1;
    private boolean lastPhaseWasPending;
    private boolean phaseInitialized;
    private boolean blinkOn;

    public ActiveSurveyPanel(SurveyManager surveyManager)
    {
        this.surveyManager = surveyManager;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

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

        cardHolder.add(buildIdleCard(), CARD_IDLE);
        cardHolder.add(buildLiveCard(), CARD_LIVE);
        add(cardHolder, BorderLayout.CENTER);
        cards.show(cardHolder, CARD_IDLE);
    }

    /** מצב ריק מנחה במקום "--:--" וטבלה ריקה */
    private JPanel buildIdleCard() {
        JLabel icon = new JLabel(AppIcons.active(48), SwingConstants.CENTER);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("אין סקר פעיל כרגע", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(UiTheme.BRAND_DARK_BLUE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("עברו ללשונית «יצירת סקר» כדי לבנות שאלות ולשלוח אותן לקהילה.",
                SwingConstants.CENTER);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 14f));
        hint.setForeground(UiTheme.MUTED_TEXT);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(Box.createVerticalGlue());
        column.add(icon);
        column.add(Box.createVerticalStrut(14));
        column.add(title);
        column.add(Box.createVerticalStrut(8));
        column.add(hint);
        column.add(Box.createVerticalGlue());

        JPanel card = new JPanel(new BorderLayout());
        card.add(column, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildLiveCard() {
        countdownLabel.setFont(countdownLabel.getFont().deriveFont(Font.BOLD, 38f));
        countdownLabel.setForeground(UiTheme.BRAND_DARK_BLUE);

        // פס התקדמות שמתרוקן — קריא הרבה יותר ממספר בודד
        countdownBar.setMinimum(0);
        countdownBar.setMaximum(1);
        countdownBar.setValue(0);
        countdownBar.setForeground(UiTheme.SUCCESS_GREEN);
        countdownBar.setPreferredSize(new Dimension(10, 14));

        phaseNoteLabel.setFont(phaseNoteLabel.getFont().deriveFont(Font.PLAIN, 13f));
        phaseNoteLabel.setForeground(UiTheme.MUTED_TEXT);

        JPanel countdownPanel = new JPanel();
        countdownPanel.setLayout(new BoxLayout(countdownPanel, BoxLayout.Y_AXIS));
        countdownPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        countdownBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        phaseNoteLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        countdownPanel.add(countdownLabel);
        countdownPanel.add(Box.createVerticalStrut(8));
        countdownPanel.add(countdownBar);
        countdownPanel.add(Box.createVerticalStrut(6));
        countdownPanel.add(phaseNoteLabel);

        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 12, 10));
        for (JLabel label : new JLabel[]{totalLabel, finishedLabel, pendingLabel})
        {
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.PANEL_BORDER, 1, true),
                    BorderFactory.createEmptyBorder(10, 6, 10, 6)));
            statsPanel.add(label);
        }

        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setBorder(BorderFactory.createTitledBorder("משתתפי הסקר"));
        tableWrapper.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.add(statsPanel, BorderLayout.NORTH);
        centerPanel.add(tableWrapper, BorderLayout.CENTER);

        // אפשרות לסגור סקר לפני תום הזמן — עד עכשיו המנהל היה נעול ל-5 דקות
        stopButton.setToolTipText("סוגר את הסקר מיד ומציג את התוצאות שנאספו עד כה");
        stopButton.setForeground(UiTheme.ERROR_RED);
        stopButton.addActionListener(e -> onStopSurvey());
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        actionsRow.add(stopButton);

        JPanel card = new JPanel(new BorderLayout());
        card.add(countdownPanel, BorderLayout.NORTH);
        card.add(centerPanel, BorderLayout.CENTER);
        card.add(actionsRow, BorderLayout.SOUTH);
        return card;
    }

    private void onStopSurvey() {
        int answer = JOptionPane.showConfirmDialog(this,
                "לסגור את הסקר עכשיו?\nהתשובות שנאספו עד כה יישמרו ויוצגו בלשונית «תוצאות».",
                "סיום סקר",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            stopButton.setEnabled(false);
            surveyManager.closeSurvey();
        }
    }

    @Override
    public void onCountdownTick(int secondsRemaining, boolean isPendingPhase) {
        SwingUtilities.invokeLater(() -> {
            cards.show(cardHolder, CARD_LIVE);
            stopButton.setEnabled(true);
            updatePhaseBase(secondsRemaining, isPendingPhase);

            String mmss = String.format("%02d:%02d", secondsRemaining / 60, secondsRemaining % 60);
            boolean lastSeconds = !isPendingPhase
                    && secondsRemaining <= SurveyManager.FINAL_WARNING_SECONDS_BEFORE_END;

            countdownLabel.setText(isPendingPhase
                    ? "⏳ הסקר יישלח בעוד: " + mmss
                    : (lastSeconds ? "⚠ " : "⏱ ") + "זמן לסיום הסקר: " + mmss);

            if (lastSeconds) {
                // הבהוב עדין בשניות האחרונות — הטיק הוא פעם בשנייה
                blinkOn = !blinkOn;
                countdownLabel.setForeground(blinkOn ? UiTheme.ERROR_RED : new Color(0xFF7A70));
            } else {
                countdownLabel.setForeground(UiTheme.BRAND_DARK_BLUE);
            }

            countdownBar.setValue(Math.min(secondsRemaining, phaseMaxSeconds));
            countdownBar.setForeground(barColorFor(secondsRemaining, isPendingPhase));

            phaseNoteLabel.setText(isPendingPhase
                    ? "הסקר ממתין לשליחה — עדיין אפשר לבטל אותו"
                    : "🔴 הסקר פעיל — התשובות נקלטות בזמן אמת");
        });
    }

    private void updatePhaseBase(int secondsRemaining, boolean isPendingPhase) {
        if (!phaseInitialized || isPendingPhase != lastPhaseWasPending || secondsRemaining > phaseMaxSeconds) {
            phaseMaxSeconds = Math.max(1, secondsRemaining);
            lastPhaseWasPending = isPendingPhase;
            phaseInitialized = true;
            countdownBar.setMaximum(phaseMaxSeconds);
        }
    }

    private Color barColorFor(int secondsRemaining, boolean isPendingPhase) {
        if (isPendingPhase) {
            return UiTheme.BRAND_BLUE;
        }
        float ratio = secondsRemaining / (float) phaseMaxSeconds;
        if (ratio > 0.5f) {
            return UiTheme.SUCCESS_GREEN;
        }
        return ratio > 0.2f ? UiTheme.WARNING_ORANGE : UiTheme.ERROR_RED;
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
                tableModel.addRow(new Object[]{p.getUser().toString(), "0/" + totalQuestions, STATUS_WAITING});
                rowByTelegramId.put(p.getUser().getTelegramId(), row++);
            }
            stopButton.setEnabled(true);
            cards.show(cardHolder, CARD_LIVE);
            refreshStats();
            UiTheme.applyRtl(this);   // תוכן שנבנה עכשיו חייב יישור מחדש
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
        SwingUtilities.invokeLater(() -> {
            countdownLabel.setText("🏁 הסקר הסתיים");
            countdownLabel.setForeground(UiTheme.BRAND_DARK_BLUE);
            countdownBar.setValue(0);
            phaseNoteLabel.setText("התוצאות המלאות מוצגות בלשונית «תוצאות»");
            stopButton.setEnabled(false);
            phaseInitialized = false;

            // רק עכשיו הצבע האדום נכון — מי שלא סיים באמת פספס את הסקר
            for (SurveyParticipant p : participants) {
                Integer row = rowByTelegramId.get(p.getUser().getTelegramId());
                if (row != null && !p.isCompleted()) {
                    tableModel.setValueAt(STATUS_MISSED, row, 2);
                }
            }
            table.repaint();
            refreshStats();
        });
    }

    private String statusLabelFor(SurveyParticipant participant) {
        if (participant.isCompleted()) {
            return STATUS_COMPLETED;
        }
        return participant.getAnsweredQuestionsCount() > 0 ? STATUS_IN_PROGRESS : STATUS_WAITING;
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
            if (STATUS_COMPLETED.equals(status)) {
                c.setBackground(UiTheme.ROW_COMPLETED);
            } else if (STATUS_IN_PROGRESS.equals(status)) {
                c.setBackground(UiTheme.ROW_IN_PROGRESS);
            } else if (STATUS_MISSED.equals(status)) {
                c.setBackground(UiTheme.ROW_MISSED);
            } else {
                // המתנה היא מצב ניטרלי, לא שגיאה — אין סיבה לצבוע אותה באדום
                c.setBackground(UiTheme.ROW_WAITING);
            }
            if (isSelected) {
                c.setBackground(c.getBackground().darker());
            }
            return c;
        }
    }
}