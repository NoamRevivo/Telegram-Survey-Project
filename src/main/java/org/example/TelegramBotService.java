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
import java.util.logging.Level;
import java.util.logging.Logger;

public class TelegramBotService extends TelegramLongPollingBot implements CommunityListener, SurveyListener {

    private final String botUsername;
    private final String botToken;
    private final CommunityManager communityManager;
    private final SurveyManager surveyManager;
    private static final Logger LOG = Logger.getLogger(TelegramBotService.class.getName());
    /** C-06: הפצה במקביל ל-4 משתתפים, ותור נפרד לתזכורות ולהודעות סיום */
    private final ExecutorService notificationExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService priorityExecutor = Executors.newSingleThreadExecutor();
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
        } else if (text.equalsIgnoreCase("/help")) {
            sendText(message.getChatId(), "🤖 /start, \"היי\" או \"Hi\" — הצטרפות לקהילה.\n"
                    + "כשנפתח סקר, השאלות יגיעו לכאן עם כפתורי תשובה. יש 5 דקות לענות.");
        } else {
            sendText(message.getChatId(), "לא הבנתי 🙂 שלח/י /start כדי להצטרף לקהילה, או /help לעזרה.");
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
        AnswerCallbackQuery feedback = new AnswerCallbackQuery();
        feedback.setCallbackQueryId(callbackQuery.getId());
        feedback.setShowAlert(true);
        try {
            feedback.setText(resolveAnswer(callbackQuery, feedback));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "callback לא תקין: " + callbackQuery.getData(), e);
            feedback.setText("לא הצלחתי לקלוט את הלחיצה, נסה/י שוב.");
        } finally {
            safeExecute(feedback);   // M-01: תמיד עונים, אחרת המשתמש רואה שעון טעינה
        }
    }

    /** C-02: פורמט ה-callback הוא surveyId:questionIndex:optionIndex */
    private String resolveAnswer(CallbackQuery callbackQuery, AnswerCallbackQuery feedback) {
        String[] parts = callbackQuery.getData().split(":", 3);
        if (parts.length != 3) {
            return "הכפתור לא תקין.";
        }
        int questionIndex = Integer.parseInt(parts[1]);
        int optionIndex = Integer.parseInt(parts[2]);

        Survey survey = surveyManager.getCurrentSurvey();
        if (survey == null) {
            return "הסקר כבר הסתיים.";
        }
        if (!survey.getId().equals(parts[0])) {
            return "הכפתור הזה שייך לסקר קודם שכבר הסתיים.";
        }
        if (questionIndex < 0 || questionIndex >= survey.getQuestions().size()) {
            return "הכפתור לא תקין.";
        }
        Question question = survey.getQuestions().get(questionIndex);
        if (optionIndex < 0 || optionIndex >= question.getOptions().size()) {
            return "הכפתור לא תקין.";
        }
        SurveyManager.AnswerResult result = surveyManager.recordAnswer(
                callbackQuery.getFrom().getId(), question.getId(), question.getOptions().get(optionIndex));
        feedback.setShowAlert(result != SurveyManager.AnswerResult.RECORDED);
        return feedbackTextFor(result);
    }

    private String feedbackTextFor(SurveyManager.AnswerResult result) {
        switch (result) {
            case RECORDED: return "תשובתך נקלטה!";
            case ALREADY_ANSWERED: return "כבר ענית על שאלה זו.";
            case SURVEY_NOT_ACTIVE: return "הסקר כבר הסתיים.";
            case UNKNOWN_PARTICIPANT: return "הצטרפת אחרי שהסקר התחיל — תוכל/י להשתתף בסקר הבא.";
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
            // C-06: כל משתתף במשימה נפרדת — 4 במקביל במקום תור אחד ארוך
            notificationExecutor.submit(() -> {
                for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
                    sendQuestion(chatId, survey.getId(), questions.get(questionIndex), questionIndex);
                    sleepMillis(400);
                }
            });
        }
    }

    private void sendQuestion(long chatId, String surveyId, Question question, int questionIndex) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(question.getText());
        message.setReplyMarkup(buildKeyboardFor(surveyId, question, questionIndex));
        safeExecute(message);
    }

    private InlineKeyboardMarkup buildKeyboardFor(String surveyId, Question question, int questionIndex) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<String> options = question.getOptions();
        for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(options.get(optionIndex));
            button.setCallbackData(surveyId + ":" + questionIndex + ":" + optionIndex);

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
        } catch (TelegramApiRequestException e) {
            Integer retryAfter = e.getParameters() != null ? e.getParameters().getRetryAfter() : null;
            if (retryAfter != null && retryAfter > 0) {
                LOG.warning("הגעה למגבלת קצב טלגרם, ממתין " + retryAfter + " שניות ומנסה שוב...");
                sleepSeconds(retryAfter);
                try {
                    execute(method);
                } catch (TelegramApiException retryEx) {
                    LOG.log(Level.WARNING, "שליחת הודעה נכשלה גם בניסיון החוזר", retryEx);
                }
            } else {
                LOG.log(Level.WARNING, "שליחת הודעה בטלגרם נכשלה", e);
            }
        } catch (TelegramApiException e) {
            LOG.log(Level.WARNING, "שליחת הודעה בטלגרם נכשלה", e);
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
        sendSurveyToParticipants(survey, participants);
    }

    @Override
    public void onReminderSent(List<SurveyParticipant> notCompleted) {
        priorityExecutor.submit(() -> sendReminders(notCompleted));
    }

    /** M-11: הודעת סיום לכל המשתתפים */
    @Override
    public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
        priorityExecutor.submit(() -> {
            for (SurveyParticipant p : participants) {
                String text = p.isCompleted()
                        ? "✅ הסקר הסתיים — תודה על ההשתתפות! 🙏"
                        : "🔒 הסקר נסגר. ענית על " + p.getAnsweredQuestionsCount() + " מתוך "
                          + survey.getQuestions().size() + " שאלות.";
                sendText(p.getUser().getTelegramId(), text);
            }
        });
    }

    public void shutdown() {
        notificationExecutor.shutdownNow();
        priorityExecutor.shutdownNow();
    }
}