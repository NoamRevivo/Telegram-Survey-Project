package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** שאלה עם אפשרויות תשובה. הוולידציה בבנאי — אי אפשר ליצור שאלה לא תקינה. */
public class Question {
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 4;
    public static final int MAX_TEXT_LENGTH = 300;
    /** אפשרות ארוכה מדי נחתכת בכפתור בטלגרם, ובנוסף מנפחת את הודעת "התשובה שלך" */
    public static final int MAX_OPTION_LENGTH = 64;

    private static final String ELLIPSIS = "…";

    private final String id;
    private final String text;
    private final List<String> options;

    public Question(String text, List<String> options) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("שאלה לא יכולה להיות ריקה");
        }
        if (text.trim().length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "נוסח השאלה ארוך מדי (עד " + MAX_TEXT_LENGTH + " תווים)");
        }
        if (options == null || options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException(
                    "כל שאלה צריכה " + MIN_OPTIONS + "-" + MAX_OPTIONS + " אפשרויות תשובה");
        }
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String option : options) {
            if (option == null || option.isBlank()) {
                throw new IllegalArgumentException("אפשרות תשובה לא יכולה להיות ריקה");
            }
            String trimmed = option.trim();
            if (trimmed.length() > MAX_OPTION_LENGTH) {
                throw new IllegalArgumentException(
                        "האפשרות \"" + trimmed + "\" ארוכה מדי (עד " + MAX_OPTION_LENGTH + " תווים)");
            }
            if (!seen.add(optionKey(trimmed))) {
                throw new IllegalArgumentException("האפשרות \"" + trimmed + "\" מופיעה פעמיים");
            }
            cleaned.add(trimmed);
        }
        this.id = UUID.randomUUID().toString();
        this.text = text.trim();
        this.options = cleaned;
    }

    /** מפתח השוואה לאפשרות — כך הכפילויות מזוהות באותה צורה בבנאי, בפרסר ובדיאלוג. */
    static String optionKey(String option) {
        return option.trim().toLowerCase(Locale.ROOT);
    }

    /** לפרסר של תשובות שירות חיצוני: חותך אפשרות ארוכה מדי במקום לפסול את כל השאלה. */
    static String truncateOption(String option) {
        String trimmed = option.trim();
        if (trimmed.length() <= MAX_OPTION_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_OPTION_LENGTH - ELLIPSIS.length()).trim() + ELLIPSIS;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public List<String> getOptions() {
        return Collections.unmodifiableList(options);
    }
}
