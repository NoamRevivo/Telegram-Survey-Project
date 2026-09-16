package org.example;

import javax.swing.*;
import java.util.List;

public class MainFrame extends JFrame {

    public MainFrame(CommunityManager communityManager, SurveyManager surveyManager, ChatGPTService chatGPTService) {
        super("Telegram Survey Bot - לוח בקרה");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();

        CommunityPanel communityPanel = new CommunityPanel();
        communityManager.addListener(communityPanel);

        ActiveSurveyPanel activeSurveyPanel = new ActiveSurveyPanel();
        ResultsPanel resultsPanel = new ResultsPanel();
        surveyManager.addSurveyListener(activeSurveyPanel);
        surveyManager.addSurveyListener(resultsPanel);

        SurveyCreationPanel creationPanel = new SurveyCreationPanel(
                surveyManager, chatGPTService, () -> tabs.setSelectedIndex(2));
        surveyManager.addSurveyListener(new SurveyListener() {
            @Override
            public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
                SwingUtilities.invokeLater(() -> tabs.setSelectedIndex(2));
            }

            @Override
            public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
                SwingUtilities.invokeLater(() -> tabs.setSelectedIndex(3));
            }
        });

        tabs.addTab("קהילה", communityPanel);
        tabs.addTab("יצירת סקר", creationPanel);
        tabs.addTab("סקר פעיל", activeSurveyPanel);
        tabs.addTab("תוצאות", resultsPanel);

        add(tabs);
    }
}