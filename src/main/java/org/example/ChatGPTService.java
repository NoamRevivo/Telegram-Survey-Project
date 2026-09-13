package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ChatGPTService {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    private final String apiKey;
    private final HttpClient httpClient;

    public ChatGPTService(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<Question> generateSurvey(String topic) throws IOException, InterruptedException {
        JSONObject payload = buildRequestPayload(topic);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("ChatGPT API החזיר שגיאה: " + response.statusCode() + " - " + response.body());
        }
        return parseQuestions(response.body());
    }

    private JSONObject buildRequestPayload(String topic) {
        JSONObject payload = new JSONObject();
        payload.put("model", MODEL);
        payload.put("temperature", 0.7);

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", buildPrompt(topic)));
        payload.put("messages", messages);
        return payload;
    }

    private String buildPrompt(String topic) {
        return "צור סקר קצר בנושא: \"" + topic + "\". "
                + "החזר אך ורק JSON תקני (ללא טקסט נוסף, ללא markdown) במבנה המדויק הבא: "
                + "{\"questions\":[{\"text\":\"...\",\"options\":[\"...\",\"...\"]}]}. "
                + "כמות השאלות חייבת להיות בין 1 ל-3, וכמות האפשרויות לכל שאלה בין 2 ל-4. "
                + "כל הטקסטים בעברית.";
    }

    private List<Question> parseQuestions(String rawApiResponse) {
        JSONObject root = new JSONObject(rawApiResponse);
        String content = root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        JSONObject parsedContent = new JSONObject(content);
        JSONArray questionsArray = parsedContent.getJSONArray("questions");

        List<Question> result = new ArrayList<>();
        for (int i = 0; i < questionsArray.length(); i++) {
            JSONObject questionObject = questionsArray.getJSONObject(i);
            String text = questionObject.getString("text");

            List<String> options = new ArrayList<>();
            JSONArray optionsArray = questionObject.getJSONArray("options");
            for (int j = 0; j < optionsArray.length(); j++) {
                options.add(optionsArray.getString(j));
            }
            result.add(new Question(text, options));
        }
        return result;
    }
}
