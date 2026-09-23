package org.example;

import com.formdev.flatlaf.FlatLightLaf;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static final boolean SEED_FAKE_MEMBERS_FOR_TESTING = true;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, error) -> LOG.log(Level.SEVERE, "חריגה לא מטופלת בחוט " + thread.getName(), error));

        String botUsername = AppConfig.env(AppConfig.ENV_BOT_USERNAME);
        String botToken = AppConfig.env(AppConfig.ENV_BOT_TOKEN);
        if (botUsername == null || botToken == null) {
            showFatal("חסרים משתני הסביבה " + AppConfig.ENV_BOT_USERNAME
                    + " / " + AppConfig.ENV_BOT_TOKEN + ".\nיש להגדיר אותם ב-Run Configuration.");
            return;
        }
        CommunityManager communityManager = new CommunityManager();
        if (SEED_FAKE_MEMBERS_FOR_TESTING)
        {
            communityManager.addMember(-900000001L, "דני (פיקטיבי)", "danny_test");
            communityManager.addMember(-900000002L, "מיכל (פיקטיבית)", "michal_test");
            LOG.warning("SEED_FAKE_MEMBERS_FOR_TESTING=true — נוספו 3 חברים פיקטיביים. "
                    + "לא לשכוח להחזיר ל-false לפני ההגשה!");
        }
        SurveyManager surveyManager = new SurveyManager(communityManager);
        ChatGPTService chatGPTService = new ChatGPTService(
                AppConfig.env(AppConfig.ENV_SURVEY_API_TOKEN),
                AppConfig.env(AppConfig.ENV_SURVEY_API_URL, AppConfig.DEFAULT_SURVEY_API_URL));
        TelegramBotService botService =
                new TelegramBotService(botUsername, botToken, communityManager, surveyManager);

        final MainFrame[] frameHolder = new MainFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                applyLookAndFeel();
                frameHolder[0] = new MainFrame(communityManager, surveyManager, chatGPTService, botUsername);
                frameHolder[0].setVisible(true);
            });
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "בניית הממשק נכשלה", e);
            showFatal("בניית הממשק נכשלה: " + e.getMessage());
            return;
        }

        BotSession session;
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            session = botsApi.registerBot(botService.gateway());
        } catch (TelegramApiException e) {
            LOG.log(Level.SEVERE, "רישום הבוט נכשל", e);
            showFatal("לא ניתן להתחבר לטלגרם: " + e.getMessage()
                    + "\nבדוק את " + AppConfig.ENV_BOT_TOKEN + " ואת החיבור לרשת.");
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (session.isRunning()) {
                session.stop();
            }
            surveyManager.shutdown();
            botService.shutdownGracefully(AppConfig.PRIORITY_SHUTDOWN_TIMEOUT);
            chatGPTService.shutdown();
        }, "shutdown-hook"));
    }

        private static void applyLookAndFeel() {
        try {
            UIManager.put("Component.accentColor", UiTheme.BRAND_BLUE);
            UIManager.put("Button.arc", 14);
            UIManager.put("Component.arc", 10);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("TextComponent.arc", 8);
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "טעינת ערכת העיצוב נכשלה", ex);
        }
    }

        private static void showFatal(String message) {
        try {
            SwingUtilities.invokeAndWait(() ->
                    JOptionPane.showMessageDialog(null, message, "שגיאה בהפעלה", JOptionPane.ERROR_MESSAGE));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, message, e);
        }
        System.exit(1);
    }
}