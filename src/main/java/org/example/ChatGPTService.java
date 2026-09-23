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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatGPTService {
    private static final Logger LOG = Logger.getLogger(ChatGPTService.class.getName());
    private final String token;
    private final String endpoint;
    private final OkHttpClient client;
    private final AtomicReference<Call> currentCall = new AtomicReference<>();

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
        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("text", buildPrompt(topic))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .build();
        Call call = client.newCall(request);
        currentCall.set(call);
        if (Thread.currentThread().isInterrupted()) {
            call.cancel();
        }
        try (Response response = call.execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new SurveyGenerationException("שירות יצירת השאלות החזיר שגיאה " + response.code()
                        + ". " + snippet(responseBody));
            }
            if (responseBody.isBlank()) {
                throw new SurveyGenerationException("השרת החזיר תגובה ריקה. "
                        + "בדוק שכתובת ה-API כוללת את הנתיב המלא ושהטוקן תקין.");
            }
            return parseQuestions(responseBody);
        } catch (IOException e) {
            throw new SurveyGenerationException("הפנייה לשירות יצירת השאלות נכשלה: " + e.getMessage(), e);
        } finally {
            currentCall.compareAndSet(call, null);
        }
    }

    public void cancelCurrentRequest() {
        Call call = currentCall.get();
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
                LOG.log(Level.FINE, "שאלה " + (i + 1) + " מהשירות דולגה: " + e.getMessage(), e);
            }
        }
        if (questions.isEmpty()) {
            throw new SurveyGenerationException("לא התקבלה אף שאלה תקינה מהשירות.");
        }
        return questions;
    }

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