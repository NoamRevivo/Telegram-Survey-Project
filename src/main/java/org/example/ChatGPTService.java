package org.example;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.HttpUrl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatGPTService {

    private static final Logger LOG = Logger.getLogger(ChatGPTService.class.getName());

    private static final String API_ENDPOINT = "https://shaitest-production-3066.up.railway.app/api-request";
    private static final String TOKEN = System.getenv("SURVEY_API_TOKEN");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    /** אורך מקסימלי של קטע מהתגובה שמוצג בהודעת שגיאה */
    private static final int SNIPPET_LENGTH = 200;

    private final OkHttpClient client;

    public ChatGPTService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT)
                .readTimeout(TIMEOUT)
                .writeTimeout(TIMEOUT)
                .build();
    }

    public List<Question> generateSurvey(String topic) throws Exception {
        if (TOKEN == null || TOKEN.isBlank()) {
            throw new IllegalStateException("חסר משתנה הסביבה SURVEY_API_TOKEN — לא ניתן ליצור שאלות אוטומטית.");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("נושא הסקר לא יכול להיות ריק.");
        }

        String prompt = "Create a survey with 1-" + Survey.MAX_QUESTIONS + " questions about: " + topic +
                "\nFor each question, provide " + Question.MIN_OPTIONS + "-" + Question.MAX_OPTIONS
                + " answer options.\n" +
                "Return ONLY valid JSON with this format:\n" +
                "{\"questions\": [{\"text\": \"question?\", \"options\": [\"a\", \"b\"]}, ...]}";

        // כתובת לא תקינה נכשלת כאן עם הודעה ברורה, במקום ב-NullPointerException
        HttpUrl baseUrl = HttpUrl.parse(API_ENDPOINT);
        if (baseUrl == null) {
            throw new IllegalStateException("כתובת שירות יצירת השאלות אינה תקינה: " + API_ENDPOINT);
        }
        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("token", TOKEN)
                .addQueryParameter("text", prompt)
                .build();

        Request req = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(req).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("שירות יצירת השאלות החזיר שגיאה " + response.code()
                        + ". " + snippet(responseBody));
            }
            if (responseBody.isBlank()) {
                // גוף ריק עם סטטוס תקין = כמעט תמיד נתיב endpoint שגוי
                throw new RuntimeException("השרת החזיר תגובה ריקה. "
                        + "בדוק שכתובת ה-API כוללת את הנתיב המלא ושהטוקן תקין.");
            }
            return parseQuestions(responseBody);
        }
    }

    private List<Question> parseQuestions(String responseBody) {
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
            throw new RuntimeException("התשובה מהשירות אינה מכילה שאלות. התקבל: " + snippet(responseBody));
        }
        JSONArray questionsArray = json.getJSONArray("questions");

        for (int i = 0; i < questionsArray.length() && questions.size() < Survey.MAX_QUESTIONS; i++) {
            try {
                questions.add(parseQuestion(questionsArray.getJSONObject(i)));
            } catch (RuntimeException e) {
                // C-07: שאלה פגומה מדולגת ולא מפילה את השאלות התקינות
                LOG.log(Level.FINE, "שאלה " + (i + 1) + " מהשירות דולגה: " + e.getMessage(), e);
            }
        }
        if (questions.isEmpty()) {
            throw new RuntimeException("לא התקבלה אף שאלה תקינה מהשירות.");
        }

        return questions;
    }

    /**
     * בונה שאלה אחת מתוך ה-JSON.
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

    private JSONObject parseJsonObject(String raw) {
        String cleaned = extractJson(raw);
        try {
            return new JSONObject(cleaned);
        } catch (JSONException e) {
            throw new RuntimeException("התגובה מהשרת לא הייתה JSON תקין. תחילת התגובה שהתקבלה: \""
                    + snippet(raw) + "\"", e);
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
        return trimmed.length() > SNIPPET_LENGTH
                ? trimmed.substring(0, SNIPPET_LENGTH) + "..."
                : trimmed;
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}