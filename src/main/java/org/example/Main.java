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
        ChatGPTService chatGPTService = new ChatGPTService(System.getenv("OPENAI_API_KEY"));

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
        });
        Runtime.getRuntime().addShutdownHook(new Thread(surveyManager::shutdown));
    }
}
