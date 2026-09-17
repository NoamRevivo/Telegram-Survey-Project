package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.swing.*;

public class Main
{
    public static void main(String[] args) {
        CommunityManager communityManager = new CommunityManager();
        SurveyManager surveyManager = new SurveyManager(communityManager);
        ChatGPTService chatGPTService = new ChatGPTService();
        TelegramBotService botService = new TelegramBotService(
                System.getenv("BOT_USERNAME"),
                System.getenv("BOT_TOKEN"),
                communityManager,
                surveyManager
        );
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(botService);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(communityManager, surveyManager, chatGPTService);
            frame.setVisible(true);
              //  if ("true".equalsIgnoreCase(System.getenv("SEED_TEST_MEMBERS"))) {
                communityManager.addMember(-1L, "בדיקה ראשונה", "test_user_1");
                communityManager.addMember(-2L, "בדיקה שנייה", "test_user_2");
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            surveyManager.shutdown();
            botService.shutdown();
        }));
    }
}