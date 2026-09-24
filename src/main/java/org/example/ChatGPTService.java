package org.example;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** לקוח HTTP לשירות יצירת השאלות; בקשה אחת בכל פעם, עם אפשרות ביטול מבחוץ. */
public class ChatGPTService {
    private static final Logger LOG = Logger.getLogger(ChatGPTService.class.getName());
    private final String token;
    private final String endpoint;
    private final OkHttpClient client;
    private final AtomicReference<Call> currentCall = new AtomicReference<>();

    public ChatGPTService(String token, String endpoint) {
        this(token, endpoint, new OkHttpClient.Builder()
                .connectTimeout(AppConfig.API_TIMEOUT)
                .readTimeout(AppConfig.API_TIMEOUT)
                .writeTimeout(AppConfig.API_TIMEOUT)
                .callTimeout(AppConfig.API_CALL_TIMEOUT)
                .build());
    }

    /** הלקוח מוזרק כדי שאפשר יהיה לבדוק את הטיפול בתשובות ובכשלי רשת בלי רשת אמיתית. */
    ChatGPTService(String token, String endpoint, OkHttpClient client) {
        this.token = token;
        this.endpoint = endpoint;
        this.client = client;
    }

    /** שולח את הנושא לשירות ומחזיר את השאלות שפורקו; כל כשל מתורגם ל-{@link SurveyGenerationException} בעברית. */
    public GeneratedSurvey generateSurvey(String topic) throws SurveyGenerationException {
        Request request = buildRequest(topic);
        Call call = client.newCall(request);
        currentCall.set(call);
        if (Thread.currentThread().isInterrupted()) {
            call.cancel();
        }
        try (Response response = call.execute()) {
            // peekBody: קורא לכל היותר MAX_RESPONSE_BYTES, כך ששרת תקול לא יטען לזיכרון תגובה בגודל חופשי
            String responseBody = response.body() == null
                    ? ""
                    : response.peekBody(AppConfig.MAX_RESPONSE_BYTES).string();
            if (!response.isSuccessful()) {
                throw new SurveyGenerationException("שירות יצירת השאלות החזיר שגיאה " + response.code()
                        + ". " + SurveyJsonParser.snippet(responseBody));
            }
            if (responseBody.isBlank()) {
                throw new SurveyGenerationException("השרת החזיר תגובה ריקה. "
                        + "בדוק שכתובת ה-API כוללת את הנתיב המלא ושהטוקן תקין.");
            }
            return SurveyJsonParser.parse(responseBody);
        } catch (IOException e) {
            throw describeNetworkFailure(e, call);
        } finally {
            currentCall.compareAndSet(call, null);
        }
    }

    /**
     * בדיקת הקלט ובניית הבקשה — בלי רשת, ולכן ניתנת לבדיקה ישירה.
     * <p>
     * שירות ה-API (shaitest-production) קורא את הטוקן מפרמטר ה-query בשם token ולא מכותרת
     * ה-Authorization — נבדק אמפירית: בקשה בלי token בכלל מחזירה code 1024, ועם token שגוי
     * code 1029. לכן הטוקן נשלח כאן בשני האופנים: כפרמטר, כדי שהשירות בפועל יזהה אותו, וגם
     * ב-Authorization, כגיבוי אם השירות ישודרג בעתיד לקרוא ממנו.
     */
    Request buildRequest(String topic) throws SurveyGenerationException {
        if (token == null || token.isBlank()) {
            throw new SurveyGenerationException("חסר משתנה הסביבה "
                    + AppConfig.ENV_SURVEY_API_TOKEN + " — לא ניתן ליצור שאלות אוטומטית.");
        }
        if (topic == null || topic.isBlank()) {
            throw new SurveyGenerationException("נושא הסקר לא יכול להיות ריק.");
        }
        String cleanTopic = topic.trim();
        if (cleanTopic.length() > AppConfig.MAX_TOPIC_CHARS) {
            throw new SurveyGenerationException(
                    "נושא הסקר ארוך מדי (עד " + AppConfig.MAX_TOPIC_CHARS + " תווים).");
        }

        HttpUrl baseUrl = HttpUrl.parse(endpoint);
        if (baseUrl == null) {
            throw new SurveyGenerationException("כתובת שירות יצירת השאלות אינה תקינה: " + endpoint);
        }
        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("text", buildPrompt(cleanTopic))
                .addQueryParameter("token", token)
                .build();
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .build();
    }

    /**
     * הודעות השגיאה של OkHttp באנגלית ואינן מובנות למנהל — כל כשל רשת מתורגם כאן לעברית,
     * והחריגה המקורית נשמרת כסיבה וכתובה ללוג.
     */
    private SurveyGenerationException describeNetworkFailure(IOException e, Call call) {
        LOG.log(Level.WARNING, "הפנייה לשירות יצירת השאלות נכשלה", e);
        if (call.isCanceled()) {
            return new SurveyGenerationException("יצירת השאלות בוטלה.", e);
        }
        if (e instanceof UnknownHostException) {
            return new SurveyGenerationException("אין חיבור לאינטרנט או ששרת השאלות אינו זמין.", e);
        }
        if (e instanceof ConnectException) {
            return new SurveyGenerationException(
                    "לא ניתן להתחבר לשרת השאלות. בדוק את כתובת השירות ואת החיבור לרשת.", e);
        }
        if (e instanceof InterruptedIOException) {
            return new SurveyGenerationException("השרת לא הגיב בזמן. נסה שוב בעוד רגע.", e);
        }
        return new SurveyGenerationException("אירעה שגיאת רשת בפנייה לשירות יצירת השאלות. נסה שוב.", e);
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
                + "Each question text must be at most " + Question.MAX_TEXT_LENGTH + " characters.\n"
                + "Return ONLY valid JSON with this format:\n"
                + "{\"questions\": [{\"text\": \"question?\", \"options\": [\"a\", \"b\"]}, ...]}";
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
