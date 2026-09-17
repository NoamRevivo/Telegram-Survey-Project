package org.example;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultsPanel extends JPanel implements SurveyListener {

    private final JLabel lockedLabel = new JLabel("🏆  אין תוצאות להצגה עדיין - ממתין לסיום סקר.", SwingConstants.CENTER);

    public ResultsPanel() {
        setLayout(new BorderLayout());
        lockedLabel.setFont(lockedLabel.getFont().deriveFont(Font.PLAIN, 16f));
        add(lockedLabel, BorderLayout.CENTER);
    }

    @Override
    public void onSurveyClosed(Survey survey, java.util.List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> renderResults(survey, participants));
    }

    private void renderResults(Survey survey, java.util.List<SurveyParticipant> participants) {
        removeAll();

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        int questionNumber = 1;
        for (Question question : survey.getQuestions()) {
            contentPanel.add(buildQuestionResultPanel(questionNumber++, question, participants));
            contentPanel.add(Box.createVerticalStrut(16));
        }

        add(new JScrollPane(contentPanel), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JPanel buildQuestionResultPanel(int questionNumber, Question question, java.util.List<SurveyParticipant> participants) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("שאלה " + questionNumber + ": " + question.getText()));

        Map<String, Integer> voteCounts = countVotes(question, participants);
        int totalAnswers = voteCounts.values().stream().mapToInt(Integer::intValue).sum();

        java.util.List<Map.Entry<String, Integer>> sortedByPopularity = new ArrayList<>(voteCounts.entrySet());
        sortedByPopularity.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        boolean first = true;
        for (Map.Entry<String, Integer> entry : sortedByPopularity) {
            panel.add(buildAnswerRow(entry.getKey(), entry.getValue(), totalAnswers, first));
            panel.add(Box.createVerticalStrut(4));
            first = false;
        }
        return panel;
    }

    private Map<String, Integer> countVotes(Question question, List<SurveyParticipant> participants) {
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
        return counts;
    }

    private JPanel buildAnswerRow(String optionText, int votes, int totalAnswers, boolean isTop) {
        int percent = totalAnswers == 0 ? 0 : Math.round(votes * 100f / totalAnswers);

        JPanel row = new JPanel(new BorderLayout(10, 2));
        JLabel optionLabel = new JLabel((isTop && votes > 0 ? "🥇 " : "     ") + optionText);
        optionLabel.setFont(optionLabel.getFont().deriveFont(isTop && votes > 0 ? Font.BOLD : Font.PLAIN, 13f));
        row.add(optionLabel, BorderLayout.WEST);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(percent);
        bar.setStringPainted(true);
        bar.setString(percent + "% (" + votes + ")");
        bar.setForeground(isTop && votes > 0 ? UiTheme.SUCCESS_GREEN : UiTheme.BRAND_BLUE);
        row.add(bar, BorderLayout.CENTER);
        return row;
    }
}