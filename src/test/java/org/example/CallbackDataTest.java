package org.example;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CallbackDataTest {
    @Test
    void roundTripsSurveyIdAndIndexes() {
        Survey survey = new Survey(List.of(new Question("ש", List.of("א", "ב"))), 0);
        String surveyId = survey.getId();

        CallbackData parsed = CallbackData.parse(CallbackData.encode(surveyId, 2, 1));

        assertEquals(new CallbackData(surveyId, 2, 1), parsed);
    }

    @Test
    void malformedDataIsRejected() {
        assertNull(CallbackData.parse(null));
        assertNull(CallbackData.parse(""));
        assertNull(CallbackData.parse("abc"));
        assertNull(CallbackData.parse("abc:1"));
        assertNull(CallbackData.parse("abc:x:1"));
        assertNull(CallbackData.parse("abc:1:y"));
        assertNull(CallbackData.parse(":1:1"));
    }

    @Test
    void negativeAndHugeIndexesParseAndAreLeftToTheManagerToReject() {
        assertEquals(new CallbackData("s", -1, 99), CallbackData.parse("s:-1:99"));
    }
}
