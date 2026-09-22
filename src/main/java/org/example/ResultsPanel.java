package org.example;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * התוצאות מתעדכנות בזמן אמת תוך כדי הסקר, ולא רק בסיומו.
 * סדר האפשרויות נשאר קבוע כדי שהשורות לא יקפצו בכל תשובה נכנסת —
 * המוביל מסומן ב-🥇 באופן דינמי.
 */
public class ResultsPanel extends JPanel implements SurveyListener {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_RESULTS = "results";

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHolder = new JPanel(cards);

    private final JLabel headerLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel summaryLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JPanel questionsContainer = new JPanel();

    private final List<QuestionView> questionViews = new ArrayList<>();
    private List<SurveyParticipant> participants = new ArrayList<>();
    private boolean surveyClosed;

    public ResultsPanel() {
        setLayout(new BorderLayout());
        cardHolder.add(buildEmptyCard(), CARD_EMPTY);
        cardHolder.add(buildResultsCard(), CARD_RESULTS);
        add(cardHolder, BorderLayout.CENTER);
        cards.show(cardHolder, CARD_EMPTY);
    }

    /** מצב ריק מנחה במקום שורת טקסט בודדת */
    private JPanel buildEmptyCard() {
        JLabel icon = new JLabel(AppIcons.results(48), SwingConstants.CENTER);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("אין תוצאות להצגה עדיין", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(UiTheme.BRAND_DARK_BLUE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("ברגע שיתחיל סקר, התוצאות יופיעו כאן ויתעדכנו בזמן אמת.",
                SwingConstants.CENTER);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 14f));
        hint.setForeground(UiTheme.MUTED_TEXT);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(Box.createVerticalGlue());
        column.add(icon);
        column.add(Box.createVerticalStrut(14));
        column.add(title);
        column.add(Box.createVerticalStrut(8));
        column.add(hint);
        column.add(Box.createVerticalGlue());

        JPanel card = new JPanel(new BorderLayout());
        card.add(column, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildResultsCard() {
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 18f));
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, 13f));
        summaryLabel.setForeground(UiTheme.MUTED_TEXT);

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        summaryLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(headerLabel);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(summaryLabel);

        questionsContainer.setLayout(new BoxLayout(questionsContainer, BoxLayout.Y_AXIS));
        questionsContainer.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));

        JPanel card = new JPanel(new BorderLayout());
        card.add(headerPanel, BorderLayout.NORTH);
        card.add(new JScrollPane(questionsContainer), BorderLayout.CENTER);
        return card;
    }

    /** M-05: סקר חדש התחיל — בונים מחדש את מבנה התוצאות */
    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> {
            this.participants = participants;
            this.surveyClosed = false;
            buildStructure(survey);
            refreshValues();
            cards.show(cardHolder, CARD_RESULTS);
            UiTheme.applyRtl(this);   // יישור מחדש אחרי בניית תוכן דינמי
            revalidate();
            repaint();
        });
    }

    /** כל תשובה נכנסת מעדכנת מיד את הפסים */
    @Override
    public void onAnswerRecorded(SurveyParticipant participant) {
        SwingUtilities.invokeLater(this::refreshValues);
    }

    @Override
    public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> {
            this.participants = participants;
            this.surveyClosed = true;
            if (participants.isEmpty()) {
                cards.show(cardHolder, CARD_EMPTY);
                return;
            }
            if (questionViews.isEmpty()) {
                buildStructure(survey);
            }
            refreshValues();
            cards.show(cardHolder, CARD_RESULTS);
            UiTheme.applyRtl(this);
            revalidate();
            repaint();
        });
    }

    private void buildStructure(Survey survey) {
        questionsContainer.removeAll();
        questionViews.clear();

        int questionNumber = 1;
        for (Question question : survey.getQuestions()) {
            QuestionView view = new QuestionView(questionNumber++, question);
            questionViews.add(view);
            questionsContainer.add(view.panel);
            questionsContainer.add(Box.createVerticalStrut(16));
        }
    }

    private void refreshValues() {
        if (questionViews.isEmpty()) {
            return;
        }
        headerLabel.setText(surveyClosed ? "🏆 תוצאות סופיות" : "🔴 תוצאות חיות — מתעדכן בזמן אמת");
        headerLabel.setForeground(surveyClosed ? UiTheme.SUCCESS_GREEN : UiTheme.ERROR_RED);
        summaryLabel.setText(buildSummaryText());

        for (QuestionView view : questionViews) {
            view.refresh(participants);
        }
    }

    private String buildSummaryText() {
        int total = participants.size();
        if (total == 0) {
            return "אין משתתפים בסקר זה.";
        }
        long completed = participants.stream().filter(SurveyParticipant::isCompleted).count();
        long responded = participants.stream().filter(p -> p.getAnsweredQuestionsCount() > 0).count();
        int completionPercent = Math.round(completed * 100f / total);
        return "✅ השלימו את כל השאלות: " + completed + " מתוך " + total + " (" + completionPercent + "%)"
                + "   ·   ✍ השיבו לפחות על שאלה אחת: " + responded + " מתוך " + total;
    }

    /** מחזיק את הרכיבים של שאלה אחת כדי לעדכן ערכים בלי לבנות מחדש ובלי הבהובים */
    private static class QuestionView {
        private final Question question;
        private final int questionNumber;
        private final JLabel header = new JLabel();
        private final JPanel panel = new JPanel();
        private final Map<String, JProgressBar> barsByOption = new LinkedHashMap<>();
        private final Map<String, JLabel> labelsByOption = new LinkedHashMap<>();

        private QuestionView(int questionNumber, Question question) {
            this.questionNumber = questionNumber;
            this.question = question;

            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.PANEL_BORDER, 1, true),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)));

            header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(header);
            panel.add(Box.createVerticalStrut(8));

            for (String option : question.getOptions()) {
                JLabel optionLabel = new JLabel(option);
                optionLabel.setFont(optionLabel.getFont().deriveFont(Font.PLAIN, 13f));
                optionLabel.setPreferredSize(new Dimension(170, 22));

                JProgressBar bar = new JProgressBar(0, 100);
                bar.setStringPainted(true);
                bar.setString("0% (0)");
                bar.setForeground(UiTheme.BRAND_BLUE);

                JPanel row = new JPanel(new BorderLayout(10, 2));
                row.add(optionLabel, BorderLayout.WEST);
                row.add(bar, BorderLayout.CENTER);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);

                panel.add(row);
                panel.add(Box.createVerticalStrut(4));

                barsByOption.put(option, bar);
                labelsByOption.put(option, optionLabel);
            }
        }

        private void refresh(List<SurveyParticipant> participants) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String option : question.getOptions()) {
                counts.put(option, 0);
            }
            for (SurveyParticipant participant : participants) {
                String answer = participant.getAnswers().get(question.getId());
                if (answer != null && counts.containsKey(answer)) {
                    counts.put(answer, counts.get(answer) + 1);
                }
            }

            int totalAnswers = counts.values().stream().mapToInt(Integer::intValue).sum();
            int leadingVotes = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);

            header.setText("שאלה " + questionNumber + ": " + question.getText()
                    + "   (ענו " + totalAnswers + " מתוך " + participants.size() + " משתתפים)");

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                int votes = entry.getValue();
                int percent = totalAnswers == 0 ? 0 : Math.round(votes * 100f / totalAnswers);
                boolean isLeading = votes > 0 && votes == leadingVotes;

                JProgressBar bar = barsByOption.get(entry.getKey());
                bar.setValue(percent);
                bar.setString(percent + "% (" + votes + ")");
                bar.setForeground(isLeading ? UiTheme.SUCCESS_GREEN : UiTheme.BRAND_BLUE);

                JLabel label = labelsByOption.get(entry.getKey());
                label.setText((isLeading ? "🥇 " : "     ") + entry.getKey());
                label.setFont(label.getFont().deriveFont(isLeading ? Font.BOLD : Font.PLAIN, 13f));
            }
        }
    }
}