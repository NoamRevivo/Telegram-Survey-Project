package org.example;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R5-M08: הצד היוצא של הבוט — מתרגם אירועים של המנהלים להודעות בטלגרם.
 * הטקסטים מגיעים מ-{@link MessageTemplates} והשליחה מ-{@link TelegramGateway}.
 */
public class BotNotifier implements CommunityListener, SurveyListener {

    private final TelegramGateway gateway;
    private final CommunityManager communityManager;
    private final SurveyManager surveyManager;

    public BotNotifier(TelegramGateway gateway,
                       CommunityManager communityManager,
                       SurveyManager surveyManager) {
        this.gateway = gateway;
        this.communityManager = communityManager;
        this.surveyManager = surveyManager;
    }

    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        gateway.runOnNotificationPool("הודעת הצטרפות של " + newUser.getTelegramId(),
                () -> broadcastNewMember(newUser, newCommunitySize));
    }

    private void broadcastNewMember(CommunityUser newUser, int newSize) {
        for (CommunityUser user : communityManager.getAllMembers()) {
            if (user.getTelegramId() == newUser.getTelegramId()) {
                continue;
            }
            gateway.sendText(user.getTelegramId(),
                    MessageTemplates.newMemberBroadcast(newUser.getFirstName(), newSize));
        }
    }

    /**
     * R5-M15: כל משתתף במשימה נפרדת, והאחרונה שמסתיימת מדווחת למנהל
     * שההפצה הושלמה — רק אז מתחילות 5 הדקות.
     */
    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        if (participants.isEmpty()) {
            surveyManager.markDistributionComplete();
            return;
        }
        AtomicInteger remaining = new AtomicInteger(participants.size());
        for (SurveyParticipant participant : participants) {
            long chatId = participant.getUser().getTelegramId();
            gateway.runOnNotificationPool("שליחת סקר ל-" + chatId, () -> {
                try {
                    sendSurveyTo(chatId, survey);
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        surveyManager.markDistributionComplete();
                    }
                }
            });
        }
    }

    private void sendSurveyTo(long chatId, Survey survey) {
        List<Question> questions = survey.getQuestions();
        gateway.sendText(chatId, MessageTemplates.surveyIntro(questions.size()));
        gateway.sleepMillis(AppConfig.INTRO_DELAY_MILLIS);
        for (int index = 0; index < questions.size(); index++) {
            sendQuestion(chatId, survey.getId(), questions.get(index), index, questions.size());
            gateway.sleepMillis(AppConfig.QUESTION_DELAY_MILLIS);
        }
    }

    private void sendQuestion(long chatId, String surveyId, Question question,
                              int questionIndex, int totalQuestions) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(MessageTemplates.questionHeader(questionIndex, totalQuestions) + question.getText());
        message.setReplyMarkup(buildKeyboardFor(surveyId, question, questionIndex));
        gateway.send(message);
    }

    private InlineKeyboardMarkup buildKeyboardFor(String surveyId, Question question, int questionIndex) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<String> options = question.getOptions();
        for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(options.get(optionIndex));
            // מזהה הסקר ב-callback_data — כפתור של סקר קודם מזוהה ונדחה
            button.setCallbackData(surveyId + ":" + questionIndex + ":" + optionIndex);

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        return markup;
    }

    @Override
    public void onReminderSent(Survey survey, List<SurveyParticipant> notCompleted) {
        gateway.runOnPriorityPool("תזכורות סקר " + survey.getId(), () -> {
            for (SurveyParticipant participant : notCompleted) {
                gateway.sendText(participant.getUser().getTelegramId(),
                        MessageTemplates.reminder(survey, participant));
            }
        });
    }

    @Override
    public void onSurveyClosed(Survey survey, List<SurveyParticipant> participants) {
        int totalQuestions = survey.getQuestions().size();
        gateway.runOnPriorityPool("הודעות סיום לסקר " + survey.getId(), () -> {
            for (SurveyParticipant participant : participants) {
                gateway.sendText(participant.getUser().getTelegramId(),
                        MessageTemplates.surveyClosed(participant, totalQuestions));
            }
        });
    }

    public void markChosenAnswer(Long chatId, Integer messageId, Question question,
                                 String chosenOption, int questionIndex, int totalQuestions) {
        if (chatId == null || messageId == null) {
            return;
        }
        gateway.runOnNotificationPool("סימון תשובה בהודעה " + messageId, () -> {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(MessageTemplates.answeredQuestion(
                    questionIndex, totalQuestions, question, chosenOption));
            gateway.send(edit);
        });
    }
}