package org.example;

/**
 * הנתונים שמוטמעים בכפתור התשובה בטלגרם: מזהה הסקר ואינדקסים בלבד.
 * מזהה UUID (36 תווים) + שני אינדקסים נשארים הרבה מתחת למגבלת 64 הבתים של טלגרם.
 */
record CallbackData(String surveyId, int questionIndex, int optionIndex) {
    static final int MAX_BYTES = 64;
    private static final String SEPARATOR = ":";
    private static final int PARTS = 3;

    static String encode(String surveyId, int questionIndex, int optionIndex) {
        return surveyId + SEPARATOR + questionIndex + SEPARATOR + optionIndex;
    }

    static CallbackData parse(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(SEPARATOR, PARTS);
        if (parts.length != PARTS || parts[0].isBlank()) {
            return null;
        }
        try {
            return new CallbackData(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
