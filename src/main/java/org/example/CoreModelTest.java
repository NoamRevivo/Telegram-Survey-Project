package org.example;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreModelTest {

    /** R5-L04: מה שנשמר הוא מה שנשלח לטלגרם — בלי רווחים בקצוות. */
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

    /** R5-C05: משתמש בלי שם אינו הופך ל-"null" על המסך. */
    @Test
    void communityUserFallsBackWhenNameIsMissing() {
        CommunityUser user = new CommunityUser(7L, null, null);

        assertEquals("חבר/ה", user.getFirstName());
        assertTrue(user.toString().contains("ללא שם משתמש בטלגרם"));
        assertFalse(user.toString().contains("null"));
    }

    /** R5-M17: אותו סדר הצטרפות בכל המסכים. */
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

    /** R5-M01: מאזין שזורק חריגה אינו מפיל את שאר המאזינים. */
    @Test
    void failingListenerDoesNotStopTheOthers() {
        Listeners<Runnable> listeners = new Listeners<>();
        boolean[] secondWasCalled = {false};

        listeners.add(() -> {
            throw new IllegalStateException("מאזין שבור");
        });
        listeners.add(() -> secondWasCalled[0] = true);

        listeners.fire(Runnable::run);

        assertTrue(secondWasCalled[0]);
    }
}