package org.example;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * R5-M08: הצד הנכנס של הבוט — פקודות ולחיצות על כפתורים.
 * התעבורה ב-{@link TelegramGateway}, הטקסטים ב-{@link MessageTemplates},
 * וההודעות היוצאות ב-{@link BotNotifier}.
 */
public class TelegramBotService implements TelegramGateway.UpdateHandler {

    private static final Logger LOG = Logger.getLogger(TelegramBotService.class.getName());
    private static final String COMMAND_START = "/start";
    private static final String COMMAND_HELP = "/help";
    private static final String[] JOIN_ALIASES = {COMMAND_START, "היי", "hi"};
    private static final int CALLBACK_PARTS = 3;

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

    /** הרכיב שנרשם מול TelegramBotsApi. */
    public TelegramGateway gateway() {
        return gateway;
    }

    /* ===================== הודעות נכנסות ===================== */

    @Override
    public void onMessage(Message message) {
        User from = message.getFrom();
        if (from == null) {
            // R5-C05: הודעה בשם ערוץ או מנהל אנונימי — אין משתמש לצרף
            LOG.fine("התקבלה הודעה ללא שולח — מתעלם");
            return;
        }
        String text = message.getText().trim();
        long chatId = message.getChatId();

        if (isJoinCommand(text)) {
            boolean added = communityManager.addMember(from.getId(), from.getFirstName(), from.getUserName());
            CommunityUser user = communityManager.getMember(from.getId());
            String displayName = user != null ? user.getFirstName() : safeName(from);
            gateway.sendText(chatId, added
                    ? MessageTemplates.welcome(displayName)
                    : MessageTemplates.alreadyMember(displayName, user == null ? null : user.getJoinedAt()));
        } else if (text.equalsIgnoreCase(COMMAND_HELP)) {
            gateway.sendText(chatId, MessageTemplates.help());
        } else {
            gateway.sendText(chatId, MessageTemplates.unknownCommand());
        }
    }

    private boolean isJoinCommand(String text) {
        for (String alias : JOIN_ALIASES) {
            if (text.equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }

    /** R5-C05: שם בטוח גם כשטלגרם לא החזיר firstName — במקום «ברוך הבא, null». */
    private static String safeName(User user) {
        String first = user.getFirstName();
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        String userName = user.getUserName();
        return (userName != null && !userName.isBlank()) ? "@" + userName : "חבר/ה";
    }

    /* ===================== לחיצות על כפתורים ===================== */

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
            gateway.send(feedback);
        }
    }

    private String resolveAnswer(CallbackQuery callbackQuery, AnswerCallbackQuery feedback) {
        User from = callbackQuery.getFrom();
        String data = callbackQuery.getData();
        if (from == null || data == null) {
            return MessageTemplates.invalidButton();
        }
        String[] parts = data.split(":", CALLBACK_PARTS);
        if (parts.length != CALLBACK_PARTS) {
            return MessageTemplates.invalidButton();
        }

        Survey survey = surveyManager.getCurrentSurvey();
        if (survey == null) {
            return MessageTemplates.surveyAlreadyOver();
        }
        if (!survey.getId().equals(parts[0])) {
            return MessageTemplates.buttonFromOldSurvey();
        }

        int questionIndex = parseIndex(parts[1]);
        int optionIndex = parseIndex(parts[2]);
        if (questionIndex < 0 || questionIndex >= survey.getQuestions().size()) {
            return MessageTemplates.invalidButton();
        }
        Question question = survey.getQuestions().get(questionIndex);
        if (optionIndex < 0 || optionIndex >= question.getOptions().size()) {
            return MessageTemplates.invalidButton();
        }

        String chosenOption = question.getOptions().get(optionIndex);
        SurveyManager.AnswerResult result =
                surveyManager.recordAnswer(from.getId(), question.getId(), chosenOption);

        if (result == SurveyManager.AnswerResult.RECORDED) {
            /*
             * R5-M05: getMessage() מחזיר MaybeInaccessibleMessage — הודעה שטלגרם
             * כבר אינו מאפשר לבוט לגשת לתוכנה. רק Message אמיתית ניתנת לעריכה,
             * ורק ממנה אפשר לקחת את מזהה הצ'אט הנכון (ולא את זה של המשתמש).
             */
            if (callbackQuery.getMessage() instanceof Message message) {
                notifier.markChosenAnswer(message.getChatId(), message.getMessageId(),
                        question, chosenOption, questionIndex, survey.getQuestions().size());
            }
        }
        feedback.setShowAlert(result != SurveyManager.AnswerResult.RECORDED);
        return MessageTemplates.answerFeedback(result);
    }

    private int parseIndex(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** R5-M06: כיבוי שאינו קוטע הודעות סיום שנמצאות באוויר. */
    public void shutdownGracefully(Duration timeout) {
        gateway.shutdownGracefully(timeout);
    }
}