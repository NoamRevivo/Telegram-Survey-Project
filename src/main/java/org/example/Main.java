package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main
{
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args)
    {
        try
        {
            UIManager.put("Component.accentColor", UiTheme.BRAND_BLUE);
            UIManager.put("Button.arc", 14);
            UIManager.put("Component.arc", 10);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("TextComponent.arc", 8);
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ex)
        {
            LOG.log(Level.WARNING, "טעינת ערכת העיצוב נכשלה", ex);
        }

        String botUsername = System.getenv("BOT_USERNAME");
        String botToken = System.getenv("BOT_TOKEN");
        if (botUsername == null || botUsername.isBlank() || botToken == null || botToken.isBlank())
        {
            showFatal("חסרים משתני הסביבה BOT_USERNAME / BOT_TOKEN.\nיש להגדיר אותם ב-Run Configuration.");
            return;
        }

        CommunityManager communityManager = new CommunityManager();
        SurveyManager surveyManager = new SurveyManager(communityManager);
        ChatGPTService chatGPTService = new ChatGPTService();
        TelegramBotService botService = new TelegramBotService(
                botUsername.trim(),
                botToken.trim(),
                communityManager,
                surveyManager
        );

        BotSession session;
        try
        {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            session = botsApi.registerBot(botService);
        } catch (TelegramApiException e)
        {
            LOG.log(Level.SEVERE, "רישום הבוט נכשל", e);
            showFatal("לא ניתן להתחבר לטלגרם: " + e.getMessage() + "\nבדוק את BOT_TOKEN ואת החיבור לרשת.");
            return;
        }

        SwingUtilities.invokeLater(() ->
        {
            MainFrame frame = new MainFrame(communityManager, surveyManager, chatGPTService, botUsername.trim());
            frame.setVisible(true);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            if (session.isRunning()) {
                session.stop();
            }
            surveyManager.shutdown();
            botService.shutdown();
            chatGPTService.shutdown();
        }));
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