package org.example;

import javax.swing.SwingWorker;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * מנהל יצירת השאלות מול ChatGPT: ה-SwingWorker, הביטול והמעבר בין התוצאות.
 * המסך רק מגיב לארבעת האירועים ואינו מכיר את החוט ברקע.
 * כל הקריאות ל-Callbacks מגיעות ב-EDT.
 */
final class QuestionGenerationController {
    interface Callbacks {
        void onStarted(String topic);

        void onSucceeded(GeneratedSurvey generated);

        void onFailed(String userMessage);

        void onCancelled();
    }

    private static final Logger LOG = Logger.getLogger(QuestionGenerationController.class.getName());

    private final ChatGPTService service;
    private final Callbacks callbacks;
    private SwingWorker<GeneratedSurvey, Void> worker;

    QuestionGenerationController(ChatGPTService service, Callbacks callbacks) {
        this.service = service;
        this.callbacks = callbacks;
    }

    boolean isRunning() {
        return worker != null;
    }

    void start(String topic) {
        if (isRunning()) {
            return;
        }
        callbacks.onStarted(topic);
        worker = new SwingWorker<>() {
            @Override
            protected GeneratedSurvey doInBackground() throws SurveyGenerationException {
                return service.generateSurvey(topic);
            }

            @Override
            protected void done() {
                worker = null;
                deliverOutcome(this);
            }
        };
        worker.execute();
    }

    /**
     * ביטול מיידי — ה-SwingWorker מופסק, ובנוסף מבטלים בפועל את חיבור ה-HTTP הפעיל,
     * כי SwingWorker.cancel(true) בלבד אינו עוצר Socket חוסם (מגבלה ידועה של Thread.interrupt() ב-JDK).
     */
    void cancel() {
        if (worker != null) {
            worker.cancel(true);
            service.cancelCurrentRequest();
        }
    }

    /**
     * חריגה בלתי צפויה (NPE וכד') מגיעה בלי הודעה — בלי הטיפול הזה המנהל היה רואה "נכשלה: null".
     * כשל מוכר (SurveyGenerationException) כבר מנוסח בעברית ומועבר כמות שהוא.
     */
    private static String describe(Throwable cause) {
        if (!(cause instanceof SurveyGenerationException)) {
            LOG.log(Level.SEVERE, "יצירת השאלות נכשלה בשגיאה בלתי צפויה", cause);
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return "אירעה שגיאה בלתי צפויה (" + cause.getClass().getSimpleName() + "). נסה שוב.";
        }
        return message;
    }

    private void deliverOutcome(SwingWorker<GeneratedSurvey, Void> finished) {
        if (finished.isCancelled()) {
            callbacks.onCancelled();
            return;
        }
        try {
            callbacks.onSucceeded(finished.get());
        } catch (CancellationException e) {
            callbacks.onCancelled();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callbacks.onCancelled();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            callbacks.onFailed(describe(cause));
        }
    }
}
