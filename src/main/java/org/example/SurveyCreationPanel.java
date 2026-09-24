package org.example;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.util.Collections;
import java.util.List;


public class SurveyCreationPanel extends JPanel {
    private static final String CARD_LIST = "list";
    private static final String CARD_LOADING = "loading";
    private static final String GENERATE_LABEL = "✨ צור סקר";
    private static final String GENERATING_LABEL = "⏳ יוצר שאלות…";

    private final QuestionGenerationController generationController;
    private final SurveyStartPanel startPanel;

    private final DefaultListModel<Question> questionsModel = new DefaultListModel<>();
    private final JList<Question> questionsList = new JList<>(questionsModel);
    private final JRadioButton manualRadio = new JRadioButton("יצירה ידנית", true);
    private final JRadioButton chatGptRadio = new JRadioButton("יצירה ע\"י ChatGPT");
    private final JTextField topicField = new JTextField(20);
    private final JButton generateButton = new JButton(GENERATE_LABEL);
    private final JButton cancelGenerateButton = new JButton("✖ בטל יצירה");
    private final JButton addQuestionButton = new JButton("➕ הוסף שאלה");
    private final JButton editQuestionButton = new JButton("✏️ ערוך שאלה");
    private final JButton deleteQuestionButton = new JButton("🗑️ מחק שאלה");
    private final JLabel questionsCountLabel = new JLabel();

    private final CardLayout questionsCards = new CardLayout();
    private final JPanel questionsCardHolder = new JPanel(questionsCards);
    private final GenerationLoadingCard loadingCard = new GenerationLoadingCard();
    private String generationTopic = "";
    private boolean generating;

    public SurveyCreationPanel(SurveyManager surveyManager,
                               CommunityManager communityManager,
                               ChatGPTService chatGPTService,
                               Runnable onSurveyStartedCallback) {
        this.startPanel = new SurveyStartPanel(surveyManager, communityManager, this::currentQuestions, () -> {
            questionsModel.clear();
            onSurveyStartedCallback.run();
        });
        this.generationController = new QuestionGenerationController(chatGPTService, new GenerationHandler());

        setLayout(new BorderLayout(UiTheme.GAP, UiTheme.GAP));
        setBorder(UiFactory.pagePadding());

        add(buildModePanel(), BorderLayout.NORTH);
        add(buildQuestionsListPanel(), BorderLayout.CENTER);
        add(startPanel, BorderLayout.SOUTH);

        questionsModel.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                onQuestionsChanged();
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                onQuestionsChanged();
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                onQuestionsChanged();
            }
        });

        onQuestionsChanged();
    }

    public SurveyStartPanel startPanel() {
        return startPanel;
    }

    private List<Question> currentQuestions() {
        return Collections.list(questionsModel.elements());
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

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, UiTheme.GAP, 8));
        panel.setBorder(BorderFactory.createTitledBorder("שיטת יצירת השאלות"));
        panel.add(manualRadio);
        panel.add(chatGptRadio);
        panel.add(Box.createHorizontalStrut(UiTheme.PAGE_PAD));
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

        questionsCardHolder.add(new JScrollPane(questionsList), CARD_LIST);
        questionsCardHolder.add(loadingCard, CARD_LOADING);
        questionsCards.show(questionsCardHolder, CARD_LIST);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("שאלות הסקר"));
        wrapper.add(questionsCardHolder, BorderLayout.CENTER);
        wrapper.add(UiFactory.actionsRow(editQuestionButton, deleteQuestionButton, questionsCountLabel),
                BorderLayout.SOUTH);
        return wrapper;
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
        startPanel.setBlocked(generating);
    }

    private void updateQuestionButtonsState() {
        boolean hasSelection = questionsList.getSelectedIndex() >= 0 && !generating;
        editQuestionButton.setEnabled(hasSelection);
        deleteQuestionButton.setEnabled(hasSelection);
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

    private void onCancelGeneration() {
        if (generating) {
            cancelGenerateButton.setEnabled(false);
            loadingCard.showCancelling();
            generationController.cancel();
        }
    }

    private void onGenerateWithChatGpt() {
        String topic = topicField.getText().trim();
        if (topic.isEmpty()) {
            Dialogs.warn(this, "חסר נושא", "נא להזין נושא לסקר.");
            topicField.requestFocusInWindow();
            return;
        }
        if (!questionsModel.isEmpty() && !confirmReplaceExisting()) {
            return;
        }
        generationController.start(topic);
    }

    private void applyGeneratedQuestions(GeneratedSurvey generated) {
        questionsModel.clear();
        for (Question q : generated.questions()) {
            if (questionsModel.size() >= Survey.MAX_QUESTIONS) {
                break;
            }
            questionsModel.addElement(q);
        }
        Toast.show(this, "✨ ChatGPT יצר " + questionsModel.size() + " שאלות בנושא " + quoted(generationTopic)
                + skippedSuffix(generated.skipped()), Toast.Type.SUCCESS);
        if (!questionsModel.isEmpty()) {
            questionsList.setSelectedIndex(0);
        }
    }

    private String skippedSuffix(int skipped) {
        if (skipped <= 0) {
            return "";
        }
        return skipped == 1 ? " (שאלה אחת נפסלה)" : " (" + skipped + " שאלות נפסלו)";
    }

    private void finishGeneration() {
        generating = false;
        cancelGenerateButton.setVisible(false);
        loadingCard.end();
        questionsCards.show(questionsCardHolder, CARD_LIST);
        generateButton.setText(GENERATE_LABEL);
        setCursor(Cursor.getDefaultCursor());
        onQuestionsChanged();
    }

    private boolean confirmReplaceExisting() {
        return Dialogs.confirmWarning(this, "החלפת השאלות הקיימות",
                "יצירה באמצעות ChatGPT תחליף את " + questionsModel.size()
                        + " השאלות שכבר ברשימה.\nלהמשיך?");
    }

    private String quoted(String topic) {
        String trimmed = topic.length() > AppConfig.TOPIC_DISPLAY_MAX_CHARS
                ? topic.substring(0, AppConfig.TOPIC_DISPLAY_MAX_CHARS) + "…"
                : topic;
        return "\"" + trimmed + "\"";
    }

    private final class GenerationHandler implements QuestionGenerationController.Callbacks {
        @Override
        public void onStarted(String topic) {
            generating = true;
            generationTopic = topic;
            generateButton.setText(GENERATING_LABEL);
            cancelGenerateButton.setVisible(true);
            cancelGenerateButton.setEnabled(true);
            loadingCard.begin("ChatGPT מנסח שאלות בנושא " + quoted(topic));
            questionsCards.show(questionsCardHolder, CARD_LOADING);
            UiTheme.applyRtl(questionsCardHolder);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            onQuestionsChanged();
        }

        @Override
        public void onSucceeded(GeneratedSurvey generated) {
            applyGeneratedQuestions(generated);
            finishGeneration();
        }

        @Override
        public void onFailed(String userMessage) {
            Dialogs.error(SurveyCreationPanel.this, "יצירת השאלות נכשלה: " + userMessage);
            finishGeneration();
        }

        @Override
        public void onCancelled() {
            Toast.show(SurveyCreationPanel.this, "יצירת השאלות בוטלה", Toast.Type.WARNING);
            finishGeneration();
        }
    }
}
