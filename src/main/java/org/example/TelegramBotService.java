package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

    public class TelegramBotService extends TelegramLongPollingBot implements CommunityListener, SurveyListener {


        private final String botUsername;
        private final String botToken;
        private final CommunityManager communityManager;
        private final SurveyManager surveyManager;

        public TelegramBotService(String botUsername,
                                  String botToken,
                                  CommunityManager communityManager,
                                  SurveyManager surveyManager) {
            this.botUsername = botUsername;
            this.botToken = botToken;
            this.communityManager = communityManager;
            this.surveyManager = surveyManager;
            this.communityManager.addListener(this);
            this.surveyManager.addListener(this);
        }
        @Override
        public String getBotUsername() {
            return botUsername;
        }
        @Override
        public String getBotToken() {
            return botToken;
        }
        @Override
        public void onUpdateReceived(Update update) {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleIncomingMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        }
        private void handleIncomingMessage(Message message) {
            String text = message.getText().trim();
            if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("היי") || text.equalsIgnoreCase("hi")) {
                User from = message.getFrom();
                boolean added = communityManager.addMember(from.getId(), from.getFirstName(), from.getUserName());
                if (added) {
                    sendText(message.getChatId(), "ברוך הבא לקהילה, " + from.getFirstName() + "!");
                }
            }
        }

        private void handleCallbackQuery(CallbackQuery callbackQuery) {
            long userId = callbackQuery.getFrom().getId();
            String[] parts = callbackQuery.getData().split(":", 2);
            if (parts.length != 2) {
                return;
            }
            String questionId = parts[0];
            String answer = parts[1];

            surveyManager.recordAnswer(userId, questionId, answer);

            AnswerCallbackQuery feedback = new AnswerCallbackQuery();
            feedback.setCallbackQueryId(callbackQuery.getId());
            feedback.setText("תשובתך נקלטה!");
            feedback.setShowAlert(false);
            safeExecute(feedback);
        }
        public void broadcastNewMember(String name, int newSize) {
            for (CommunityUser user : communityManager.getAllMembers()) {
                sendText(user.getTelegramId(), name + " הצטרף/ה לקהילה! (סה\"כ חברים: " + newSize + ")");
            }
        }
        public void sendSurveyToParticipants(Survey survey, List<SurveyParticipant> participants) {
            for (SurveyParticipant participant : participants) {
                long chatId = participant.getUser().getTelegramId();
                for (Question question : survey.getQuestions()) {
                    sendQuestion(chatId, question);
                }
            }
        }
        private void sendQuestion(long chatId, Question question) {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(question.getText());
            message.setReplyMarkup(buildKeyboardFor(question));
            safeExecute(message);
        }
        private InlineKeyboardMarkup buildKeyboardFor(Question question) {
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (String option : question.getOptions()) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(option);
                button.setCallbackData(question.getId() + ":" + option);

                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(button);
                rows.add(row);
            }
            markup.setKeyboard(rows);
            return markup;
        }
        public void sendReminders(List<SurveyParticipant> unfinishedParticipants) {
            for (SurveyParticipant participant : unfinishedParticipants) {
                sendText(participant.getUser().getTelegramId(), "תזכורת: נא לענות על הסקר לפני שהזמן אוזל!");
            }
        }

        private void sendText(long chatId, String text) {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            safeExecute(message);
        }

        private void safeExecute(org.telegram.telegrambots.meta.api.methods.BotApiMethod<?> method) {
            try {
                execute(method);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
            broadcastNewMember(newUser.getFirstName(), newCommunitySize);
        }
        @Override
        public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
            sendSurveyToParticipants(survey, participants);
        }
        @Override
        public void onReminderDue(List<SurveyParticipant> unfinishedParticipants) {
            sendReminders(unfinishedParticipants);
        }
    }

