package org.example;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyJsonParserTest {
    private static final String TWO_QUESTIONS =
            "{\"questions\": ["
                    + "{\"text\": \"מה הצבע האהוב?\", \"options\": [\"כחול\", \"ירוק\"]},"
                    + "{\"text\": \"מה המשקה?\", \"options\": [\"קפה\", \"תה\", \"מים\"]}]}";

    @Test
    void parsesPlainJson() throws Exception {
        GeneratedSurvey survey = SurveyJsonParser.parse(TWO_QUESTIONS);

        assertEquals(2, survey.questions().size());
        assertEquals(0, survey.skipped());
        assertEquals("מה הצבע האהוב?", survey.questions().get(0).getText());
        assertEquals(3, survey.questions().get(1).getOptions().size());
    }

    @Test
    void stripsMarkdownCodeFence() throws Exception {
        GeneratedSurvey survey = SurveyJsonParser.parse("```json\n" + TWO_QUESTIONS + "\n```");

        assertEquals(2, survey.questions().size());
    }

    @Test
    void unwrapsValueObject() throws Exception {
        GeneratedSurvey survey = SurveyJsonParser.parse("{\"value\": " + TWO_QUESTIONS + "}");

        assertEquals(2, survey.questions().size());
    }

    @Test
    void unwrapsValueThatIsAnEscapedJsonString() throws Exception {
        String escaped = TWO_QUESTIONS.replace("\"", "\\\"");

        GeneratedSurvey survey = SurveyJsonParser.parse("{\"value\": \"" + escaped + "\"}");

        assertEquals(2, survey.questions().size());
    }

    @Test
    void nullTextIsSkippedInsteadOfBecomingTheWordNull() throws Exception {
        String body = "{\"questions\": ["
                + "{\"text\": null, \"options\": [\"א\", \"ב\"]},"
                + "{\"text\": \"שאלה תקינה\", \"options\": [\"כן\", \"לא\"]}]}";

        GeneratedSurvey survey = SurveyJsonParser.parse(body);

        assertEquals(1, survey.questions().size());
        assertEquals(1, survey.skipped());
        assertEquals("שאלה תקינה", survey.questions().get(0).getText());
    }

    @Test
    void numericTextIsSkipped() throws Exception {
        String body = "{\"questions\": ["
                + "{\"text\": 42, \"options\": [\"א\", \"ב\"]},"
                + "{\"text\": \"שאלה תקינה\", \"options\": [\"כן\", \"לא\"]}]}";

        assertEquals(1, SurveyJsonParser.parse(body).skipped());
    }

    @Test
    void tooLongQuestionIsSkipped() throws Exception {
        String longText = "א".repeat(Question.MAX_TEXT_LENGTH + 1);
        String body = "{\"questions\": ["
                + "{\"text\": \"" + longText + "\", \"options\": [\"א\", \"ב\"]},"
                + "{\"text\": \"קצרה\", \"options\": [\"כן\", \"לא\"]}]}";

        GeneratedSurvey survey = SurveyJsonParser.parse(body);

        assertEquals(1, survey.questions().size());
        assertEquals(1, survey.skipped());
    }

    @Test
    void nullAndDuplicateOptionsAreDropped() throws Exception {
        String body = "{\"questions\": [{\"text\": \"שאלה\", "
                + "\"options\": [\"כן\", null, \"  \", \"כן\", \"לא\"]}]}";

        assertEquals(List.of("כן", "לא"),
                SurveyJsonParser.parse(body).questions().get(0).getOptions());
    }

    @Test
    void questionWithTooFewOptionsAfterCleaningIsSkipped() throws Exception {
        String body = "{\"questions\": ["
                + "{\"text\": \"בעייתית\", \"options\": [\"כן\", \"כן\"]},"
                + "{\"text\": \"תקינה\", \"options\": [\"כן\", \"לא\"]}]}";

        GeneratedSurvey survey = SurveyJsonParser.parse(body);

        assertEquals(1, survey.questions().size());
        assertEquals(1, survey.skipped());
    }

    @Test
    void keepsAtMostTheMaximumNumberOfQuestions() throws Exception {
        StringBuilder body = new StringBuilder("{\"questions\": [");
        for (int i = 1; i <= 5; i++) {
            body.append(i > 1 ? "," : "")
                    .append("{\"text\": \"שאלה ").append(i).append("\", \"options\": [\"א\", \"ב\"]}");
        }
        body.append("]}");

        GeneratedSurvey survey = SurveyJsonParser.parse(body.toString());

        assertEquals(Survey.MAX_QUESTIONS, survey.questions().size());
        assertEquals(0, survey.skipped());
    }

    @Test
    void failsWhenNoQuestionIsValid() {
        String body = "{\"questions\": [{\"text\": null, \"options\": [\"א\", \"ב\"]}]}";

        assertThrows(SurveyGenerationException.class, () -> SurveyJsonParser.parse(body));
    }

    @Test
    void failsWithSpecificMessageWhenServiceReportsMissingToken() {
        String body = "{\"error\":true,\"code\":1024,\"logout\":false,\"language\":0,\"retryable\":false}";

        SurveyGenerationException error = assertThrows(SurveyGenerationException.class,
                () -> SurveyJsonParser.parse(body));

        assertTrue(error.getMessage().contains("1024"));
        assertTrue(error.getMessage().contains("טוקן"));
    }

    @Test
    void failsWithSpecificMessageWhenServiceReportsInvalidToken() {
        String body = "{\"error\":true,\"code\":1029}";

        SurveyGenerationException error = assertThrows(SurveyGenerationException.class,
                () -> SurveyJsonParser.parse(body));

        assertTrue(error.getMessage().contains("1029"));
        assertTrue(error.getMessage().contains("אינו תקין"));
    }

    @Test
    void failsWithGenericMessageOnUnknownErrorCode() {
        String body = "{\"error\":true,\"code\":9999}";

        SurveyGenerationException error = assertThrows(SurveyGenerationException.class,
                () -> SurveyJsonParser.parse(body));

        assertTrue(error.getMessage().contains("9999"));
    }

    @Test
    void failsWhenQuestionsKeyIsMissing() {
        SurveyGenerationException error = assertThrows(SurveyGenerationException.class,
                () -> SurveyJsonParser.parse("{\"answer\": \"hello\"}"));

        assertTrue(error.getMessage().contains("אינה מכילה שאלות"));
    }

    @Test
    void failsWhenQuestionsIsNotAnArray() {
        assertThrows(SurveyGenerationException.class,
                () -> SurveyJsonParser.parse("{\"questions\": \"none\"}"));
    }

    @Test
    void failsWithReadableMessageOnPlainText() {
        SurveyGenerationException error = assertThrows(SurveyGenerationException.class,
                () -> SurveyJsonParser.parse("Sorry, I cannot help with that."));

        assertTrue(error.getMessage().contains("JSON תקין"));
    }

    @Test
    void extractJsonHandlesNullAndSurroundingText() {
        assertEquals("", SurveyJsonParser.extractJson(null));
        assertEquals("{\"a\": 1}", SurveyJsonParser.extractJson("Here you go: {\"a\": 1} enjoy"));
    }

    @Test
    void snippetTruncatesLongResponses() {
        String snippet = SurveyJsonParser.snippet("x".repeat(AppConfig.RESPONSE_SNIPPET_LENGTH + 50));

        assertTrue(snippet.endsWith("..."));
        assertFalse(snippet.length() > AppConfig.RESPONSE_SNIPPET_LENGTH + 3);
        assertEquals("(תגובה ריקה)", SurveyJsonParser.snippet("  "));
    }
}
