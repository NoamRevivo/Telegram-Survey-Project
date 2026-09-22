package org.example;

/**
 * R5-M11: העיכוב והתווית קשורים יחד — אין יותר מערך מקביל ל-ComboBox
 * שנשבר בשקט כשמוסיפים אפשרות.
 */
public enum SurveyDelay {

    IMMEDIATE(0, "מיידי"),
    ONE(1, "דקה 1"),
    TWO(2, "2 דקות"),
    FIVE(5, "5 דקות"),
    TEN(10, "10 דקות");

    private final int minutes;
    private final String label;

    SurveyDelay(int minutes, String label) {
        this.minutes = minutes;
        this.label = label;
    }

    public int minutes() {
        return minutes;
    }

    @Override
    public String toString() {
        return label;
    }
}