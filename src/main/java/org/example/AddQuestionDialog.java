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
        super(owner, "הוספת שאלה", true);
        setLayout(new BorderLayout(8, 8));
        setSize(420, 380);
        setLocationRelativeTo(owner);

        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.add(new JLabel("טקסט השאלה:"), BorderLayout.NORTH);
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

        JPanel optionInputPanel = new JPanel(new BorderLayout(5, 5));
        optionInputPanel.add(optionField, BorderLayout.CENTER);
        optionInputPanel.add(addOptionButton, BorderLayout.EAST);

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(new JScrollPane(optionsList), BorderLayout.CENTER);
        centerPanel.add(optionInputPanel, BorderLayout.SOUTH);

        JButton confirmButton = new JButton("אישור");
        confirmButton.addActionListener(e -> onConfirm());

        add(top, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(confirmButton, BorderLayout.SOUTH);
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