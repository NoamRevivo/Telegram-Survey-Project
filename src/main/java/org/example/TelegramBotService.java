package org.example;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Duration;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TelegramBotService implements TelegramGateway.UpdateHandler {
    private static final Logger LOG = Logger.getLogger(TelegramBotService.class.getName());
    private static final String COMMAND_START = "/start";
    private static final String COMMAND_HELP = "/help";
    private static final String[] JOIN_ALIASES = {COMMAND_START, "היי", "hi"};
    private static final char COMMAND_PREFIX = '/';
    private static final char BOT_MENTION = '@';

    private final CommunityManager communityManager;
    private final SurveyManager surveyManager;
    private final TelegramGateway gateway;
    private final BotNotifier notifier;

    public TelegramBotService(String botUsername,
                              String botToken,
                              CommunityManager communityManager,
                              SurveyManager surveyManager) {
        this.communityManager = communityManager;
        this.surveyManager = surveyManager;
        this.gateway = new TelegramGateway(botUsername, botToken, this);
        this.notifier = new BotNotifier(gateway, communityManager, surveyManager);
        communityManager.addListener(notifier);
        surveyManager.addSurveyListener(notifier);
    }

    public TelegramGateway gateway() {
        return gateway;
    }

    @Override
    public void onMessage(Message message) {
        Chat chat = message.getChat();
        if (chat == null || !Boolean.TRUE.equals(chat.isUserChat())) {
            return;
        }
        User from = message.getFrom();
        if (from == null) {
            LOG.fine("התקבלה הודעה ללא שולח — מתעלם");
            return;
        }
        String command = commandOf(message.getText());
        long chatId = message.getChatId();

        if (isJoinCommand(command)) {
            boolean added = communityManager.addMember(from.getId(), from.getFirstName(), from.getUserName());
            CommunityUser user = communityManager.getMember(from.getId());
            String displayName = user != null ? user.getFirstName() : safeName(from);
            gateway.sendText(chatId, added
                    ? MessageTemplates.welcome(displayName)
                    : MessageTemplates.alreadyMember(displayName, user == null ? null : user.getJoinedAt()));
        } else if (command.equals(COMMAND_HELP)) {
            gateway.sendText(chatId, MessageTemplates.help());
        } else {
            gateway.sendText(chatId, MessageTemplates.unknownCommand());
        }
    }

    /**
     * פקודת סלאש מגיעה לעיתים עם פרמטר (t.me/Bot?start=abc שולח "/start abc")
     * או עם תיוג הבוט ("/start@BotName") — בשני המקרים הפקודה עצמה היא ההתחלה.
     */
    static String commandOf(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) != COMMAND_PREFIX) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String first = trimmed.split("\\s+", 2)[0];
        int mention = first.indexOf(BOT_MENTION);
        return (mention > 0 ? first.substring(0, mention) : first).toLowerCase(Locale.ROOT);
    }

    private boolean isJoinCommand(String command) {
        for (String alias : JOIN_ALIASES) {
            if (command.equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }

    private static String safeName(User user) {
        String first = user.getFirstName();
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        String userName = user.getUserName();
        return (userName != null && !userName.isBlank()) ? "@" + userName : "חבר/ה";
    }

    /**
     * אישור הלחיצה עובר לתור נפרד וללא ניסיון חוזר: 429 עם retryAfter לא יחסום את חוט ה-polling,
     * ואישור לחיצה שמגיע באיחור ממילא אינו מועיל למשתמש.
     */
    @Override
    public void onCallback(CallbackQuery callbackQuery) {
        AnswerCallbackQuery feedback = new AnswerCallbackQuery();
        feedback.setCallbackQueryId(callbackQuery.getId());
        feedback.setShowAlert(true);
        try {
            feedback.setText(resolveAnswer(callbackQuery, feedback));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "callback לא תקין: " + callbackQuery.getData(), e);
            feedback.setText(MessageTemplates.callbackFailed());
        } finally {
            gateway.runOnAckPool("אישור לחיצה", () -> gateway.sendOnce(feedback));
        }
    }

    private String resolveAnswer(CallbackQuery callbackQuery, AnswerCallbackQuery feedback) {
        User from = callbackQuery.getFrom();
        CallbackData data = CallbackData.parse(callbackQuery.getData());
        if (from == null || data == null) {
            return MessageTemplates.invalidButton();
        }

        Survey survey = surveyManager.getCurrentSurvey();
        if (survey == null) {
            return MessageTemplates.surveyAlreadyOver();
        }
        if (!survey.getId().equals(data.surveyId())) {
            return MessageTemplates.buttonFromOldSurvey();
        }

        SurveyManager.AnswerResult result = surveyManager.recordAnswer(
                data.surveyId(), from.getId(), data.questionIndex(), data.optionIndex());
        if (result == SurveyManager.AnswerResult.RECORDED) {
            markChosenAnswer(callbackQuery, survey, data);
        }
        feedback.setShowAlert(result != SurveyManager.AnswerResult.RECORDED);
        return MessageTemplates.answerFeedback(result);
    }

    private void markChosenAnswer(CallbackQuery callbackQuery, Survey survey, CallbackData data) {
        if (callbackQuery.getMessage() instanceof Message message) {
            Question question = survey.getQuestions().get(data.questionIndex());
            String chosenOption = question.getOptions().get(data.optionIndex());
            notifier.markChosenAnswer(message.getChatId(), message.getMessageId(),
                    question, chosenOption, data.questionIndex(), survey.getQuestions().size());
        }
    }

    public void shutdownGracefully(Duration timeout) {
        gateway.shutdownGracefully(timeout);
    }
}
