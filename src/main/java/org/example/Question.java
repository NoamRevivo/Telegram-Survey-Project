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


