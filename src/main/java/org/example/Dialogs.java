package org.example;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * כל חלונות הדו-שיח החוסמים במקום אחד — סוג ההודעה, הכותרת והסמל נקבעים כאן,
 * כך ששגיאה אינה מוצגת בטעות עם סמל «מידע» ובלי כותרת.
 */
public final class Dialogs {
    private static final String ERROR_TITLE = "שגיאה";

    private Dialogs() {
    }

    public static void warn(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    public static void error(Component parent, String message) {
        error(parent, ERROR_TITLE, message);
    }

    public static void error(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static boolean confirm(Component parent, String title, String message) {
        return ask(parent, title, message, JOptionPane.QUESTION_MESSAGE);
    }

    public static boolean confirmWarning(Component parent, String title, String message) {
        return ask(parent, title, message, JOptionPane.WARNING_MESSAGE);
    }

    private static boolean ask(Component parent, String title, String message, int messageType) {
        return JOptionPane.showConfirmDialog(parent, message, title,
                JOptionPane.YES_NO_OPTION, messageType) == JOptionPane.YES_OPTION;
    }
}
