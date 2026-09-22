package org.example;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

public class MainFrame extends JFrame {

    private final JLabel statusBar = new JLabel();
    private final String botUsername;

    private final CommunityManager communityManager;
    private final SurveyManager surveyManager;

    private final JTabbedPane tabs = new JTabbedPane();
    private final CommunityPanel communityPanel;
    private final SurveyCreationPanel creationPanel;
    private final ActiveSurveyPanel activeSurveyPanel;
    private final ResultsPanel resultsPanel;
    private final CommunityListener statusBarListener;
    private final SurveyListener tabsListener;

    private int communitySize;
    private boolean surveyActive;

    public MainFrame(CommunityManager communityManager,
                     SurveyManager surveyManager,
                     ChatGPTService chatGPTService,
                     String botUsername) {
        super("Telegram Survey Bot - לוח בקרה");
        this.communityManager = communityManager;
        this.surveyManager = surveyManager;
        this.botUsername = botUsername;
        // R5-C02: המספר האמיתי כבר עכשיו — לא 0 שסותר את מסך יצירת הסקר
        this.communitySize = communityManager.getCommunitySize();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppConfig.WINDOW_WIDTH, AppConfig.WINDOW_HEIGHT);
        setMinimumSize(new Dimension(AppConfig.WINDOW_MIN_WIDTH, AppConfig.WINDOW_MIN_HEIGHT));
        setLocationRelativeTo(null);
        setIconImage(createAppIcon());
        getContentPane().setBackground(UiTheme.PAGE_BACKGROUND);

        add(buildHeader(), BorderLayout.NORTH);

        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, UiTheme.FONT_BODY));

        communityPanel = new CommunityPanel(communityManager);
        activeSurveyPanel = new ActiveSurveyPanel(surveyManager);
        resultsPanel = new ResultsPanel();
        creationPanel = new SurveyCreationPanel(
                surveyManager, communityManager, chatGPTService, this::showActiveSurveyTab);

        tabs.addTab("קהילה", AppIcons.community(20), communityPanel);
        tabs.addTab("יצירת סקר", AppIcons.create(20), creationPanel);
        tabs.addTab("סקר פעיל", AppIcons.active(20), activeSurveyPanel);
        tabs.addTab("תוצאות", AppIcons.results(20), resultsPanel);

        statusBarListener = (newUser, newSize) -> SwingUtilities.invokeLater(() -> {
            communitySize = newSize;
            refreshStatusBar();
        });
        tabsListener = new TabsListener();

        communityManager.addListener(communityPanel);
        communityManager.addListener(creationPanel);
        communityManager.addListener(statusBarListener);
        surveyManager.addSurveyListener(activeSurveyPanel);
        surveyManager.addSurveyListener(resultsPanel);
        surveyManager.addSurveyListener(tabsListener);

        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        refreshStatusBar();
        UiTheme.applyRtl(getContentPane());
    }

    /** R5-L02: אינדקס לפי הרכיב עצמו — לא קבוע ידני שנשבר בשקט כשמוסיפים לשונית. */
    private int tabIndexOf(JPanel panel) {
        return tabs.indexOfComponent(panel);
    }

    /**
     * R5-L06: המעבר ללשונית «סקר פעיל» קורה כאן בלבד, מיד עם פתיחת הסקר —
     * כך הוא עובד גם כשהסקר נפתח בהשהיה, והמאזין שלמטה מטפל רק בכותרות.
     */
    private void showActiveSurveyTab() {
        tabs.setSelectedIndex(tabIndexOf(activeSurveyPanel));
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiTheme.BRAND_BLUE);
        header.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));

        JLabel title = new JLabel("Telegram Survey Bot — לוח בקרה",
                AppIcons.robotOnLight(32), SwingConstants.LEADING);
        title.setIconTextGap(12);
        UiFactory.styled(title, Font.BOLD, UiTheme.FONT_HEADLINE, Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JLabel botLabel = UiFactory.styled(new JLabel("@" + botUsername),
                Font.PLAIN, UiTheme.FONT_BODY, Color.WHITE);
        header.add(botLabel, BorderLayout.EAST);

        return header;
    }

    private JLabel buildStatusBar() {
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 18, 6, 18));
        statusBar.setFont(statusBar.getFont().deriveFont(UiTheme.FONT_TINY));
        statusBar.setForeground(Color.DARK_GRAY);
        return statusBar;
    }

    private void refreshStatusBar() {
        String surveyPart = surveyActive ? "📋 סקר פעיל כרגע" : "📋 אין סקר פעיל כרגע";
        statusBar.setText("🟢 מחובר כ-@" + botUsername
                + "   |   👥 " + communitySize + " חברים בקהילה   |   " + surveyPart);
    }

    /**
     * R5-M16: מאזינים שנרשמו על ידי החלון מוסרים כשהוא נסגר —
     * אחרת חלון שנסגר ממשיך לקבל אירועים ולהחזיק את כל עץ הרכיבים בזיכרון.
     */
    @Override
    public void dispose() {
        communityManager.removeListener(communityPanel);
        communityManager.removeListener(creationPanel);
        communityManager.removeListener(statusBarListener);
        surveyManager.removeSurveyListener(activeSurveyPanel);
        surveyManager.removeSurveyListener(resultsPanel);
        surveyManager.removeSurveyListener(tabsListener);
        super.dispose();
    }

    /** מעדכן את כותרות הלשוניות בלבד — המעבר ביניהן נעשה ב-showActiveSurveyTab. */
    private class TabsListener implements SurveyListener {

        @Override
        public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
            SwingUtilities.invokeLater(() -> {
                surveyActive = true;
                int activeTab = tabIndexOf(activeSurveyPanel);
                tabs.setIconAt(activeTab, AppIcons.live(20));
                tabs.setTitleAt(activeTab, "סקר פעיל (חי)");
                tabs.setTitleAt(tabIndexOf(resultsPanel), "תוצאות (חי)");
                refreshStatusBar();
            });
        }

        @Override
        public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
            SwingUtilities.invokeLater(() -> {
                surveyActive = false;
                resetTabTitles();
                tabs.setSelectedIndex(tabIndexOf(resultsPanel));
                refreshStatusBar();
            });
        }

        /** R5-M13: סקר שבוטל אינו מקפיץ ללשונית תוצאות ריקה. */
        @Override
        public void onSurveyCancelled(Survey survey) {
            SwingUtilities.invokeLater(() -> {
                surveyActive = false;
                resetTabTitles();
                refreshStatusBar();
            });
        }

        private void resetTabTitles() {
            int activeTab = tabIndexOf(activeSurveyPanel);
            tabs.setIconAt(activeTab, AppIcons.active(20));
            tabs.setTitleAt(activeTab, "סקר פעיל");
            tabs.setTitleAt(tabIndexOf(resultsPanel), "תוצאות");
        }
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