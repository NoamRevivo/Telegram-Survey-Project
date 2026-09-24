package org.example;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class AddQuestionDialog extends JDialog {
    private Question result;
    private final JTextField questionField = new JTextField(30);
    private final DefaultListModel<String> optionsModel = new DefaultListModel<>();

    public AddQuestionDialog(Frame owner) {
        this(owner, null);
    }

    public AddQuestionDialog(Frame owner, Question existing) {
        super(owner, existing == null ? "➕ הוספת שאלה" : "✏️ עריכת שאלה", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(UiFactory.pagePadding());

        JPanel top = new JPanel(new BorderLayout(6, 6));
        JLabel questionLabel = new JLabel("טקסט השאלה:");
        questionLabel.setFont(questionLabel.getFont().deriveFont(Font.BOLD, UiTheme.FONT_SMALL));
        top.add(questionLabel, BorderLayout.NORTH);
        top.add(questionField, BorderLayout.CENTER);

        JList<String> optionsList = new JList<>(optionsModel);
        JTextField optionField = new JTextField(15);
        JButton addOptionButton = new JButton("הוסף אפשרות (עד " + Question.MAX_OPTIONS + ")");
        Runnable addOption = () -> {
            String option = optionField.getText().trim();
            if (option.isEmpty()) {
                Dialogs.warn(this, "אפשרות ריקה", "אפשרות ריקה אינה חוקית.");
            } else if (option.length() > Question.MAX_OPTION_LENGTH) {
                Dialogs.warn(this, "אפשרות ארוכה מדי",
                        "אפשרות תשובה יכולה להכיל עד " + Question.MAX_OPTION_LENGTH + " תווים (הוזנו "
                                + option.length() + ").");
            } else if (optionsModel.size() >= Question.MAX_OPTIONS) {
                Dialogs.warn(this, "מגבלת אפשרויות",
                        "ניתן להזין עד " + Question.MAX_OPTIONS + " אפשרויות.");
            } else if (containsOption(option)) {
                Dialogs.warn(this, "אפשרות כפולה", "האפשרות \"" + option + "\" כבר קיימת.");
            } else {
                optionsModel.addElement(option);
                optionField.setText("");
            }
            optionField.requestFocusInWindow();
        };
        addOptionButton.addActionListener(e -> addOption.run());
        optionField.addActionListener(e -> addOption.run());
        JButton removeOptionButton = new JButton("הסר אפשרות נבחרת");
        removeOptionButton.addActionListener(e -> {
            int index = optionsList.getSelectedIndex();
            if (index >= 0) {
                optionsModel.remove(index);
            }
        });

        JPanel optionInputPanel = new JPanel(new BorderLayout(5, 5));
        optionInputPanel.add(optionField, BorderLayout.CENTER);
        optionInputPanel.add(addOptionButton, BorderLayout.EAST);

        JPanel optionButtonsPanel = UiFactory.actionsRow(removeOptionButton);

        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.setBorder(BorderFactory.createTitledBorder("אפשרויות תשובה"));
        centerPanel.add(new JScrollPane(optionsList), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(optionInputPanel, BorderLayout.NORTH);
        south.add(optionButtonsPanel, BorderLayout.SOUTH);
        centerPanel.add(south, BorderLayout.SOUTH);

        JButton confirmButton = new JButton(existing == null ? "✅ הוסף" : "✅ עדכן");
        confirmButton.setFont(confirmButton.getFont().deriveFont(Font.BOLD, UiTheme.FONT_SMALL));
        confirmButton.addActionListener(e -> onConfirm());

        add(top, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        JButton cancelButton = new JButton("ביטול");
        cancelButton.addActionListener(e -> dispose());
        add(UiFactory.actionsRow(confirmButton, cancelButton), BorderLayout.SOUTH);

        if (existing != null) {
            questionField.setText(existing.getText());
            for (String option : existing.getOptions()) {
                optionsModel.addElement(option);
            }
        }

        getRootPane().setDefaultButton(confirmButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private void onConfirm() {
        String text = questionField.getText().trim();
        if (text.isEmpty() || optionsModel.size() < Question.MIN_OPTIONS) {
            Dialogs.warn(this, "שאלה חסרה", "יש להזין טקסט שאלה ולפחות " + Question.MIN_OPTIONS + " אפשרויות תשובה.");
            return;
        }
        List<String> options = new ArrayList<>();
        for (int i = 0; i < optionsModel.size(); i++) {
            options.add(optionsModel.get(i));
        }
        try {
            result = new Question(text, options);
            dispose();
        } catch (IllegalArgumentException e) {
            Dialogs.error(this, e.getMessage());
        }
    }

    private boolean containsOption(String option) {
        for (int i = 0; i < optionsModel.size(); i++) {
            if (Question.optionKey(optionsModel.get(i)).equals(Question.optionKey(option))) {
                return true;
            }
        }
        return false;
    }

    public Question showDialog() {
        try {
            setVisible(true);
            return result;
        } finally {
            dispose();
        }
    }
}