package org.example;

/** מחזור החיים של סקר: ממתין לשליחה, פעיל, הסתיים או בוטל. */
public enum SurveyStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    CANCELLED
}