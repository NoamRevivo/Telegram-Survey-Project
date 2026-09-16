package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.HttpUrl;
import java.util.ArrayList;
import java.util.List;

public class ChatGPTService {

    private static final String API_ENDPOINT = "https://shaitest-production-3066.up.railway.app/api-request";
    private static final String TOKEN = System.getenv("SURVEY_API_TOKEN");
    private final OkHttpClient client;

    public ChatGPTService() {
        this.client = new OkHttpClient();
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

        JSONObject json = new JSONObject(responseBody);

        if (json.has("value")) {
            String value = json.getString("value");
            json = new JSONObject(value);
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
}