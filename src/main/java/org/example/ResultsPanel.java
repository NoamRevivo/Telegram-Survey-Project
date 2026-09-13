package org.example;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultsPanel extends JPanel implements SurveyListener {

    private final JLabel lockedLabel = new JLabel("אין תוצאות להצגה עדיין - ממתין לסיום סקר.", SwingConstants.CENTER);

    public ResultsPanel() {
        setLayout(new BorderLayout());
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
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (Question question : survey.getQuestions()) {
            contentPanel.add(buildQuestionResultPanel(question, participants));
            contentPanel.add(Box.createVerticalStrut(15));
        }

        add(new JScrollPane(contentPanel), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JPanel buildQuestionResultPanel(Question question, java.util.List<SurveyParticipant> participants) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(question.getText()));

        Map<String, Integer> voteCounts = countVotes(question, participants);
        int totalAnswers = voteCounts.values().stream().mapToInt(Integer::intValue).sum();

        java.util.List<Map.Entry<String, Integer>> sortedByPopularity = new ArrayList<>(voteCounts.entrySet());
        sortedByPopularity.sort((a, b) -> b.getValue() - a.getValue());

        for (Map.Entry<String, Integer> entry : sortedByPopularity) {
            panel.add(buildAnswerRow(entry.getKey(), entry.getValue(), totalAnswers));
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

    private JPanel buildAnswerRow(String optionText, int votes, int totalAnswers) {
        int percent = totalAnswers == 0 ? 0 : Math.round(votes * 100f / totalAnswers);

        JPanel row = new JPanel(new BorderLayout(10, 2));
        row.add(new JLabel(optionText), BorderLayout.WEST);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(percent);
        bar.setStringPainted(true);
        bar.setString(percent + "% (" + votes + ")");
        row.add(bar, BorderLayout.CENTER);
        return row;
    }
}
