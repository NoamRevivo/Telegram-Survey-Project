package org.example;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatGPTService {

    private static final Logger LOG = Logger.getLogger(ChatGPTService.class.getName());

    /** R5-M04: הטוקן והכתובת מוזרקים — המחלקה ניתנת לבדיקה מול שרת דמה */
    private final String token;
    private final String endpoint;
    private final OkHttpClient client;
    /**
     * R6-M01: הקריאה הפעילה, כדי שביטול מהמשתמש יוכל לנתק בפועל את חיבור ה-HTTP
     * (SwingWorker.cancel(true) בלבד אינו עוצר Socket חוסם — רק Call.cancel() של OkHttp עושה זאת).
     */
    private volatile Call currentCall;

    public ChatGPTService(String token, String endpoint) {
        this.token = token;
        this.endpoint = endpoint;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(AppConfig.API_TIMEOUT)
                .readTimeout(AppConfig.API_TIMEOUT)
                .writeTimeout(AppConfig.API_TIMEOUT)
                .build();
    }

    public List<Question> generateSurvey(String topic) throws SurveyGenerationException {
        if (token == null || token.isBlank()) {
            throw new SurveyGenerationException("חסר משתנה הסביבה "
                    + AppConfig.ENV_SURVEY_API_TOKEN + " — לא ניתן ליצור שאלות אוטומטית.");
        }
        if (topic == null || topic.isBlank()) {
            throw new SurveyGenerationException("נושא הסקר לא יכול להיות ריק.");
        }

        HttpUrl baseUrl = HttpUrl.parse(endpoint);
        if (baseUrl == null) {
            throw new SurveyGenerationException("כתובת שירות יצירת השאלות אינה תקינה: " + endpoint);
        }
        // R5-M04: הטוקן עובר ב-header ולא ב-URL — פרמטרים ב-URL נרשמים בלוגים של שרתים ופרוקסי
        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("text", buildPrompt(topic))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .build();
        LOG.info("DEBUG: URL=" + url + " | token length=" + (token == null ? "null" : token.length())
                + " | token last4=" + (token != null && token.length() >= 4 ? token.substring(token.length() - 4) : token));
        Call call = client.newCall(request);
        currentCall = call;
        try (Response response = call.execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new SurveyGenerationException("שירות יצירת השאלות החזיר שגיאה " + response.code()
                        + ". " + snippet(responseBody));
            }
            if (responseBody.isBlank()) {
                // גוף ריק עם סטטוס תקין = כמעט תמיד נתיב endpoint שגוי
                throw new SurveyGenerationException("השרת החזיר תגובה ריקה. "
                        + "בדוק שכתובת ה-API כוללת את הנתיב המלא ושהטוקן תקין.");
            }
            return parseQuestions(responseBody);
        } catch (IOException e) {
            // R5-M14 / R6-M01: ביטול מצד המשתמש מגיע לכאן כ-IOException (מ-call.cancel())
            throw new SurveyGenerationException("הפנייה לשירות יצירת השאלות נכשלה: " + e.getMessage(), e);
        } finally {
            currentCall = null;
        }
    }

    /**
     * R6-M01: מבטל בפועל את חיבור ה-HTTP הפעיל (אם קיים). ניתן לקריאה מכל חוט —
     * למשל מה-EDT כשהמשתמש לוחץ "בטל יצירה" בזמן ש-doInBackground חוסם על השקע.
     */
    public void cancelCurrentRequest() {
        Call call = currentCall;
        if (call != null) {
            call.cancel();
        }
    }

    private String buildPrompt(String topic) {
        return "Create a survey with 1-" + Survey.MAX_QUESTIONS + " questions about: " + topic
                + "\nFor each question, provide " + Question.MIN_OPTIONS + "-" + Question.MAX_OPTIONS
                + " answer options.\n"
                + "Return ONLY valid JSON with this format:\n"
                + "{\"questions\": [{\"text\": \"question?\", \"options\": [\"a\", \"b\"]}, ...]}";
    }

    private List<Question> parseQuestions(String responseBody) throws SurveyGenerationException {
        List<Question> questions = new ArrayList<>();
        JSONObject json = parseJsonObject(responseBody);

        // השירות עוטף לפעמים את ה-JSON בשדה "value" — כמחרוזת או כאובייקט
        Object wrapped = json.opt("value");
        if (wrapped instanceof JSONObject) {
            json = (JSONObject) wrapped;
        } else if (wrapped instanceof String) {
            json = parseJsonObject((String) wrapped);
        }

        if (!json.has("questions")) {
            throw new SurveyGenerationException(
                    "התשובה מהשירות אינה מכילה שאלות. התקבל: " + snippet(responseBody));
        }
        JSONArray questionsArray = json.getJSONArray("questions");

        for (int i = 0; i < questionsArray.length() && questions.size() < Survey.MAX_QUESTIONS; i++) {
            try {
                questions.add(parseQuestion(questionsArray.getJSONObject(i)));
            } catch (RuntimeException e) {
                // שאלה פגומה מדולגת ולא מפילה את השאלות התקינות
                LOG.log(Level.FINE, "שאלה " + (i + 1) + " מהשירות דולגה: " + e.getMessage(), e);
            }
        }
        if (questions.isEmpty()) {
            throw new SurveyGenerationException("לא התקבלה אף שאלה תקינה מהשירות.");
        }
        return questions;
    }

    /**
     * ChatGPT מחזיר לעיתים יותר אפשרויות מהמותר או אפשרויות כפולות —
     * במקום לפסול את השאלה כולה, מנקים ומקצצים למה שתקין.
     */
    private Question parseQuestion(JSONObject questionJson) {
        String text = String.valueOf(questionJson.get("text")).trim();

        JSONArray optionsArray = questionJson.getJSONArray("options");
        List<String> options = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int j = 0; j < optionsArray.length() && options.size() < Question.MAX_OPTIONS; j++) {
            String option = String.valueOf(optionsArray.get(j)).trim();
            if (!option.isEmpty() && seen.add(option.toLowerCase())) {
                options.add(option);
            }
        }
        return new Question(text, options);
    }

    private JSONObject parseJsonObject(String raw) throws SurveyGenerationException {
        String cleaned = extractJson(raw);
        try {
            return new JSONObject(cleaned);
        } catch (JSONException e) {
            throw new SurveyGenerationException(
                    "התגובה מהשרת לא הייתה JSON תקין. תחילת התגובה שהתקבלה: \"" + snippet(raw) + "\"", e);
        }
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline != -1) {
                text = text.substring(firstNewline + 1);
            }
            int lastFence = text.lastIndexOf("```");
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

    private String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "(תגובה ריקה)";
        }
        String trimmed = text.trim();
        return trimmed.length() > AppConfig.RESPONSE_SNIPPET_LENGTH
                ? trimmed.substring(0, AppConfig.RESPONSE_SNIPPET_LENGTH) + "..."
                : trimmed;
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}