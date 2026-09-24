package org.example;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * כל הטקסטים של המערכת במקום אחד — הבוט מטפל בתעבורה, לא בניסוח.
 */
public final class MessageTemplates {
    private static final String[] ALREADY_MEMBER_TEMPLATES = {
            "%s, את/ה כבר איתנו! הצטרפת %s - אין צורך להצטרף שוב 😉",
            "רגע, אני מכיר אותך! %s, כבר חבר/ה בקהילה מאז %s 🎉",
            "%s, הקהילה כבר מכירה אותך (מאז %s) - תודה שאת/ה כאן! 💙",
            "היי שוב %s! כבר סימנתי אותך ברשימה מאז %s - בוא/י נמשיך משם 🚀"
    };

    private MessageTemplates() {
    }

    public static String welcome(String displayName) {
        return "ברוכ/ה הבא/ה לקהילה, " + displayName + "!";
    }

    /** בוחר ניסוח אקראי; ThreadLocalRandom במקום Random משותף לכל חוטי הבוט. */
    public static String alreadyMember(String displayName, LocalDateTime joinedAt) {
        String since = joinedAt == null ? "כבר" : timeSinceJoined(joinedAt);
        String template = ALREADY_MEMBER_TEMPLATES[
                ThreadLocalRandom.current().nextInt(ALREADY_MEMBER_TEMPLATES.length)];
        return String.format(template, displayName, since);
    }

    public static String help() {
        return "🤖 /start, \"היי\" או \"Hi\" — הצטרפות לקהילה.\n"
                + "כשנפתח סקר, השאלות יגיעו לכאן עם כפתורי תשובה.\n"
                + "⏱ יש " + formatDuration(AppConfig.SURVEY_DURATION_SECONDS) + " לענות על כל השאלות.\n"
                + "🔔 אם לא תספיק/י לענות על הכל, תישלח לך תזכורת אישית אחת עם השאלות שנשארו.";
    }

    public static String unknownCommand() {
        return "לא הבנתי 🙂 שלח/י /start כדי להצטרף לקהילה, או /help לעזרה.";
    }

    public static String newMemberBroadcast(String displayName, int newSize) {
        return displayName + " הצטרף/ה לקהילה! (סה\"כ חברים: " + newSize + ")";
    }

    public static String joinToast(CommunityUser user) {
        return "🎉 " + user.getFirstName() + " הצטרף/ה לקהילה!";
    }

    public static String surveyIntro(int questionCount) {
        return "📊 נפתח סקר חדש!\n"
                + "❓ מספר שאלות: " + questionCount + "\n"
                + "⏱ יש " + formatDuration(AppConfig.SURVEY_DURATION_SECONDS) + " לענות.\n"
                + "השאלות מגיעות עכשיו 👇";
    }

    public static String questionHeader(int questionIndex, int totalQuestions) {
        return "❓ שאלה " + (questionIndex + 1) + " מתוך " + totalQuestions + "\n\n";
    }

    public static String answeredQuestion(int questionIndex, int totalQuestions,
                                          Question question, String chosenOption) {
        return questionHeader(questionIndex, totalQuestions) + question.getText()
                + "\n\n✅ התשובה שלך: " + chosenOption;
    }

    public static String surveyClosed(SurveyParticipant participant, int totalQuestions) {
        return participant.isCompleted()
                ? "✅ הסקר הסתיים — תודה על ההשתתפות! 🙏"
                : "🔒 הסקר נסגר. ענית על " + participant.getAnsweredQuestionsCount()
                  + " מתוך " + totalQuestions + " שאלות.";
    }

    public static String reminder(Survey survey, SurveyParticipant participant) {
        List<Integer> missing = unansweredQuestionNumbers(survey, participant);

        StringBuilder text = new StringBuilder();
        text.append("🔔 תזכורת: הסקר ייסגר בקרוב.\n\n");

        if (missing.isEmpty()) {
            text.append("ענית על כל השאלות — תודה רבה! 🙏");
            return text.toString();
        }
        if (missing.size() == 1) {
            text.append("נותרה לך שאלה ").append(missing.get(0)).append(" שטרם ענית עליה.");
        } else {
            text.append("טרם ענית על ").append(missing.size()).append(" שאלות: ")
                    .append(formatQuestionNumbers(missing)).append('.');
        }
        text.append("\n👆 גלול/י למעלה אל השאלה ולחץ/י על אחת מאפשרויות התשובה.");
        return text.toString();
    }

    public static String answerFeedback(SurveyManager.AnswerResult result) {
        switch (result) {
            case RECORDED:
                return "תשובתך נקלטה!";
            case ALREADY_ANSWERED:
                return "כבר ענית על שאלה זו.";
            case SURVEY_NOT_ACTIVE:
                return "הסקר כבר הסתיים.";
            case UNKNOWN_PARTICIPANT:
                return "הצטרפת אחרי שהסקר התחיל — תוכל/י להשתתף בסקר הבא.";
            case INVALID_ANSWER:
                return invalidButton();
            default:
                return "לא ניתן לקלוט את התשובה.";
        }
    }

    public static String invalidButton() {
        return "הכפתור לא תקין.";
    }

    public static String surveyAlreadyOver() {
        return "הסקר כבר הסתיים.";
    }

    public static String buttonFromOldSurvey() {
        return "הכפתור הזה שייך לסקר קודם שכבר הסתיים.";
    }

    public static String callbackFailed() {
        return "לא הצלחתי לקלוט את הלחיצה, נסה/י שוב.";
    }

    public static String formatDuration(int totalSeconds) {
        int minutes = totalSeconds / 60;
        if (minutes <= 0) {
            return totalSeconds + " שניות";
        }
        return minutes == 1 ? "דקה אחת" : minutes + " דקות";
    }

    private static List<Integer> unansweredQuestionNumbers(Survey survey, SurveyParticipant participant) {
        List<Integer> missing = new ArrayList<>();
        List<Question> questions = survey.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            if (!participant.hasAnswered(questions.get(i).getId())) {
                missing.add(i + 1);
            }
        }
        return missing;
    }

    private static String formatQuestionNumbers(List<Integer> numbers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numbers.size(); i++) {
            if (i > 0) {
                sb.append(i == numbers.size() - 1 ? " ו-" : ", ");
            }
            sb.append(numbers.get(i));
        }
        return sb.toString();
    }

    private static String timeSinceJoined(LocalDateTime joinedAt) {
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
}