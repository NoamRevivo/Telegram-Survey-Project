package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Question {
    private final String id;
    private final String text;
    private final List<String> options;

    public Question(String text, List<String> options) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("שאלה לא יכולה להיות ריקה");
        }
        if (options == null || options.size() < 2 || options.size() > 4) {
            throw new IllegalArgumentException("כל שאלה צריכה 2-4 אפשרויות תשובה");
        }
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.options = new ArrayList<>(options);
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


