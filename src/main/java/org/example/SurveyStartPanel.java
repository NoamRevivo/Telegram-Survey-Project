package org.example;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * תזמון והפעלה: זמן דחייה, מצב הקהילה וכפתור «התחל סקר».
 * המסך אינו מכיר את עריכת השאלות — הוא מקבל אותן מ-{@code questions} ומדווח ל-{@code onStarted} כשהסקר נפתח.
 * כל המתודות רצות על ה-EDT; ההעברה אליו נעשית ב-{@link EdtSurveyListener} וב-{@link EdtCommunityListener}.
 */
public class SurveyStartPanel extends JPanel implements CommunityListener, SurveyListener {
    private static final Logger LOG = Logger.getLogger(SurveyStartPanel.class.getName());
    private static final String START_TOOLTIP = "שולח את השאלות לכל חברי הקהילה ופותח את הסקר";
    private static final String BUSY_TOOLTIP = "יש סקר פעיל או ממתין — אפשר להתחיל סקר חדש רק אחרי שיסתיים";

    private final SurveyManager surveyManager;
    private final CommunityManager communityManager;
    private final Supplier<List<Question>> questions;
    private final Runnable onStarted;

    private final JButton startButton = new JButton("🚀 התחל סקר");
    private final JLabel communityStatusLabel = new JLabel();
    private final JSpinner delaySpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, AppConfig.MAX_DELAY_MINUTES, 1));
    private boolean blocked;
    private int lastCommunitySize;

    public SurveyStartPanel(SurveyManager surveyManager,
                            CommunityManager communityManager,
                            Supplier<List<Question>> questions,
                            Runnable onStarted) {
        this.surveyManager = surveyManager;
        this.communityManager = communityManager;
        this.questions = questions;
        this.onStarted = onStarted;

        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, UiTheme.FONT_BUTTON));
        startButton.addActionListener(e -> onStartSurvey());
        startButton.setToolTipText(START_TOOLTIP);
        delaySpinner.setToolTipText("כמה דקות לחכות לפני שהשאלות יישלחו בטלגרם (0 = מיידי, עד "
                + AppConfig.MAX_DELAY_MINUTES + ")");

        communityStatusLabel.setFont(communityStatusLabel.getFont().deriveFont(Font.PLAIN, UiTheme.FONT_SMALL));
        communityStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        UiFactory.centered(communityStatusLabel);

        JPanel controlsRow = UiFactory.actionsRow(new JLabel("⏱ תזמון (דקות):"), delaySpinner, startButton);
        controlsRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder("תזמון והפעלה"));
        add(communityStatusLabel);
        add(controlsRow);

        updateCommunityStatus();
    }

    /** המסך נעול בזמן שהשאלות עדיין נוצרות — אי אפשר לפתוח סקר עם רשימה שעומדת להיות מוחלפת. */
    void setBlocked(boolean blocked) {
        this.blocked = blocked;
        refreshStartButton();
    }

    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        updateCommunityStatus();
    }

    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        refreshStartButton();
    }

    @Override
    public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
        refreshStartButton();
    }

    @Override
    public void onSurveyCancelled(Survey survey) {
        refreshStartButton();
    }

    private void updateCommunityStatus() {
        int communitySize = communityManager.getCommunitySize();
        lastCommunitySize = communitySize;
        refreshStartButton();
        if (communitySize >= AppConfig.MIN_COMMUNITY_SIZE) {
            communityStatusLabel.setText("✅  ניתן להתחיל סקר — " + communitySize + " חברים בקהילה");
            communityStatusLabel.setForeground(UiTheme.SUCCESS_GREEN);
        } else {
            int missing = AppConfig.MIN_COMMUNITY_SIZE - communitySize;
            communityStatusLabel.setText("⚠️  נדרשים עוד " + missing
                    + " חברים כדי להתחיל סקר (יש " + communitySize + ")");
            communityStatusLabel.setForeground(UiTheme.WARNING_ORANGE);
        }
    }

    /**
     * הכפתור פעיל רק כשאין סקר פעיל וגם אין סקר ממתין (PENDING) —
     * אחרת הלחיצה נכשלת רק אחרי חלון האישור, עם «סקר פעיל כבר קיים».
     */
    private void refreshStartButton() {
        boolean busy = surveyManager.isSurveyInProgress();
        boolean ready = !blocked && !busy && lastCommunitySize >= AppConfig.MIN_COMMUNITY_SIZE;
        startButton.setEnabled(ready);
        startButton.setToolTipText(busy ? BUSY_TOOLTIP : START_TOOLTIP);
        delaySpinner.setEnabled(!blocked && !busy);
    }

    private void onStartSurvey() {
        Integer delayMinutes = readValidatedDelayMinutes();
        if (delayMinutes == null) {
            return;
        }
        List<Question> chosen = questions.get();
        if (chosen.isEmpty()) {
            Dialogs.warn(this, "חסרות שאלות", "הוסף לפחות שאלה אחת!");
            return;
        }
        if (!confirmStart(delayMinutes, chosen.size())) {
            return;
        }
        try {
            surveyManager.createSurvey(chosen, delayMinutes);
            refreshStartButton();
            onStarted.run();
            Toast.show(this, delayMinutes <= 0
                            ? "🚀 הסקר נשלח לקהילה!"
                            : "🚀 הסקר נקבע — יישלח בעוד "
                            + MessageTemplates.formatDuration(delayMinutes * AppConfig.SECONDS_PER_MINUTE),
                    Toast.Type.SUCCESS);
        } catch (IllegalStateException | IllegalArgumentException e) {
            Dialogs.error(this, e.getMessage());
        } catch (RuntimeException e) {
            // חריגה לא צפויה לא נבלעת ב-EDT בלי שהמנהל יודע שהסקר לא יצא
            LOG.log(Level.SEVERE, "יצירת הסקר נכשלה", e);
            Dialogs.error(this, "יצירת הסקר נכשלה בשגיאה לא צפויה. פרטים ביומן.");
        }
    }

    /**
     * הטקסט עצמו נבדק (ולא ערך ה-JSpinner), כי ה-JSpinner קוטע בשקט "3.5" ל-3.
     * מותר רק מספר שלם בין 0 ל-MAX_DELAY_MINUTES.
     */
    private Integer readValidatedDelayMinutes() {
        String raw = ((JSpinner.DefaultEditor) delaySpinner.getEditor()).getTextField().getText().trim();
        if (!raw.matches("\\d{1,3}") || Integer.parseInt(raw) > AppConfig.MAX_DELAY_MINUTES) {
            Dialogs.warn(this, "ערך לא תקין",
                    "זמן הדחייה חייב להיות מספר שלם של דקות בין 0 ל-" + AppConfig.MAX_DELAY_MINUTES
                            + " (0 = שליחה מיידית).");
            return null;
        }
        int delayMinutes = Integer.parseInt(raw);
        delaySpinner.setValue(delayMinutes);
        return delayMinutes;
    }

    private boolean confirmStart(int delayMinutes, int questionCount) {
        String timing = delayMinutes <= 0
                ? "מיידית"
                : "בעוד " + MessageTemplates.formatDuration(delayMinutes * AppConfig.SECONDS_PER_MINUTE);
        int durationMinutes = AppConfig.SURVEY_DURATION_SECONDS / AppConfig.SECONDS_PER_MINUTE;
        String message = "לפתוח את הסקר?\n\n"
                + "❓ שאלות: " + questionCount + "\n"
                + "👥 חברי קהילה שיקבלו את הסקר: " + communityManager.getCommunitySize() + "\n"
                + "⏱ שליחה: " + timing + "\n"
                + "🕔 זמן מענה: " + durationMinutes + " דקות\n\n"
                + "לא ניתן לערוך את השאלות אחרי הפתיחה.";
        return Dialogs.confirm(this, "אישור פתיחת סקר", message);
    }
}
