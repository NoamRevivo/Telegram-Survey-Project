package org.example;

import javax.swing.*;
import java.awt.*;
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
        setLayout(new BorderLayout(10, 10));
        setSize(440, 400);
        setLocationRelativeTo(owner);
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        JLabel questionLabel = new JLabel("טקסט השאלה:");
        questionLabel.setFont(questionLabel.getFont().deriveFont(Font.BOLD, 13f));
        top.add(questionLabel, BorderLayout.NORTH);
        top.add(questionField, BorderLayout.CENTER);

        JList<String> optionsList = new JList<>(optionsModel);
        JTextField optionField = new JTextField(15);
        JButton addOptionButton = new JButton("הוסף אפשרות (עד 4)");
        addOptionButton.addActionListener(e -> {
            String option = optionField.getText().trim();
            if (!option.isEmpty() && optionsModel.size() < 4) {
                optionsModel.addElement(option);
                optionField.setText("");
            }
        });
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
        confirmButton.setFont(confirmButton.getFont().deriveFont(Font.BOLD, 13f));
        confirmButton.addActionListener(e -> onConfirm());

        add(top, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(confirmButton, BorderLayout.SOUTH);

        if (existing != null) {
            questionField.setText(existing.getText());
            for (String option : existing.getOptions()) {
                optionsModel.addElement(option);
            }
        }
    }

    private void onConfirm() {
        String text = questionField.getText().trim();
        if (text.isEmpty() || optionsModel.size() < 2) {
            JOptionPane.showMessageDialog(this, "יש להזין טקסט שאלה ולפחות 2 אפשרויות תשובה.");
            return;
        }
        List<String> options = new ArrayList<>();
        for (int i = 0; i < optionsModel.size(); i++) {
            options.add(optionsModel.get(i));
        }
        result = new Question(text, options);
        dispose();
    }

    public Question showDialog() {
        setVisible(true);
        return result;
    }
}