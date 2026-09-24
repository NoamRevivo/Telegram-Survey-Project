package org.example;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * פירוק תשובת שירות יצירת השאלות — טהור, ללא HTTP, ולכן ניתן לבדיקה ישירה.
 */
final class SurveyJsonParser {
    private static final Logger LOG = Logger.getLogger(SurveyJsonParser.class.getName());
    private static final String CODE_FENCE = "```";
    private static final String KEY_WRAPPER = "value";
    private static final String KEY_QUESTIONS = "questions";
    private static final String KEY_TEXT = "text";
    private static final String KEY_OPTIONS = "options";
    private static final String KEY_ERROR = "error";
    private static final String KEY_CODE = "code";
    private static final int ERROR_CODE_MISSING_TOKEN = 1024;
    private static final int ERROR_CODE_INVALID_TOKEN = 1029;

    private SurveyJsonParser() {
    }

    static GeneratedSurvey parse(String responseBody) throws SurveyGenerationException {
        JSONObject json = parseJsonObject(responseBody);
        Object wrapped = json.opt(KEY_WRAPPER);
        if (wrapped instanceof JSONObject) {
            json = (JSONObject) wrapped;
        } else if (wrapped instanceof String) {
            json = parseJsonObject((String) wrapped);
        }

        if (json.optBoolean(KEY_ERROR, false)) {
            throw serviceErrorException(json, responseBody);
        }

        JSONArray questionsArray = json.optJSONArray(KEY_QUESTIONS);
        if (questionsArray == null) {
            throw new SurveyGenerationException(
                    "התשובה מהשירות אינה מכילה שאלות. התקבל: " + snippet(responseBody));
        }

        List<Question> questions = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < questionsArray.length() && questions.size() < Survey.MAX_QUESTIONS; i++) {
            try {
                questions.add(parseQuestion(questionsArray.getJSONObject(i)));
            } catch (RuntimeException e) {
                skipped++;
                LOG.log(Level.WARNING, "שאלה " + (i + 1) + " מהשירות דולגה: " + e.getMessage());
            }
        }
        if (questions.isEmpty()) {
            throw new SurveyGenerationException("לא התקבלה אף שאלה תקינה מהשירות.");
        }
        return new GeneratedSurvey(questions, skipped);
    }

    /**
     * שדה text חסר, null או מספר זורק חריגה והשאלה נפסלת —
     * String.valueOf היה הופך null למחרוזת «null» ומציג אותה למנהל כשאלה.
     */
    private static Question parseQuestion(JSONObject questionJson) {
        String text = questionJson.getString(KEY_TEXT).trim();
        JSONArray optionsArray = questionJson.getJSONArray(KEY_OPTIONS);
        List<String> options = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int j = 0; j < optionsArray.length() && options.size() < Question.MAX_OPTIONS; j++) {
            String option = optionsArray.optString(j, "").trim();
            if (option.isEmpty()) {
                continue;
            }
            String shortened = Question.truncateOption(option);
            if (seen.add(Question.optionKey(shortened))) {
                options.add(shortened);
            }
        }
        return new Question(text, options);
    }

    /**
     * השירות מחזיר לעיתים HTTP 200 עם מעטפת שגיאה משלו ({"error": true, "code": N, ...})
     * במקום שאלות. קוד 1024 נבדק אמפירית כ"חסר טוקן בבקשה" וקוד 1029 כ"טוקן שגוי" —
     * שני הקודים האלה מתורגמים להודעה מפורשת, וכל קוד אחר מקבל הודעה כללית עם הקוד עצמו.
     */
    private static SurveyGenerationException serviceErrorException(JSONObject json, String responseBody) {
        int code = json.optInt(KEY_CODE, -1);
        String reason = switch (code) {
            case ERROR_CODE_MISSING_TOKEN -> "השירות לא קיבל טוקן. בדוק שמשתנה הסביבה "
                    + AppConfig.ENV_SURVEY_API_TOKEN + " מוגדר ושכתובת השירות תקינה.";
            case ERROR_CODE_INVALID_TOKEN -> "הטוקן שנשלח לשירות אינו תקין. בדוק את הערך של משתנה הסביבה "
                    + AppConfig.ENV_SURVEY_API_TOKEN + ".";
            default -> "בדוק את משתנה הסביבה " + AppConfig.ENV_SURVEY_API_TOKEN + " ואת כתובת השירות.";
        };
        return new SurveyGenerationException(
                "השירות החזיר שגיאה (קוד " + code + "). " + reason + " התקבל: " + snippet(responseBody));
    }

    private static JSONObject parseJsonObject(String raw) throws SurveyGenerationException {
        String cleaned = extractJson(raw);
        try {
            return new JSONObject(cleaned);
        } catch (JSONException e) {
            throw new SurveyGenerationException(
                    "התגובה מהשרת לא הייתה JSON תקין. תחילת התגובה שהתקבלה: \"" + snippet(raw) + "\"", e);
        }
    }

    static String extractJson(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith(CODE_FENCE)) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline != -1) {
                text = text.substring(firstNewline + 1);
            }
            int lastFence = text.lastIndexOf(CODE_FENCE);
            if (lastFence != -1) {
                text = text.substring(0, lastFence);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return text;
    }

    static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "(תגובה ריקה)";
        }
        String trimmed = text.trim();
        return trimmed.length() > AppConfig.RESPONSE_SNIPPET_LENGTH
                ? trimmed.substring(0, AppConfig.RESPONSE_SNIPPET_LENGTH) + "..."
                : trimmed;
    }
}
