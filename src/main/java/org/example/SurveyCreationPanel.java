package org.example;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * המסך מרכיב ממשק ומפעיל את המנהלים בלבד —
 * התצורה ב-{@link AppConfig}, הרכיבים החוזרים ב-{@link UiFactory},
 * והוולידציה בבנאים של {@link Question} ו-{@link Survey}.
 */
public class SurveyCreationPanel extends JPanel implements CommunityListener {
    private static final String CARD_LIST = "list";
    private static final String CARD_LOADING = "loading";

    private final SurveyManager surveyManager;
    private final CommunityManager communityManager;
    private final ChatGPTService chatGPTService;
    private final Runnable onSurveyStartedCallback;

    private final DefaultListModel<Question> questionsModel = new DefaultListModel<>();
    private final JList<Question> questionsList = new JList<>(questionsModel);
    private final JRadioButton manualRadio = new JRadioButton("יצירה ידנית", true);
    private final JRadioButton chatGptRadio = new JRadioButton("יצירה ע\"י ChatGPT");
    private final JTextField topicField = new JTextField(20);
    private final JButton generateButton = new JButton("✨ צור סקר");
    /** אפשר לוותר על הפנייה ל-ChatGPT בלי לחכות ל-timeout */
    private final JButton cancelGenerateButton = new JButton("✖ בטל יצירה");
    private final JButton addQuestionButton = new JButton("➕ הוסף שאלה");
    private final JButton editQuestionButton = new JButton("✏️ ערוך שאלה");
    private final JButton deleteQuestionButton = new JButton("🗑️ מחק שאלה");
    private final JButton startButton = new JButton("🚀 התחל סקר");
    private final JLabel communityStatusLabel = new JLabel();
    private final JLabel questionsCountLabel = new JLabel();
    /**
     * שדה דחייה חופשי בדקות (0 עד AppConfig.MAX_DELAY_MINUTES) במקום רשימת ערכים
     * קבועה מראש — המשתמש יכול לבחור כל דחייה סבירה, לא רק את חמשת הערכים ש-SurveyDelay הציע.
     */
    private final JSpinner delaySpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, AppConfig.MAX_DELAY_MINUTES, 1));

    private final CardLayout questionsCards = new CardLayout();
    private final JPanel questionsCardHolder = new JPanel(questionsCards);
    private final JLabel loadingTitleLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel loadingElapsedLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar loadingBar = new JProgressBar();
    private Timer elapsedTimer;
    private SwingWorker<List<Question>, Void> generationWorker;
    private boolean generating;
    private int lastCommunitySize;

    public SurveyCreationPanel(SurveyManager surveyManager,
                               CommunityManager communityManager,
                               ChatGPTService chatGPTService,
                               Runnable onSurveyStartedCallback) {
        this.surveyManager = surveyManager;
        this.communityManager = communityManager;
        this.chatGPTService = chatGPTService;
        this.onSurveyStartedCallback = onSurveyStartedCallback;

        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        add(buildModePanel(), BorderLayout.NORTH);
        add(buildQuestionsListPanel(), BorderLayout.CENTER);
        add(buildStartPanel(), BorderLayout.SOUTH);

        questionsModel.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override
            public void intervalAdded(javax.swing.event.ListDataEvent e) {
                onQuestionsChanged();
            }

            @Override
            public void intervalRemoved(javax.swing.event.ListDataEvent e) {
                onQuestionsChanged();
            }

            @Override
            public void contentsChanged(javax.swing.event.ListDataEvent e) {
                onQuestionsChanged();
            }
        });

        onQuestionsChanged();
        updateCommunityStatus(communityManager.getCommunitySize());
    }

    private JPanel buildModePanel() {
        ButtonGroup group = new ButtonGroup();
        group.add(manualRadio);
        group.add(chatGptRadio);
        manualRadio.addActionListener(e -> onQuestionsChanged());
        chatGptRadio.addActionListener(e -> onQuestionsChanged());
        generateButton.addActionListener(e -> onGenerateWithChatGpt());
        addQuestionButton.addActionListener(e -> onAddQuestionManually());
        cancelGenerateButton.addActionListener(e -> onCancelGeneration());
        cancelGenerateButton.setVisible(false);
        cancelGenerateButton.setToolTipText("מפסיק את ההמתנה לשירות ומחזיר את המסך לעריכה ידנית");

        topicField.addActionListener(e -> {
            if (generateButton.isEnabled()) {
                onGenerateWithChatGpt();
            }
        });

        manualRadio.setToolTipText("הזנת השאלות והתשובות בעצמך");
        chatGptRadio.setToolTipText("ChatGPT יציע שאלות לפי הנושא שתזין");
        topicField.setToolTipText("נושא הסקר, לדוגמה: \"טיול שנתי\" — ואז Enter");
        generateButton.setToolTipText("שולח את הנושא ל-ChatGPT ומקבל עד " + Survey.MAX_QUESTIONS + " שאלות");
        addQuestionButton.setToolTipText("הוספת שאלה חדשה עם " + Question.MIN_OPTIONS
                + "-" + Question.MAX_OPTIONS + " אפשרויות תשובה");

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 8));
        panel.setBorder(BorderFactory.createTitledBorder("שיטת יצירת השאלות"));
        panel.add(manualRadio);
        panel.add(chatGptRadio);
        panel.add(Box.createHorizontalStrut(14));
        panel.add(new JLabel("נושא:"));
        panel.add(topicField);
        panel.add(generateButton);
        panel.add(cancelGenerateButton);
        panel.add(addQuestionButton);
        return panel;
    }

    private JPanel buildQuestionsListPanel() {
        questionsList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel((index + 1) + ".  " + value.getText() + "   " + value.getOptions());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        questionsList.addListSelectionListener(e -> updateQuestionButtonsState());

        editQuestionButton.setEnabled(false);
        editQuestionButton.addActionListener(e -> onEditSelectedQuestion());
        editQuestionButton.setToolTipText("עריכת השאלה המסומנת ברשימה");

        deleteQuestionButton.setEnabled(false);
        deleteQuestionButton.addActionListener(e -> onDeleteSelectedQuestion());
        deleteQuestionButton.setToolTipText("מחיקת השאלה המסומנת ברשימה");

        questionsCountLabel.setFont(questionsCountLabel.getFont().deriveFont(Font.PLAIN, UiTheme.FONT_TINY));

        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        actionsRow.add(editQuestionButton);
        actionsRow.add(deleteQuestionButton);
        actionsRow.add(questionsCountLabel);

        questionsCardHolder.add(new JScrollPane(questionsList), CARD_LIST);
        questionsCardHolder.add(buildLoadingCard(), CARD_LOADING);
        questionsCards.show(questionsCardHolder, CARD_LIST);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("שאלות הסקר"));
        wrapper.add(questionsCardHolder, BorderLayout.CENTER);
        wrapper.add(actionsRow, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildLoadingCard() {
        JLabel sparkle = UiFactory.centered(new JLabel("✨", SwingConstants.CENTER));
        sparkle.setFont(sparkle.getFont().deriveFont(Font.PLAIN, UiTheme.FONT_HERO_ICON));

        UiFactory.centered(UiFactory.styled(loadingTitleLabel, Font.BOLD,
                UiTheme.FONT_SUBTITLE, UiTheme.BRAND_DARK_BLUE));
        UiFactory.centered(UiFactory.styled(loadingElapsedLabel, Font.PLAIN,
                UiTheme.FONT_SMALL, UiTheme.MUTED_TEXT));

        loadingBar.setIndeterminate(true);
        loadingBar.setForeground(UiTheme.BRAND_BLUE);
        Dimension barSize = new Dimension(AppConfig.LOADING_BAR_WIDTH, AppConfig.PROGRESS_BAR_HEIGHT);
        loadingBar.setMaximumSize(barSize);
        loadingBar.setPreferredSize(barSize);
        loadingBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(Box.createVerticalGlue());
        column.add(sparkle);
        column.add(Box.createVerticalStrut(10));
        column.add(loadingTitleLabel);
        column.add(Box.createVerticalStrut(16));
        column.add(loadingBar);
        column.add(Box.createVerticalStrut(10));
        column.add(loadingElapsedLabel);
        column.add(Box.createVerticalGlue());

        JPanel card = new JPanel(new BorderLayout());
        card.add(column, BorderLayout.CENTER);
        return card;
    }

    private void showLoadingCard(String topic) {
        generating = true;
        cancelGenerateButton.setVisible(true);
        cancelGenerateButton.setEnabled(true);
        loadingTitleLabel.setText("ChatGPT מנסח שאלות בנושא " + quoted(topic));
        loadingElapsedLabel.setText("שולח בקשה לשרת…");
        loadingBar.setIndeterminate(true);
        questionsCards.show(questionsCardHolder, CARD_LOADING);
        UiTheme.applyRtl(questionsCardHolder);

        long startedAt = System.currentTimeMillis();
        stopElapsedTimer();
        elapsedTimer = new Timer(AppConfig.ELAPSED_TICK_MILLIS, e -> {
            int seconds = (int) ((System.currentTimeMillis() - startedAt) / 1000);
            loadingElapsedLabel.setText(seconds < AppConfig.SLOW_RESPONSE_SECONDS
                    ? "חלפו " + seconds + " שניות…"
                    : "חלפו " + seconds + " שניות — עוד רגע, השרת עדיין עונה…");
        });
        elapsedTimer.start();
    }

    private void hideLoadingCard() {
        generating = false;
        generationWorker = null;
        cancelGenerateButton.setVisible(false);
        stopElapsedTimer();
        loadingBar.setIndeterminate(false);
        questionsCards.show(questionsCardHolder, CARD_LIST);
    }

    private void stopElapsedTimer() {
        if (elapsedTimer != null) {
            elapsedTimer.stop();
            elapsedTimer = null;
        }
    }

    private JPanel buildStartPanel() {
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, UiTheme.FONT_BUTTON));
        startButton.addActionListener(e -> onStartSurvey());
        startButton.setToolTipText("שולח את השאלות לכל חברי הקהילה ופותח את הסקר");
        delaySpinner.setToolTipText("כמה דקות לחכות לפני שהשאלות יישלחו בטלגרם (0 = מיידי, עד "
                + AppConfig.MAX_DELAY_MINUTES + ")");

        communityStatusLabel.setFont(communityStatusLabel.getFont().deriveFont(Font.PLAIN, UiTheme.FONT_SMALL));
        communityStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        UiFactory.centered(communityStatusLabel);

        JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        controlsRow.add(new JLabel("⏱ תזמון (דקות):"));
        controlsRow.add(delaySpinner);
        controlsRow.add(startButton);
        controlsRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("תזמון והפעלה"));
        panel.add(communityStatusLabel);
        panel.add(controlsRow);
        return panel;
    }

    private void onQuestionsChanged() {
        boolean isChatGpt = chatGptRadio.isSelected();
        boolean atMax = questionsModel.size() >= Survey.MAX_QUESTIONS;

        topicField.setEnabled(isChatGpt && !generating);
        generateButton.setEnabled(isChatGpt && !generating);
        addQuestionButton.setEnabled(!isChatGpt && !atMax && !generating);

        questionsCountLabel.setText(generating
                ? "יוצר שאלות…"
                : questionsModel.size() + " / " + Survey.MAX_QUESTIONS + " שאלות");
        questionsCountLabel.setForeground(atMax && !generating ? UiTheme.WARNING_ORANGE : UiTheme.MUTED_TEXT);

        updateQuestionButtonsState();
        refreshStartButton();
    }

    private void updateQuestionButtonsState() {
        boolean hasSelection = questionsList.getSelectedIndex() >= 0 && !generating;
        editQuestionButton.setEnabled(hasSelection);
        deleteQuestionButton.setEnabled(hasSelection);
    }

    private void refreshStartButton() {
        startButton.setEnabled(!generating && lastCommunitySize >= AppConfig.MIN_COMMUNITY_SIZE);
        delaySpinner.setEnabled(!generating);
    }

    private void onEditSelectedQuestion() {
        int index = questionsList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        Question edited = openQuestionDialog(questionsModel.get(index));
        if (edited != null) {
            questionsModel.set(index, edited);
        }
    }

    private void onDeleteSelectedQuestion() {
        int index = questionsList.getSelectedIndex();
        if (index >= 0) {
            questionsModel.remove(index);
        }
    }

    private void onAddQuestionManually() {
        Question question = openQuestionDialog(null);
        if (question != null) {
            questionsModel.addElement(question);
        }
    }

    private Question openQuestionDialog(Question existing) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        Frame ownerFrame = owner instanceof Frame ? (Frame) owner : null;
        AddQuestionDialog dialog = new AddQuestionDialog(ownerFrame, existing);
        return dialog.showDialog();
    }

    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        SwingUtilities.invokeLater(() -> updateCommunityStatus(newCommunitySize));
    }

    private void updateCommunityStatus(int communitySize) {
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
     * ביטול מיידי — ה-SwingWorker מופסק, ובנוסף מבטלים בפועל
     * את חיבור ה-HTTP הפעיל דרך ChatGPTService.cancelCurrentRequest(), כי
     * SwingWorker.cancel(true) בלבד אינו עוצר Socket חוסם (מגבלה ידועה של Thread.interrupt() ב-JDK).
     */
    private void onCancelGeneration() {
        if (generationWorker != null) {
            cancelGenerateButton.setEnabled(false);
            loadingElapsedLabel.setText("מבטל…");
            generationWorker.cancel(true);
            chatGPTService.cancelCurrentRequest();
        }
    }

    private void onGenerateWithChatGpt() {
        String topic = topicField.getText().trim();
        if (topic.isEmpty()) {
            JOptionPane.showMessageDialog(this, "נא להזין נושא לסקר.", "חסר נושא",
                    JOptionPane.WARNING_MESSAGE);
            topicField.requestFocusInWindow();
            return;
        }
        if (!questionsModel.isEmpty() && !confirmReplaceExisting()) {
            return;
        }

        generateButton.setText("⏳ יוצר שאלות…");
        showLoadingCard(topic);
        onQuestionsChanged();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        generationWorker = new SwingWorker<>() {
            @Override
            protected List<Question> doInBackground() throws SurveyGenerationException {
                return chatGPTService.generateSurvey(topic);
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        Toast.show(SurveyCreationPanel.this, "יצירת השאלות בוטלה", Toast.Type.WARNING);
                        return;
                    }
                    applyGeneratedQuestions(get(), topic);
                } catch (CancellationException ex) {
                    Toast.show(SurveyCreationPanel.this, "יצירת השאלות בוטלה", Toast.Type.WARNING);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    // כישלון נשאר חלונית חוסמת — אסור שיתפספס
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(SurveyCreationPanel.this,
                            "יצירת השאלות נכשלה: " + cause.getMessage(), "שגיאה", JOptionPane.ERROR_MESSAGE);
                } finally {
                    generateButton.setText("✨ צור סקר");
                    setCursor(Cursor.getDefaultCursor());
                    hideLoadingCard();
                    onQuestionsChanged();
                }
            }
        };
        generationWorker.execute();
    }

    private void applyGeneratedQuestions(List<Question> generated, String topic) {
        questionsModel.clear();
        for (Question q : generated) {
            if (questionsModel.size() >= Survey.MAX_QUESTIONS) {
                break;
            }
            questionsModel.addElement(q);
        }
        Toast.show(this, "✨ ChatGPT יצר " + questionsModel.size() + " שאלות בנושא " + quoted(topic),
                Toast.Type.SUCCESS);
        if (!questionsModel.isEmpty()) {
            questionsList.setSelectedIndex(0);
        }
    }

    private boolean confirmReplaceExisting() {
        int answer = JOptionPane.showConfirmDialog(this,
                "יצירה באמצעות ChatGPT תחליף את " + questionsModel.size()
                        + " השאלות שכבר ברשימה.\nלהמשיך?",
                "החלפת השאלות הקיימות",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    /** מקצר נושא ארוך כדי שהבועה תישאר בשורה אחת */
    private String quoted(String topic) {
        String trimmed = topic.length() > AppConfig.TOPIC_DISPLAY_MAX_CHARS
                ? topic.substring(0, AppConfig.TOPIC_DISPLAY_MAX_CHARS) + "…"
                : topic;
        return "\"" + trimmed + "\"";
    }

    private void onStartSurvey() {
        Integer delayMinutes = readValidatedDelayMinutes();
        if (delayMinutes == null) {
            return;
        }
        if (questionsModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "הוסף לפחות שאלה אחת!", "שגיאה",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!confirmStart(delayMinutes)) {
            return;
        }
        try {
            List<Question> questions = new ArrayList<>();
            for (int i = 0; i < questionsModel.size(); i++) {
                questions.add(questionsModel.getElementAt(i));
            }
            surveyManager.createSurvey(questions, delayMinutes);
            onSurveyStartedCallback.run();
            questionsModel.clear();
            Toast.show(this, delayMinutes <= 0
                            ? "🚀 הסקר נשלח לקהילה!"
                            : "🚀 הסקר נקבע — יישלח בעוד " + MessageTemplates.formatDuration(delayMinutes * AppConfig.SECONDS_PER_MINUTE),
                    Toast.Type.SUCCESS);
        } catch (IllegalStateException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "שגיאה", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * דרישה 4: עיכוב של מספר דקות שיוזן בממשק. הטקסט עצמו נבדק (ולא ערך ה-JSpinner),
     * כי ה-JSpinner קוטע בשקט "3.5" ל-3. מותר רק מספר שלם בין 0 ל-MAX_DELAY_MINUTES.
     */
    private Integer readValidatedDelayMinutes() {
        String raw = ((JSpinner.DefaultEditor) delaySpinner.getEditor()).getTextField().getText().trim();
        if (!raw.matches("\\d{1,3}") || Integer.parseInt(raw) > AppConfig.MAX_DELAY_MINUTES) {
            JOptionPane.showMessageDialog(this,
                    "זמן הדחייה חייב להיות מספר שלם של דקות בין 0 ל-" + AppConfig.MAX_DELAY_MINUTES
                            + " (0 = שליחה מיידית).",
                    "ערך לא תקין", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        int delayMinutes = Integer.parseInt(raw);
        delaySpinner.setValue(delayMinutes);
        return delayMinutes;
    }

    private boolean confirmStart(int delayMinutes) {
        String timing = delayMinutes <= 0
                ? "מיידית"
                : "בעוד " + MessageTemplates.formatDuration(delayMinutes * AppConfig.SECONDS_PER_MINUTE);
        int durationMinutes = AppConfig.SURVEY_DURATION_SECONDS / AppConfig.SECONDS_PER_MINUTE;
        String message = "לפתוח את הסקר?\n\n"
                + "❓ שאלות: " + questionsModel.size() + "\n"
                + "👥 חברי קהילה שיקבלו את הסקר: " + communityManager.getCommunitySize() + "\n"
                + "⏱ שליחה: " + timing + "\n"
                + "🕔 זמן מענה: " + durationMinutes + " דקות\n\n"
                + "לא ניתן לערוך את השאלות אחרי הפתיחה.";
        int answer = JOptionPane.showConfirmDialog(this, message, "אישור פתיחת סקר",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }
}