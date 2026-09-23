package org.example;

import javax.swing.*;
import java.awt.*;
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
        // ברירת המחדל HIDE_ON_CLOSE מדליפה חלון בכל סגירה ב-X
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

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
                JOptionPane.showMessageDialog(this, "אפשרות ריקה אינה חוקית.");
            } else if (optionsModel.size() >= Question.MAX_OPTIONS) {
                JOptionPane.showMessageDialog(this, "ניתן להזין עד " + Question.MAX_OPTIONS + " אפשרויות.");
            } else if (containsOption(option)) {
                JOptionPane.showMessageDialog(this, "האפשרות \"" + option + "\" כבר קיימת.");
            } else {
                optionsModel.addElement(option);
                optionField.setText("");
            }
            optionField.requestFocusInWindow();
        };
        addOptionButton.addActionListener(e -> addOption.run());
        optionField.addActionListener(e -> addOption.run());   // Enter בשדה = הוספת אפשרות
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

        JPanel optionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        optionButtonsPanel.add(removeOptionButton);

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
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonsPanel.add(confirmButton);
        buttonsPanel.add(cancelButton);
        add(buttonsPanel, BorderLayout.SOUTH);

        if (existing != null) {
            questionField.setText(existing.getText());
            for (String option : existing.getOptions()) {
                optionsModel.addElement(option);
            }
        }

        // Enter מאשר, Esc סוגר, גודל לפי התוכן, ימין-לשמאל
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
            JOptionPane.showMessageDialog(this, "יש להזין טקסט שאלה ולפחות 2 אפשרויות תשובה.");
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
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    private boolean containsOption(String option) {
        for (int i = 0; i < optionsModel.size(); i++) {
            if (optionsModel.get(i).trim().equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    /** החלון משוחרר בכל מסלול יציאה — אישור, ביטול, Esc או X. */
    public Question showDialog() {
        try {
            setVisible(true);   // מודאלי — חוסם עד סגירה
            return result;
        } finally {
            dispose();
        }
    }
}