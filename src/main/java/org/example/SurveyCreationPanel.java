package org.example;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SurveyCreationPanel extends JPanel {

    private final SurveyManager surveyManager;
    private final ChatGPTService chatGPTService;
    private final Runnable onSurveyStartedCallback;

    private final DefaultListModel<Question> questionsModel = new DefaultListModel<>();
    private final JRadioButton manualRadio = new JRadioButton("יצירה ידנית", true);
    private final JRadioButton chatGptRadio = new JRadioButton("יצירה ע\"י ChatGPT");
    private final JTextField topicField = new JTextField(20);
    private final JButton generateButton = new JButton("צור סקר");
    private final JButton addQuestionButton = new JButton("הוסף שאלה");
    private final JComboBox<String> delayCombo =
            new JComboBox<>(new String[]{"מיידי", "1 דקה", "2 דקות", "5 דקות", "10 דקות"});

    public SurveyCreationPanel(SurveyManager surveyManager,
                               ChatGPTService chatGPTService,
                               Runnable onSurveyStartedCallback) {
        this.surveyManager = surveyManager;
        this.chatGPTService = chatGPTService;
        this.onSurveyStartedCallback = onSurveyStartedCallback;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildModePanel(), BorderLayout.NORTH);
        add(buildQuestionsListPanel(), BorderLayout.CENTER);
        add(buildStartPanel(), BorderLayout.SOUTH);

        updateModeState();
    }

    private JPanel buildModePanel() {
        ButtonGroup group = new ButtonGroup();
        group.add(manualRadio);
        group.add(chatGptRadio);
        manualRadio.addActionListener(e -> updateModeState());
        chatGptRadio.addActionListener(e -> updateModeState());
        generateButton.addActionListener(e -> onGenerateWithChatGpt());
        addQuestionButton.addActionListener(e -> onAddQuestionManually());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(manualRadio);
        panel.add(chatGptRadio);
        panel.add(new JLabel("נושא:"));
        panel.add(topicField);
        panel.add(generateButton);
        panel.add(addQuestionButton);
        return panel;
    }

    private JScrollPane buildQuestionsListPanel() {
        JList<Question> questionsList = new JList<>(questionsModel);
        questionsList.setCellRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel((index + 1) + ". " + value.getText() + "  " + value.getOptions()));
        return new JScrollPane(questionsList);
    }

    private JPanel buildStartPanel() {
        JButton startButton = new JButton("התחל סקר");
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, 14f));
        startButton.addActionListener(e -> onStartSurvey());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.add(new JLabel("תזמון:"));
        panel.add(delayCombo);
        panel.add(startButton);
        return panel;
    }

    private void updateModeState() {
        boolean isChatGpt = chatGptRadio.isSelected();
        topicField.setEnabled(isChatGpt);
        generateButton.setEnabled(isChatGpt);
        addQuestionButton.setEnabled(!isChatGpt);
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
            JOptionPane.showMessageDialog(this, "נא להזין נושא לסקר.");
            return;
        }
        generateButton.setEnabled(false);
        new SwingWorker<java.util.List<Question>, Void>() {
            @Override
            protected java.util.List<Question> doInBackground() throws Exception {
                return chatGPTService.generateSurvey(topic);
            }
            @Override
            protected void done() {
                generateButton.setEnabled(true);
                try {
                    java.util.List<Question> generated = get();
                    questionsModel.clear();
                    for (Question q : generated) {
                        questionsModel.addElement(q);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SurveyCreationPanel.this,
                            "שגיאה ביצירת הסקר מול ChatGPT: " + ex.getMessage());
                }
            }
        }.execute();
    }
    private void onStartSurvey() {
        if (questionsModel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "הוסף לפחות שאלה אחת!",
                    "שגיאה",
                    JOptionPane.WARNING_MESSAGE);  // ← WARNING, לא ERROR
            return;
        }
        String delayLabel = (String) delayCombo.getSelectedItem();
        int delayMinutes = parseDelay(delayLabel);
        try {
            List<Question> questions = new ArrayList<>();
            for (int i = 0; i < questionsModel.size(); i++) {
                questions.add(questionsModel.getElementAt(i));
            }
            surveyManager.createSurvey(questions, delayMinutes);
            onSurveyStartedCallback.run();
            questionsModel.clear();
            JOptionPane.showMessageDialog(this,
                    "הסקר התחיל בהצלחה!",
                    "הצלחה",
                    JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this,
                    e.getMessage(),
                    "שגיאה",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    private int parseDelay(String label) {
        if (label == null || label.equals("מיידי")) {
            return 0;
        }
        return Integer.parseInt(label.replaceAll("[^0-9]", ""));
    }
}
