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
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

public class TelegramBotService extends TelegramLongPollingBot implements CommunityListener, SurveyListener {

    private final String botUsername;
    private final String botToken;
    private final CommunityManager communityManager;
    private final SurveyManager surveyManager;
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor();
    private static final Random RANDOM = new Random();
    private static final String[] ALREADY_MEMBER_TEMPLATES = {
            "%s, את/ה כבר איתנו! הצטרפת %s - אין צורך להצטרף שוב 😉",
            "רגע, אני מכיר אותך! %s, כבר חבר/ה בקהילה מאז %s 🎉",
            "%s, הקהילה כבר מכירה אותך (מאז %s) - תודה שאת/ה כאן! 💙",
            "היי שוב %s! כבר סימנתי אותך ברשימה מאז %s - בוא/י נמשיך משם 🚀"
    };

    public TelegramBotService(String botUsername,
                              String botToken,
                              CommunityManager communityManager,
                              SurveyManager surveyManager) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.communityManager = communityManager;
        this.surveyManager = surveyManager;
        this.communityManager.addListener(this);
        this.surveyManager.addSurveyListener(this);
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
            } else {
                sendText(message.getChatId(), alreadyMemberMessage(from));
            }
        }
    }

    private String alreadyMemberMessage(User from) {
        CommunityUser existing = communityManager.getMember(from.getId());
        String sinceText = existing != null ? timeSinceJoined(existing.getJoinedAt()) : "כבר";
        String template = ALREADY_MEMBER_TEMPLATES[RANDOM.nextInt(ALREADY_MEMBER_TEMPLATES.length)];
        return String.format(template, from.getFirstName(), sinceText);
    }

    private String timeSinceJoined(LocalDateTime joinedAt) {
        Duration duration = Duration.between(joinedAt, LocalDateTime.now());
        long days = duration.toDays();
        long hours = duration.toHours();
        long minutes = duration.toMinutes();
        if (days > 0) {
            return "לפני " + days + (days == 1 ? " יום" : " ימים");
        }
        if (hours > 0) {
            return "לפני " + hours + (hours == 1 ? " שעה" : " שעות");
        }
        if (minutes > 0) {
            return "לפני " + minutes + (minutes == 1 ? " דקה" : " דקות");
        }
        return "ממש הרגע";
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        long userId = callbackQuery.getFrom().getId();
        String[] parts = callbackQuery.getData().split(":", 2);

        AnswerCallbackQuery feedback = new AnswerCallbackQuery();
        feedback.setCallbackQueryId(callbackQuery.getId());

        if (parts.length != 2) {
            return;
        }

        int questionIndex;
        int optionIndex;
        try {
            questionIndex = Integer.parseInt(parts[0]);
            optionIndex = Integer.parseInt(parts[1]);
        } catch (NumberFormatException nfe) {
            return;
        }

        Survey survey = surveyManager.getCurrentSurvey();
        if (survey == null || questionIndex < 0 || questionIndex >= survey.getQuestions().size()) {
            feedback.setText("הסקר כבר הסתיים.");
            feedback.setShowAlert(true);
            safeExecute(feedback);
            return;
        }

        Question question = survey.getQuestions().get(questionIndex);
        List<String> options = question.getOptions();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        String answer = options.get(optionIndex);

        SurveyManager.AnswerResult result = surveyManager.recordAnswer(userId, question.getId(), answer);

        feedback.setText(feedbackTextFor(result));
        feedback.setShowAlert(result != SurveyManager.AnswerResult.RECORDED);
        safeExecute(feedback);
    }

    private String feedbackTextFor(SurveyManager.AnswerResult result) {
        switch (result) {
            case RECORDED: return "תשובתך נקלטה!";
            case ALREADY_ANSWERED: return "כבר ענית על שאלה זו.";
            case SURVEY_NOT_ACTIVE: return "הסקר כבר הסתיים.";
            default: return "לא ניתן לקלוט את התשובה.";
        }
    }

    public void broadcastNewMember(long newMemberId, String name, int newSize) {
        for (CommunityUser user : communityManager.getAllMembers()) {
            if (user.getTelegramId() == newMemberId) {
                continue;
            }
            sendText(user.getTelegramId(), name + " הצטרף/ה לקהילה! (סה\"כ חברים: " + newSize + ")");
        }
    }

    public void sendSurveyToParticipants(Survey survey, List<SurveyParticipant> participants) {
        List<Question> questions = survey.getQuestions();
        for (SurveyParticipant participant : participants) {
            long chatId = participant.getUser().getTelegramId();
            for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
                sendQuestion(chatId, questions.get(questionIndex), questionIndex);
                sleepMillis(400);
            }
        }
    }

    private void sendQuestion(long chatId, Question question, int questionIndex) {
        if (isFakeChatId(chatId)) {
            return;
        }
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(question.getText());
        message.setReplyMarkup(buildKeyboardFor(question, questionIndex));
        safeExecute(message);
    }

    private InlineKeyboardMarkup buildKeyboardFor(Question question, int questionIndex) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<String> options = question.getOptions();
        for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(options.get(optionIndex));
            button.setCallbackData(questionIndex + ":" + optionIndex);

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
        if (isFakeChatId(chatId)) {
            return;
        }
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        safeExecute(message);
    }

    private boolean isFakeChatId(long chatId) {
        return chatId < 0;
    }

    private void safeExecute(org.telegram.telegrambots.meta.api.methods.BotApiMethod<?> method) {
        try {
            execute(method);
        } catch (TelegramApiRequestException e) {
            Integer retryAfter = e.getParameters() != null ? e.getParameters().getRetryAfter() : null;
            if (retryAfter != null && retryAfter > 0) {
                System.err.println("הגעה למגבלת קצב טלגרם, ממתין " + retryAfter + " שניות ומנסה שוב...");
                sleepSeconds(retryAfter);
                try {
                    execute(method);
                } catch (TelegramApiException retryEx) {
                    System.err.println("שליחת הודעה נכשלה גם בניסיון החוזר: " + retryEx.getMessage());
                }
            } else {
                System.err.println("שליחת הודעה בטלגרם נכשלה: " + e.getMessage());
            }
        } catch (TelegramApiException e) {
            System.err.println("שליחת הודעה בטלגרם נכשלה: " + e.getMessage());
        }
    }

    private void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepSeconds(int seconds) {
        sleepMillis(seconds * 1000L);
    }

    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        notificationExecutor.submit(() ->
                broadcastNewMember(newUser.getTelegramId(), newUser.getFirstName(), newCommunitySize));
    }

    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        notificationExecutor.submit(() -> sendSurveyToParticipants(survey, participants));
    }

    @Override
    public void onReminderSent(List<SurveyParticipant> notCompleted) {
        notificationExecutor.submit(() -> sendReminders(notCompleted));
    }

    public void shutdown() {
        notificationExecutor.shutdownNow();
    }
}