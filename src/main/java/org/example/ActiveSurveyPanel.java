package org.example;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveSurveyPanel extends JPanel implements SurveyListener {
    private static final String CARD_IDLE = "idle";
    private static final String CARD_LIVE = "live";

    private static final String STATUS_WAITING = "טרם ענה";
    private static final String STATUS_IN_PROGRESS = "בתהליך";
    private static final String STATUS_COMPLETED = "השלים";
    private static final String STATUS_MISSED = "לא השלים";
    private static final String STATUS_UNREACHABLE = "לא נמסר";

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
    private final JLabel unreachableLabel = new JLabel("🚫 לא נמסר: 0");
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Map<Long, Integer> rowByTelegramId = new HashMap<>();

    private int totalQuestions;
    private List<SurveyParticipant> currentParticipants;

    private String closedSurveyId;
    private boolean pendingPhase;

    private int phaseMaxSeconds = 1;
    private boolean lastPhaseWasPending;
    private boolean phaseInitialized;
    private boolean blinkOn;

    public ActiveSurveyPanel(SurveyManager surveyManager) {
        this.surveyManager = surveyManager;

        setLayout(new BorderLayout());
        setBorder(UiFactory.pagePadding());

        tableModel = UiFactory.readOnlyModel(new Object[]{"שם", "התקדמות", "סטטוס"});
        table = UiFactory.readOnlyTable(tableModel);
        table.setDefaultRenderer(Object.class, new StatusRowRenderer());

        cardHolder.add(UiFactory.emptyState(AppIcons.active(UiTheme.ICON_EMPTY_STATE), "אין סקר פעיל כרגע",
                "עברו ללשונית «יצירת סקר» כדי לבנות שאלות ולשלוח אותן לקהילה."), CARD_IDLE);
        cardHolder.add(buildLiveCard(), CARD_LIVE);
        add(cardHolder, BorderLayout.CENTER);
        cards.show(cardHolder, CARD_IDLE);
    }

    private JPanel buildLiveCard() {
        UiFactory.styled(countdownLabel, Font.BOLD, UiTheme.FONT_COUNTDOWN, UiTheme.BRAND_DARK_BLUE);

        countdownBar.setMinimum(0);
        countdownBar.setMaximum(1);
        countdownBar.setValue(0);
        countdownBar.setForeground(UiTheme.SUCCESS_GREEN);
        countdownBar.setPreferredSize(new Dimension(10, AppConfig.PROGRESS_BAR_HEIGHT));

        UiFactory.styled(phaseNoteLabel, Font.PLAIN, UiTheme.FONT_SMALL, UiTheme.MUTED_TEXT);

        JPanel countdownPanel = new JPanel();
        countdownPanel.setLayout(new BoxLayout(countdownPanel, BoxLayout.Y_AXIS));
        countdownPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        countdownPanel.add(UiFactory.centered(countdownLabel));
        countdownPanel.add(Box.createVerticalStrut(8));
        countdownPanel.add(UiFactory.centered(countdownBar));
        countdownPanel.add(Box.createVerticalStrut(6));
        countdownPanel.add(UiFactory.centered(phaseNoteLabel));

        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 12, UiTheme.GAP));
        for (JLabel label : new JLabel[]{totalLabel, finishedLabel, pendingLabel, unreachableLabel}) {
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD, UiTheme.FONT_BODY));
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.PANEL_BORDER, 1, true),
                    BorderFactory.createEmptyBorder(10, 6, 10, 6)));
            statsPanel.add(label);
        }

        JPanel centerPanel = new JPanel(new BorderLayout(UiTheme.GAP, UiTheme.GAP));
        centerPanel.add(statsPanel, BorderLayout.NORTH);
        centerPanel.add(UiFactory.titledScroll("משתתפי הסקר", table), BorderLayout.CENTER);

        stopButton.setToolTipText("סוגר את הסקר מיד ומציג את התוצאות שנאספו עד כה");
        stopButton.setForeground(UiTheme.ERROR_RED);
        stopButton.addActionListener(e -> onStopSurvey());

        JPanel card = new JPanel(new BorderLayout());
        card.add(countdownPanel, BorderLayout.NORTH);
        card.add(centerPanel, BorderLayout.CENTER);
        card.add(UiFactory.actionsRow(stopButton), BorderLayout.SOUTH);
        return card;
    }

    private void onStopSurvey() {
        boolean cancelBeforeSending = pendingPhase;
        String message = cancelBeforeSending
                ? "לבטל את הסקר לפני השליחה?\nהשאלות לא יישלחו לאף אחד ולא ייווצרו תוצאות."
                : "לסגור את הסקר עכשיו?\nהתשובות שנאספו עד כה יישמרו ויוצגו בלשונית «תוצאות».";
        String title = cancelBeforeSending ? "ביטול סקר" : "סיום סקר";
        if (!Dialogs.confirm(this, title, message)) {
            return;
        }
        stopButton.setEnabled(false);
        if (cancelBeforeSending) {
            surveyManager.cancelPendingSurvey();
        } else {
            surveyManager.closeSurvey();
        }
    }

    @Override
    public void onCountdownTick(String surveyId, int secondsRemaining, boolean isPendingPhase) {
        SwingUtilities.invokeLater(() -> {
            if (surveyId.equals(closedSurveyId)) {
                return;
            }
            pendingPhase = isPendingPhase;
            cards.show(cardHolder, CARD_LIVE);
            stopButton.setEnabled(true);
            stopButton.setText(isPendingPhase ? "⏹ בטל את הסקר" : "⏹ סיים סקר עכשיו");
            updatePhaseBase(secondsRemaining, isPendingPhase);

            String mmss = String.format("%02d:%02d", secondsRemaining / 60, secondsRemaining % 60);
            boolean lastSeconds = !isPendingPhase
                    && secondsRemaining <= AppConfig.URGENT_SECONDS_BEFORE_END;

            countdownLabel.setText(isPendingPhase
                    ? "⏳ הסקר יישלח בעוד: " + mmss
                    : (lastSeconds ? "⚠ " : "⏱ ") + "זמן לסיום הסקר: " + mmss);

            if (lastSeconds) {
                blinkOn = !blinkOn;
                countdownLabel.setForeground(blinkOn ? UiTheme.ERROR_RED : UiTheme.ERROR_RED_SOFT);
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
        if (ratio > AppConfig.BAR_WARN_RATIO) {
            return UiTheme.SUCCESS_GREEN;
        }
        return ratio > AppConfig.BAR_DANGER_RATIO ? UiTheme.WARNING_ORANGE : UiTheme.ERROR_RED;
    }

    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> {
            this.closedSurveyId = null;
            this.pendingPhase = false;
            this.totalQuestions = survey.getQuestions().size();
            this.currentParticipants = participants;
            tableModel.setRowCount(0);
            rowByTelegramId.clear();
            int row = 0;
            for (SurveyParticipant p : participants) {
                tableModel.addRow(new Object[]{
                        p.getUser().toString(), "0/" + totalQuestions, STATUS_WAITING});
                rowByTelegramId.put(p.getUser().getTelegramId(), row++);
            }
            stopButton.setEnabled(true);
            stopButton.setText("⏹ סיים סקר עכשיו");
            countdownLabel.setText("📤 שולח את השאלות…");
            countdownLabel.setForeground(UiTheme.BRAND_DARK_BLUE);
            phaseNoteLabel.setText("השאלות נשלחות למשתתפים — הספירה תתחיל כשכולם יקבלו אותן");
            cards.show(cardHolder, CARD_LIVE);
            refreshStats();
            UiTheme.applyRtl(this);
        });
    }

    @Override
    public void onAnswerRecorded(SurveyParticipant participant) {
        SwingUtilities.invokeLater(() -> {
            Integer row = rowByTelegramId.get(participant.getUser().getTelegramId());
            if (row != null) {
                tableModel.setValueAt(
                        participant.getAnsweredQuestionsCount() + "/" + totalQuestions, row, 1);
                tableModel.setValueAt(statusLabelFor(participant), row, 2);
            }
            refreshStats();
        });
    }

    @Override
    public void onParticipantUnreachable(SurveyParticipant participant) {
        SwingUtilities.invokeLater(() -> {
            Integer row = rowByTelegramId.get(participant.getUser().getTelegramId());
            if (row != null && !participant.isCompleted()) {
                tableModel.setValueAt(STATUS_UNREACHABLE, row, 2);
            }
            refreshStats();
            Toast.show(this, "⚠ ההודעות לא הגיעו אל " + participant.getUser().getFirstName(),
                    Toast.Type.WARNING);
        });
    }

    @Override
    public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> {
            closedSurveyId = survey.getId();
            pendingPhase = false;
            countdownLabel.setText("🏁 הסקר הסתיים");
            countdownLabel.setForeground(UiTheme.BRAND_DARK_BLUE);
            countdownBar.setValue(0);
            phaseNoteLabel.setText("התוצאות המלאות מוצגות בלשונית «תוצאות»");
            stopButton.setEnabled(false);
            phaseInitialized = false;

            for (SurveyParticipant p : participants) {
                Integer row = rowByTelegramId.get(p.getUser().getTelegramId());
                if (row != null && !p.isCompleted() && !p.isUnreachable()) {
                    tableModel.setValueAt(STATUS_MISSED, row, 2);
                }
            }
            table.repaint();
            refreshStats();
        });
    }

    @Override
    public void onSurveyCancelled(Survey survey) {
        SwingUtilities.invokeLater(() -> {
            closedSurveyId = survey.getId();
            pendingPhase = false;
            phaseInitialized = false;
            currentParticipants = null;
            tableModel.setRowCount(0);
            rowByTelegramId.clear();
            stopButton.setEnabled(false);
            cards.show(cardHolder, CARD_IDLE);
        });
    }

    private String statusLabelFor(SurveyParticipant participant) {
        if (participant.isCompleted()) {
            return STATUS_COMPLETED;
        }
        if (participant.isUnreachable()) {
            return STATUS_UNREACHABLE;
        }
        return participant.getAnsweredQuestionsCount() > 0 ? STATUS_IN_PROGRESS : STATUS_WAITING;
    }

    private void refreshStats() {
        if (currentParticipants == null) {
            return;
        }
        long finished = currentParticipants.stream().filter(SurveyParticipant::isCompleted).count();
        long unreachable = currentParticipants.stream()
                .filter(p -> p.isUnreachable() && !p.isCompleted()).count();
        totalLabel.setText("👥 סה\"כ משתתפים: " + currentParticipants.size());
        finishedLabel.setText("✅ סיימו: " + finished);
        pendingLabel.setText("⏳ טרם סיימו: " + (currentParticipants.size() - finished - unreachable));
        unreachableLabel.setText("🚫 לא נמסר: " + unreachable);
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
            } else if (STATUS_MISSED.equals(status) || STATUS_UNREACHABLE.equals(status)) {
                c.setBackground(UiTheme.ROW_MISSED);
            } else {
                c.setBackground(UiTheme.ROW_WAITING);
            }
            if (isSelected) {
                c.setBackground(c.getBackground().darker());
            }
            return c;
        }
    }
}