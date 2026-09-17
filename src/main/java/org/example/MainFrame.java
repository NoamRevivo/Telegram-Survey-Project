package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class MainFrame extends JFrame {

    private final JLabel statusBar = new JLabel();
    private int communitySize = 0;
    private boolean surveyActive = false;

    public MainFrame(CommunityManager communityManager, SurveyManager surveyManager, ChatGPTService chatGPTService) {
        super("Telegram Survey Bot - לוח בקרה");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 740);
        setMinimumSize(new Dimension(880, 640));
        setLocationRelativeTo(null);
        setIconImage(createAppIcon());

        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 14f));

        CommunityPanel communityPanel = new CommunityPanel();
        communityManager.addListener(communityPanel);

        ActiveSurveyPanel activeSurveyPanel = new ActiveSurveyPanel();
        ResultsPanel resultsPanel = new ResultsPanel();
        surveyManager.addSurveyListener(activeSurveyPanel);
        surveyManager.addSurveyListener(resultsPanel);

        SurveyCreationPanel creationPanel = new SurveyCreationPanel(
                surveyManager, communityManager, chatGPTService, () -> tabs.setSelectedIndex(2));
        communityManager.addListener(creationPanel);

        communityManager.addListener((newUser, newSize) -> {
            communitySize = newSize;
            SwingUtilities.invokeLater(this::refreshStatusBar);
        });

        surveyManager.addSurveyListener(new SurveyListener() {
            @Override
            public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
                surveyActive = true;
                SwingUtilities.invokeLater(() -> {
                    tabs.setSelectedIndex(2);
                    tabs.setIconAt(2, AppIcons.live(20));
                    tabs.setTitleAt(2, "סקר פעיל (חי)");
                    refreshStatusBar();
                });
            }

            @Override
            public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
                surveyActive = false;
                SwingUtilities.invokeLater(() -> {
                    tabs.setSelectedIndex(3);
                    tabs.setIconAt(2, AppIcons.active(20));
                    tabs.setTitleAt(2, "סקר פעיל");
                    refreshStatusBar();
                });
            }
        });

        tabs.addTab("קהילה", AppIcons.community(20), communityPanel);
        tabs.addTab("יצירת סקר", AppIcons.create(20), creationPanel);
        tabs.addTab("סקר פעיל", AppIcons.active(20), activeSurveyPanel);
        tabs.addTab("תוצאות", AppIcons.results(20), resultsPanel);

        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        refreshStatusBar();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiTheme.BRAND_BLUE);
        header.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));

        JLabel title = new JLabel("Telegram Survey Bot — לוח בקרה", AppIcons.robotOnLight(32), SwingConstants.LEADING);
        title.setIconTextGap(12);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        return header;
    }

    private JLabel buildStatusBar() {
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 18, 6, 18));
        statusBar.setFont(statusBar.getFont().deriveFont(12f));
        statusBar.setForeground(Color.DARK_GRAY);
        return statusBar;
    }

    private void refreshStatusBar() {
        String surveyPart = surveyActive ? "📋 סקר פעיל כרגע" : "📋 אין סקר פעיל כרגע";
        statusBar.setText("🟢 המערכת פעילה   |   👥 " + communitySize + " חברים בקהילה   |   " + surveyPart);
    }

    private Image createAppIcon() {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(UiTheme.BRAND_BLUE);
        g2.fillOval(0, 0, size, size);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        FontMetrics fm = g2.getFontMetrics();
        String text = "S";
        int textWidth = fm.stringWidth(text);
        g2.drawString(text, (size - textWidth) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
        return image;
    }
}