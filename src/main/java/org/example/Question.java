package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Question {

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 4;

    private final String id;
    private final String text;
    private final List<String> options;

    public Question(String text, List<String> options) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("שאלה לא יכולה להיות ריקה");
        }
        if (options == null || options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException(
                    "כל שאלה צריכה " + MIN_OPTIONS + "-" + MAX_OPTIONS + " אפשרויות תשובה");
        }
        // R5-L04: מה שנשמר הוא מה שנשלח לטלגרם — מנקים רווחים כבר כאן
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String option : options) {
            if (option == null || option.isBlank()) {
                throw new IllegalArgumentException("אפשרות תשובה לא יכולה להיות ריקה");
            }
            String trimmed = option.trim();
            if (!seen.add(trimmed.toLowerCase())) {
                throw new IllegalArgumentException("האפשרות \"" + trimmed + "\" מופיעה פעמיים");
            }
            cleaned.add(trimmed);
        }
        this.id = UUID.randomUUID().toString();
        this.text = text.trim();
        this.options = cleaned;
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