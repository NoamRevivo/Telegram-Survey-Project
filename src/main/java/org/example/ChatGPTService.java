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
import java.util.List;

public class ChatGPTService {

    private static final String API_ENDPOINT = "https://shaitest-production-3066.up.railway.app/api-request";
    private static final String TOKEN = System.getenv("SURVEY_API_TOKEN");
    private final OkHttpClient client;

    public ChatGPTService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    public List<Question> generateSurvey(String topic) throws Exception {
        String prompt = "Create a survey with 1-3 questions about: " + topic +
                "\nFor each question, provide 2-4 answer options.\n" +
                "Return ONLY valid JSON with this format:\n" +
                "{\"questions\": [{\"text\": \"question?\", \"options\": [\"a\", \"b\"]}, ...]}";

        HttpUrl url = HttpUrl.parse(API_ENDPOINT).newBuilder()
                .addQueryParameter("token", TOKEN)
                .addQueryParameter("text", prompt)
                .build();

        Request req = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(req).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("API Error: " + response.code());
            }

            String responseBody = response.body().string();
            return parseQuestions(responseBody);
        }
    }

    private List<Question> parseQuestions(String responseBody) {
        List<Question> questions = new ArrayList<>();

        JSONObject json = parseJsonObject(responseBody);

        if (json.has("value")) {
            String value = json.getString("value");
            json = parseJsonObject(value);
        }

        JSONArray questionsArray = json.getJSONArray("questions");

        for (int i = 0; i < questionsArray.length(); i++) {
            JSONObject q = questionsArray.getJSONObject(i);
            String text = q.getString("text");

            List<String> options = new ArrayList<>();
            JSONArray optionsArray = q.getJSONArray("options");
            for (int j = 0; j < optionsArray.length(); j++) {
                options.add(optionsArray.getString(j));
            }

            questions.add(new Question(text, options));
        }

        return questions;
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
        if (text == null || text.isEmpty()) {
            return "(תגובה ריקה)";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}