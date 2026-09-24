package org.example;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** בניית הבקשה והטיפול בתשובות ובכשלי רשת, מול לקוח HTTP שמחזיר תשובות מוכנות (ללא רשת). */
class ChatGPTServiceTest {
    private static final String ENDPOINT = "https://survey.example.com/api";
    private static final String VALID_JSON =
            "{\"questions\": [{\"text\": \"מה הצבע האהוב?\", \"options\": [\"כחול\", \"ירוק\"]}]}";

    private static ChatGPTService serviceReturning(int code, String body) {
        return serviceWith(chain -> response(chain.request(), code, body));
    }

    private static ChatGPTService serviceWith(Interceptor interceptor) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor).build();
        return new ChatGPTService("secret-token", ENDPOINT, client);
    }

    private static Response response(Request request, int code, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }

    /** כשלי הרשת בבדיקות מכוונים; הלוגר מושתק כדי לא להציף את הפלט במחסניות. */
    private static void withQuietLogger(Runnable action) {
        Logger logger = Logger.getLogger(ChatGPTService.class.getName());
        Level original = logger.getLevel();
        logger.setLevel(Level.OFF);
        try {
            action.run();
        } finally {
            logger.setLevel(original);
        }
    }

    @Test
    void requestCarriesTokenAsQueryParameterAndAuthorizationHeader() throws Exception {
        Request request = new ChatGPTService("secret-token", ENDPOINT, null).buildRequest("חתולים");

        assertEquals("secret-token", request.url().queryParameter("token"));
        assertEquals("Bearer secret-token", request.header("Authorization"));
        assertTrue(request.url().queryParameter("text").contains("חתולים"));
    }

    @Test
    void promptStatesTheLimitsThatTheDomainEnforces() throws Exception {
        String prompt = new ChatGPTService("t", ENDPOINT, null).buildRequest("נושא").url().queryParameter("text");

        assertTrue(prompt.contains("1-" + Survey.MAX_QUESTIONS));
        assertTrue(prompt.contains(Question.MIN_OPTIONS + "-" + Question.MAX_OPTIONS));
    }

    @Test
    void missingTokenBlankTopicAndOversizedTopicAreRejectedBeforeAnyRequest() {
        assertThrows(SurveyGenerationException.class,
                () -> new ChatGPTService(" ", ENDPOINT, null).buildRequest("נושא"));
        assertThrows(SurveyGenerationException.class,
                () -> new ChatGPTService(null, ENDPOINT, null).buildRequest("נושא"));
        assertThrows(SurveyGenerationException.class,
                () -> new ChatGPTService("t", ENDPOINT, null).buildRequest("   "));
        assertThrows(SurveyGenerationException.class,
                () -> new ChatGPTService("t", ENDPOINT, null).buildRequest("א".repeat(AppConfig.MAX_TOPIC_CHARS + 1)));
    }

    @Test
    void invalidEndpointIsRejected() {
        assertThrows(SurveyGenerationException.class,
                () -> new ChatGPTService("t", "not a url", null).buildRequest("נושא"));
    }

    @Test
    void successfulResponseIsParsedIntoQuestions() throws Exception {
        GeneratedSurvey survey = serviceReturning(200, VALID_JSON).generateSurvey("צבעים");

        assertEquals(1, survey.questions().size());
        assertEquals(List.of("כחול", "ירוק"), survey.questions().get(0).getOptions());
    }

    @Test
    void httpErrorIsReportedWithItsStatusCode() {
        SurveyGenerationException error = assertThrows(SurveyGenerationException.class,
                () -> serviceReturning(500, "boom").generateSurvey("צבעים"));

        assertTrue(error.getMessage().contains("500"));
    }

    @Test
    void emptyBodyIsReportedInsteadOfParsed() {
        assertThrows(SurveyGenerationException.class, () -> serviceReturning(200, "  ").generateSurvey("צבעים"));
    }

    @Test
    void networkFailuresAreTranslatedAndKeepTheirCause() {
        withQuietLogger(() -> {
            SurveyGenerationException noInternet = assertThrows(SurveyGenerationException.class,
                    () -> serviceWith(chain -> {
                        throw new UnknownHostException("host");
                    }).generateSurvey("צבעים"));
            SurveyGenerationException refused = assertThrows(SurveyGenerationException.class,
                    () -> serviceWith(chain -> {
                        throw new ConnectException("refused");
                    }).generateSurvey("צבעים"));
            SurveyGenerationException other = assertThrows(SurveyGenerationException.class,
                    () -> serviceWith(chain -> {
                        throw new IOException("reset");
                    }).generateSurvey("צבעים"));

            assertNotNull(noInternet.getCause());
            assertNotNull(refused.getCause());
            assertNotNull(other.getCause());
            assertNotEquals(noInternet.getMessage(), refused.getMessage(), "הודעה נפרדת לכל סוג כשל");
        });
    }
}
