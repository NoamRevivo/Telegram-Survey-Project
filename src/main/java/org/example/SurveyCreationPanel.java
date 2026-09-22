package org.example;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SurveyCreationPanel extends JPanel implements CommunityListener {

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
    private final JButton addQuestionButton = new JButton("➕ הוסף שאלה");
    private final JButton editQuestionButton = new JButton("✏️ ערוך שאלה");
    private final JButton deleteQuestionButton = new JButton("🗑️ מחק שאלה");
    private final JButton startButton = new JButton("🚀 התחל סקר");
    private final JLabel communityStatusLabel = new JLabel();
    private final JLabel questionsCountLabel = new JLabel();
    /** M-09: הדקות לפי אותו סדר של delayCombo — לא מפענחים מספרים מתוך הטקסט */
    private static final int[] DELAY_MINUTES = {0, 1, 2, 5, 10};
    private final JComboBox<String> delayCombo =
            new JComboBox<>(new String[]{"מיידי", "1 דקה", "2 דקות", "5 דקות", "10 דקות"});

    /** מסך טעינה שתופס את מקום רשימת השאלות בזמן שChatGPT עובד */
    private static final String CARD_LIST = "list";
    private static final String CARD_LOADING = "loading";
    private final CardLayout questionsCards = new CardLayout();
    private final JPanel questionsCardHolder = new JPanel(questionsCards);
    private final JLabel loadingTitleLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel loadingElapsedLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar loadingBar = new JProgressBar();
    private Timer elapsedTimer;
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

        questionsModel.addListDataListener(new ListDataListener() {
            @Override public void intervalAdded(ListDataEvent e) { onQuestionsChanged(); }
            @Override public void intervalRemoved(ListDataEvent e) { onQuestionsChanged(); }
            @Override public void contentsChanged(ListDataEvent e) { onQuestionsChanged(); }
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
        // Enter בשדה הנושא מפעיל יצירה — בלי להוריד יד מהמקלדת
        topicField.addActionListener(e -> {
            if (generateButton.isEnabled()) {
                onGenerateWithChatGpt();
            }
        });

        // הסברים קצרים על כל פקד
        manualRadio.setToolTipText("הזנת השאלות והתשובות בעצמך");
        chatGptRadio.setToolTipText("ChatGPT יציע שאלות לפי הנושא שתזין");
        topicField.setToolTipText("נושא הסקר, לדוגמה: \"טיול שנתי\" — ואז Enter");
        generateButton.setToolTipText("שולח את הנושא ל-ChatGPT ומקבל עד "
                + Survey.MAX_QUESTIONS + " שאלות");
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

        questionsCountLabel.setFont(questionsCountLabel.getFont().deriveFont(Font.PLAIN, 12f));

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

    /**
     * חיווי טעינה שאי אפשר לפספס.
     * שינוי הטקסט בכפתור לבדו נבלע — כאן כל אזור הרשימה מוחלף.
     */
    private JPanel buildLoadingCard() {
        JLabel sparkle = new JLabel("✨", SwingConstants.CENTER);
        sparkle.setFont(sparkle.getFont().deriveFont(Font.PLAIN, 40f));
        sparkle.setAlignmentX(Component.CENTER_ALIGNMENT);

        loadingTitleLabel.setFont(loadingTitleLabel.getFont().deriveFont(Font.BOLD, 17f));
        loadingTitleLabel.setForeground(UiTheme.BRAND_DARK_BLUE);
        loadingTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        loadingBar.setIndeterminate(true);
        loadingBar.setForeground(UiTheme.BRAND_BLUE);
        loadingBar.setMaximumSize(new Dimension(320, 14));
        loadingBar.setPreferredSize(new Dimension(320, 14));
        loadingBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        loadingElapsedLabel.setFont(loadingElapsedLabel.getFont().deriveFont(Font.PLAIN, 13f));
        loadingElapsedLabel.setForeground(UiTheme.MUTED_TEXT);
        loadingElapsedLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

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
        loadingTitleLabel.setText("ChatGPT מנסח שאלות בנושא " + quoted(topic));
        loadingElapsedLabel.setText("שולח בקשה לשרת…");
        loadingBar.setIndeterminate(true);
        questionsCards.show(questionsCardHolder, CARD_LOADING);
        UiTheme.applyRtl(questionsCardHolder);

        long startedAt = System.currentTimeMillis();
        stopElapsedTimer();
        elapsedTimer = new Timer(1000, e -> {
            int seconds = (int) ((System.currentTimeMillis() - startedAt) / 1000);
            loadingElapsedLabel.setText(seconds < 12
                    ? "חלפו " + seconds + " שניות…"
                    : "חלפו " + seconds + " שניות — עוד רגע, השרת עדיין עונה…");
        });
        elapsedTimer.start();
    }

    private void hideLoadingCard() {
        generating = false;
        stopElapsedTimer();
        loadingBar.setIndeterminate(false);   // עוצר את האנימציה כדי לא לבזבז מחזורי ציור
        questionsCards.show(questionsCardHolder, CARD_LIST);
    }

    private void stopElapsedTimer() {
        if (elapsedTimer != null) {
            elapsedTimer.stop();
            elapsedTimer = null;
        }
    }

    private JPanel buildStartPanel() {
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, 15f));
        startButton.addActionListener(e -> onStartSurvey());
        startButton.setToolTipText("שולח את השאלות לכל חברי הקהילה ופותח את הסקר");
        delayCombo.setToolTipText("כמה זמן לחכות לפני שהשאלות יישלחו בטלגרם");

        communityStatusLabel.setFont(communityStatusLabel.getFont().deriveFont(Font.PLAIN, 13f));
        communityStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        communityStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        controlsRow.add(new JLabel("⏱ תזמון:"));
        controlsRow.add(delayCombo);
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

        // בזמן יצירה כל הפקדים נעולים — אין טעם לערוך רשימה שעומדת להידרס
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
        startButton.setEnabled(!generating && lastCommunitySize >= SurveyManager.MIN_COMMUNITY_SIZE);
        delayCombo.setEnabled(!generating);
    }

    private void onEditSelectedQuestion() {
        int index = questionsList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        AddQuestionDialog dialog = new AddQuestionDialog((Frame) owner, questionsModel.get(index));
        Question edited = dialog.showDialog();
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

    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        SwingUtilities.invokeLater(() -> updateCommunityStatus(newCommunitySize));
    }

    private void updateCommunityStatus(int communitySize) {
        lastCommunitySize = communitySize;
        boolean canStart = communitySize >= SurveyManager.MIN_COMMUNITY_SIZE;
        refreshStartButton();
        if (canStart) {
            communityStatusLabel.setText("✅  ניתן להתחיל סקר — " + communitySize + " חברים בקהילה");
            communityStatusLabel.setForeground(UiTheme.SUCCESS_GREEN);
        } else {
            int missing = SurveyManager.MIN_COMMUNITY_SIZE - communitySize;
            communityStatusLabel.setText("⚠️  נדרשים עוד " + missing + " חברים כדי להתחיל סקר (יש " + communitySize + ")");
            communityStatusLabel.setForeground(UiTheme.WARNING_ORANGE);
        }
    }

    private void onAddQuestionManually() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        AddQuestionDialog dialog = new AddQuestionDialog((Frame) owner);
        Question question = dialog.showDialog();
        if (question != null) {
            questionsModel.addElement(question);
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
        // היצירה דורסת את הרשימה הקיימת — שואלים לפני שמוחקים עבודה של המשתמש
        if (!questionsModel.isEmpty()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "יצירה באמצעות ChatGPT תחליף את " + questionsModel.size()
                            + " השאלות שכבר ברשימה.\nלהמשיך?",
                    "החלפת השאלות הקיימות",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        generateButton.setText("⏳ יוצר שאלות…");
        showLoadingCard(topic);   // החיווי הראשי — מיד עם הלחיצה
        onQuestionsChanged();     // נועל את שאר הפקדים
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<java.util.List<Question>, Void>() {
            @Override
            protected java.util.List<Question> doInBackground() throws Exception {
                return chatGPTService.generateSurvey(topic);
            }
            @Override
            protected void done() {
                try {
                    java.util.List<Question> generated = get();
                    questionsModel.clear();
                    for (Question q : generated) {
                        if (questionsModel.size() >= Survey.MAX_QUESTIONS) {
                            break;
                        }
                        questionsModel.addElement(q);
                    }
                    // הצלחה מוצגת כבועה שלא חוסמת את המשתמש
                    Toast.show(SurveyCreationPanel.this,
                            "✨ ChatGPT יצר " + questionsModel.size() + " שאלות בנושא " + quoted(topic),
                            Toast.Type.SUCCESS);
                    if (!questionsModel.isEmpty()) {
                        questionsList.setSelectedIndex(0);
                    }
                } catch (Exception ex) {
                    // כישלון נשאר חלונית חוסמת — אסור שתתפספס
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
        }.execute();
    }

    /** מקצר נושא ארוך כדי שהבועה תישאר בשורה אחת */
    private String quoted(String topic) {
        String trimmed = topic.length() > 40 ? topic.substring(0, 40) + "…" : topic;
        return "\"" + trimmed + "\"";
    }

    private void onStartSurvey() {
        if (questionsModel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "הוסף לפחות שאלה אחת!",
                    "שגיאה",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int delayMinutes = DELAY_MINUTES[delayCombo.getSelectedIndex()];
        // אישור עם סיכום — פתיחת סקר היא פעולה שלא ניתן לבטל
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
            // הצלחה כבועה — הדיאלוג החוסם קטע בדיוק את המעבר ללשונית הסקר
            Toast.show(this,
                    delayMinutes == 0
                            ? "🚀 הסקר נשלח לקהילה!"
                            : "🚀 הסקר נקבע — יישלח בעוד " + delayCombo.getSelectedItem(),
                    Toast.Type.SUCCESS);
        }
        catch (IllegalStateException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this,
                    e.getMessage(),
                    "שגיאה",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean confirmStart(int delayMinutes) {
        String timing = delayMinutes == 0 ? "מיידית" : "בעוד " + delayCombo.getSelectedItem();
        int durationMinutes = SurveyManager.SURVEY_DURATION_SECONDS / 60;
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