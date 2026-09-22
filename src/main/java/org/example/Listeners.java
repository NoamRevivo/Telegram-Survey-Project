package org.example;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * R5-M09: רשימת מאזינים בטוחה — מאזין שנכשל אינו מפיל את השאר.
 * מחליפה את לולאת ה-fire שהייתה משוכפלת ב-SurveyManager וב-CommunityManager.
 */
public final class Listeners<T> {

    private static final Logger LOG = Logger.getLogger(Listeners.class.getName());

    private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();

    public void add(T listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void remove(T listener) {
        listeners.remove(listener);
    }

    /** R5-M01: חייב להיקרא מחוץ לבלוק synchronized של המנהל (copy-then-notify). */
    public void fire(Consumer<T> event) {
        for (T listener : listeners) {
            try {
                event.accept(listener);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "מאזין נכשל: " + listener.getClass().getName(), e);
            }
        }
    }
}