package org.example;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreModelTest {
    @Test
    void questionTrimsTextAndOptions() {
        Question question = new Question("  מה דעתך?  ", List.of("  כן ", "לא  "));

        assertEquals("מה דעתך?", question.getText());
        assertEquals(List.of("כן", "לא"), question.getOptions());
    }

    @Test
    void questionRejectsDuplicateOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> new Question("שאלה", List.of("כן", " כן ")));
    }

    @Test
    void questionRejectsTooManyOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> new Question("שאלה", List.of("א", "ב", "ג", "ד", "ה")));
    }

    @Test
    void questionRejectsTextLongerThanTheLimit() {
        String tooLong = "א".repeat(Question.MAX_TEXT_LENGTH + 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new Question(tooLong, List.of("כן", "לא")));
        assertTrue(error.getMessage().contains(String.valueOf(Question.MAX_TEXT_LENGTH)));
    }

    @Test
    void questionAcceptsTextExactlyAtTheLimit() {
        String atLimit = "א".repeat(Question.MAX_TEXT_LENGTH);

        assertEquals(Question.MAX_TEXT_LENGTH, new Question(atLimit, List.of("כן", "לא")).getText().length());
    }

    @Test
    void communityUserFallsBackWhenNameIsMissing() {
        CommunityUser user = new CommunityUser(7L, null, null);

        assertEquals("חבר/ה", user.getFirstName());
        assertTrue(user.toString().contains("ללא שם משתמש בטלגרם"));
        assertFalse(user.toString().contains("null"));
    }

    @Test
    void membersAreReturnedInJoinOrder() {
        CommunityManager manager = new CommunityManager();
        for (long id = 1; id <= 20; id++) {
            manager.addMember(id, "חבר " + id, "user" + id);
        }

        List<Long> ids = new ArrayList<>();
        for (CommunityUser user : manager.getAllMembers()) {
            ids.add(user.getTelegramId());
        }

        List<Long> expected = new ArrayList<>();
        for (long id = 1; id <= 20; id++) {
            expected.add(id);
        }
        assertEquals(expected, ids);
    }

    @Test
    void duplicateMemberIsNotAddedTwice() {
        CommunityManager manager = new CommunityManager();

        assertTrue(manager.addMember(1L, "אורי", "uri"));
        assertFalse(manager.addMember(1L, "אורי", "uri"));
        assertEquals(1, manager.getCommunitySize());
    }

    /**
     * מאזין שזורק חריגה אינו מפיל את שאר המאזינים.
     * הלוגר מושתק לרגע — החריגה כאן מכוונת, ואין טעם להציף את פלט הבדיקות בעקבות מחסנית.
     */
    @Test
    void failingListenerDoesNotStopTheOthers() {
        Logger logger = Logger.getLogger(Listeners.class.getName());
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
        try {
            Listeners<Runnable> listeners = new Listeners<>();
            boolean[] secondWasCalled = {false};

            listeners.add(() -> {
                throw new IllegalStateException("מאזין שבור");
            });
            listeners.add(() -> secondWasCalled[0] = true);

            listeners.fire(Runnable::run);

            assertTrue(secondWasCalled[0]);
        } finally {
            logger.setLevel(originalLevel);
        }
    }
}