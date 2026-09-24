package org.example;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class BotNotifier implements CommunityListener, SurveyListener {
    private final MessageSender gateway;
    private final CommunityManager communityManager;
    private final SurveyManager surveyManager;

    public BotNotifier(MessageSender gateway,
                       CommunityManager communityManager,
                       SurveyManager surveyManager) {
        this.gateway = gateway;
        this.communityManager = communityManager;
        this.surveyManager = surveyManager;
    }



    @Override
    public void onMemberAdded(CommunityUser newUser, int newCommunitySize) {
        String text = MessageTemplates.newMemberBroadcast(newUser.getFirstName(), newCommunitySize);
        for (CommunityUser user : communityManager.getAllMembers()) {
            if (user.getTelegramId() == newUser.getTelegramId()) {
                continue;
            }
            long chatId = user.getTelegramId();
            gateway.runOnNotificationPool("הודעת הצטרפות ל-" + chatId, () -> gateway.sendText(chatId, text));
        }
    }


    @Override
    public void onSurveyStarted(Survey survey, List<SurveyParticipant> participants) {
        String surveyId = survey.getId();
        if (participants.isEmpty()) {
            surveyManager.markDistributionComplete(surveyId);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(participants.size());
        for (SurveyParticipant participant : participants) {
            long chatId = participant.getUser().getTelegramId();
            gateway.runOnNotificationPool("שליחת סקר ל-" + chatId, () -> {
                try {
                    deliverSurvey(participant, survey);
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        surveyManager.markDistributionComplete(surveyId);
                    }
                }
            });
        }
    }

    private void deliverSurvey(SurveyParticipant participant, Survey survey) {
        String surveyId = survey.getId();
        long chatId = participant.getUser().getTelegramId();
        switch (sendSurveyTo(participant, survey)) {
            case BLOCKED -> surveyManager.markUnreachable(surveyId, chatId);
            case DELIVERED -> surveyManager.markDeliveryFailed(surveyId, chatId, false);
            case REJECTED, TRANSIENT_FAILURE -> surveyManager.markDeliveryFailed(surveyId, chatId, true);
        }
    }


    private TelegramGateway.SendResult sendSurveyTo(SurveyParticipant participant, Survey survey) {
        if (!participant.tryBeginDelivery()) {
            return TelegramGateway.SendResult.DELIVERED;
        }
        try {
            long chatId = participant.getUser().getTelegramId();
            String surveyId = survey.getId();
            List<Question> questions = survey.getQuestions();
            if (!surveyManager.isActive(surveyId)) {
                return TelegramGateway.SendResult.DELIVERED;
            }
            if (!participant.isIntroDelivered()) {
                TelegramGateway.SendResult intro =
                        gateway.trySendText(chatId, MessageTemplates.surveyIntro(questions.size()));
                if (intro != TelegramGateway.SendResult.DELIVERED) {
                    return intro;
                }
                participant.markIntroDelivered();
                gateway.sleepMillis(AppConfig.INTRO_DELAY_MILLIS);
            }
            for (int index = participant.getQuestionsDelivered(); index < questions.size(); index++) {
                if (!surveyManager.isActive(surveyId)) {
                    return TelegramGateway.SendResult.DELIVERED;
                }
                TelegramGateway.SendResult sent =
                        sendQuestion(chatId, surveyId, questions.get(index), index, questions.size());
                if (sent != TelegramGateway.SendResult.DELIVERED) {
                    return sent;
                }
                participant.markQuestionDelivered();
                gateway.sleepMillis(AppConfig.QUESTION_DELAY_MILLIS);
            }
            return TelegramGateway.SendResult.DELIVERED;
        } finally {
            participant.endDelivery();
        }
    }

    private TelegramGateway.SendResult sendQuestion(long chatId, String surveyId, Question question,
                                                    int questionIndex, int totalQuestions) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(MessageTemplates.questionHeader(questionIndex, totalQuestions) + question.getText());
        message.setReplyMarkup(buildKeyboardFor(surveyId, question, questionIndex));
        return gateway.trySend(message);
    }

    private InlineKeyboardMarkup buildKeyboardFor(String surveyId, Question question, int questionIndex) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<String> options = question.getOptions();
        for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(options.get(optionIndex));
            button.setCallbackData(CallbackData.encode(surveyId, questionIndex, optionIndex));

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        return markup;
    }


    @Override
    public void onReminderSent(Survey survey, List<SurveyParticipant> notCompleted) {
        List<SurveyParticipant> toRemind = new ArrayList<>();
        for (SurveyParticipant participant : notCompleted) {
            if (participant.isDeliveryFailed()) {
                gateway.runOnNotificationPool(
                        "שליחה חוזרת ל-" + participant.getUser().getTelegramId(),
                        () -> deliverSurvey(participant, survey));
            } else {
                toRemind.add(participant);
            }
        }
        if (toRemind.isEmpty()) {
            return;
        }
        gateway.runOnPriorityPool("תזכורות סקר " + survey.getId(), () -> {
            for (SurveyParticipant participant : toRemind) {
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
                if (participant.isUnreachable()) {
                    continue;
                }
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
        gateway.runOnReplyPool("סימון תשובה בהודעה " + messageId, () -> {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(MessageTemplates.answeredQuestion(
                    questionIndex, totalQuestions, question, chosenOption));
            gateway.send(edit);
        });
    }
}
