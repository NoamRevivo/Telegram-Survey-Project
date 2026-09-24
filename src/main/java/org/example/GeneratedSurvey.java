package org.example;

import java.util.List;

/**
 * תוצאת פירוק תשובת השירות: השאלות התקינות, ומספר השאלות שנפסלו בדרך —
 * המנהל רואה «נוצרו 2 שאלות (1 נפסלה)» ולא רק את מה ששרד.
 */
public record GeneratedSurvey(List<Question> questions, int skipped) {
    public GeneratedSurvey {
        questions = List.copyOf(questions);
    }
}
