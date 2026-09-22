package org.example;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * התוצאות מתעדכנות בזמן אמת תוך כדי הסקר.
 * <p>
 * R5-C03: בזמן הסקר הסדר קבוע כדי שהשורות לא יקפצו בכל תשובה נכנסת,
 * וברגע הסגירה הן ממוינות לפי שכיחות בסדר יורד — כנדרש בדרישה 6.
 */
public class ResultsPanel extends JPanel implements SurveyListener {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_RESULTS = "results";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHolder = new JPanel(cards);

    private final JLabel headerLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel summaryLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JPanel questionsContainer = new JPanel();

    private final List<QuestionView> questionViews = new ArrayList<>();
    private List<SurveyParticipant> participants = new ArrayList<>();
    private LocalDateTime startedAt;
    private boolean surveyClosed;

    public ResultsPanel() {
        setLayout(new BorderLayout());
        cardHolder.add(UiFactory.emptyState(AppIcons.results(48), "אין תוצאות להצגה עדיין",
                "ברגע שיתחיל סקר, התוצאות יופיעו כאן ויתעדכנו בזמן אמת."), CARD_EMPTY);
        cardHolder.add(buildResultsCard(), CARD_RESULTS);
        add(cardHolder, BorderLayout.CENTER);
        cards.show(cardHolder, CARD_EMPTY);
    }

    private JPanel buildResultsCard() {
        UiFactory.styled(headerLabel, Font.BOLD, UiTheme.FONT_SECTION, null);
        UiFactory.styled(summaryLabel, Font.PLAIN, UiTheme.FONT_SMALL, UiTheme.MUTED_TEXT);

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        headerPanel.add(UiFactory.centered(headerLabel));
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(UiFactory.centered(summaryLabel));

        questionsContainer.setLayout(new BoxLayout(questionsContainer, BoxLayout.Y_AXIS));
        questionsContainer.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));

        JPanel card = new JPanel(new BorderLayout());
        card.add(headerPanel, BorderLayout.NORTH);
        card.add(new JScrollPane(questionsContainer), BorderLayout.CENTER);
        return card;
    }

    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> {
            this.participants = participants;
            this.startedAt = survey.getStartTime();
            this.surveyClosed = false;
            buildStructure(survey);
            refreshValues();
            cards.show(cardHolder, CARD_RESULTS);
            UiTheme.applyRtl(this);
            revalidate();
            repaint();
        });
    }

    @Override
    public void onAnswerRecorded(SurveyParticipant participant) {
        SwingUtilities.invokeLater(this::refreshValues);
    }

    @Override
    public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
        SwingUtilities.invokeLater(() -> {
            this.participants = participants;
            this.startedAt = survey.getStartTime();
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

    /** R5-M13: סקר שבוטל לפני השליחה אינו מציג לשונית תוצאות ריקה. */
    @Override
    public void onSurveyCancelled(Survey survey) {
        SwingUtilities.invokeLater(() -> {
            participants = new ArrayList<>();
            questionViews.clear();
            questionsContainer.removeAll();
            surveyClosed = false;
            cards.show(cardHolder, CARD_EMPTY);
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
        headerLabel.setText(surveyClosed
                ? "🏆 תוצאות סופיות" + startedAtSuffix()
                : "🔴 תוצאות חיות — מתעדכן בזמן אמת");
        headerLabel.setForeground(surveyClosed ? UiTheme.SUCCESS_GREEN : UiTheme.ERROR_RED);
        summaryLabel.setText(buildSummaryText());

        for (QuestionView view : questionViews) {
            // R5-C03: מיון לפי שכיחות רק בתוצאות הסופיות — בזמן הסקר הסדר יציב
            view.refresh(participants, surveyClosed);
        }
    }

    private String startedAtSuffix() {
        return startedAt == null ? "" : "  ·  הסקר נפתח ב-" + startedAt.format(TIME_FORMAT);
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
        /** R5-C03: שורת האפשרות כרכיב אחד — כך אפשר למיין בלי לבנות מחדש */
        private final Map<String, JComponent> rowsByOption = new LinkedHashMap<>();

        private QuestionView(int questionNumber, Question question) {
            this.questionNumber = questionNumber;
            this.question = question;

            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.PANEL_BORDER, 1, true),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)));

            header.setFont(header.getFont().deriveFont(Font.BOLD, UiTheme.FONT_BODY));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(header);
            panel.add(Box.createVerticalStrut(8));

            for (String option : question.getOptions()) {
                JLabel optionLabel = new JLabel(option);
                optionLabel.setFont(optionLabel.getFont().deriveFont(Font.PLAIN, UiTheme.FONT_SMALL));
                optionLabel.setPreferredSize(new Dimension(
                        AppConfig.OPTION_LABEL_WIDTH, AppConfig.OPTION_LABEL_HEIGHT));

                JProgressBar bar = new JProgressBar(0, 100);
                bar.setStringPainted(true);
                bar.setString("0% (0)");
                bar.setForeground(UiTheme.BRAND_BLUE);

                JPanel row = new JPanel(new BorderLayout(10, 2));
                row.add(optionLabel, BorderLayout.WEST);
                row.add(bar, BorderLayout.CENTER);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                // R5-C03: הרווח בין השורות הוא חלק מהשורה עצמה, לא Strut נפרד שיישאר מאחור בעת מיון
                row.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

                panel.add(row);
                barsByOption.put(option, bar);
                labelsByOption.put(option, optionLabel);
                rowsByOption.put(option, row);
            }
        }

        private void refresh(List<SurveyParticipant> participants, boolean sortByFrequency) {
            Map<String, Integer> counts = countVotes(participants);

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
                label.setFont(label.getFont().deriveFont(isLeading ? Font.BOLD : Font.PLAIN,
                        UiTheme.FONT_SMALL));
            }

            if (sortByFrequency) {
                reorderRows(counts);
            }
        }

        private Map<String, Integer> countVotes(List<SurveyParticipant> participants) {
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

        /** R5-C03: ממיין את שורות האפשרויות לפי מספר הקולות בסדר יורד (שובר שוויון: סדר המקור). */
        private void reorderRows(Map<String, Integer> counts) {
            List<String> ordered = new ArrayList<>(counts.keySet());
            ordered.sort(Comparator.comparingInt((String option) -> counts.get(option)).reversed());
            for (String option : ordered) {
                JComponent row = rowsByOption.get(option);
                panel.remove(row);
                panel.add(row);   // מוסיף בסוף → מתקבל סדר יורד
            }
            panel.revalidate();
            panel.repaint();
        }
    }
}