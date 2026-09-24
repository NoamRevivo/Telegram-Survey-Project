package org.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelegramBotServiceTest {
    @Test
    void commandWithParameterIsReducedToTheCommand() {
        assertEquals("/start", TelegramBotService.commandOf("/start abc"));
        assertEquals("/start", TelegramBotService.commandOf("  /start   abc def "));
    }

    @Test
    void commandAddressedToTheBotIsReducedToTheCommand() {
        assertEquals("/start", TelegramBotService.commandOf("/start@MySurveyBot"));
        assertEquals("/help", TelegramBotService.commandOf("/help@MySurveyBot extra"));
    }

    @Test
    void commandIsCaseInsensitive() {
        assertEquals("/start", TelegramBotService.commandOf("/START"));
    }

    @Test
    void plainTextIsLeftWhole() {
        assertEquals("היי", TelegramBotService.commandOf("היי"));
        assertEquals("hi there", TelegramBotService.commandOf("Hi there"));
        assertEquals("", TelegramBotService.commandOf("   "));
        assertEquals("", TelegramBotService.commandOf(null));
    }
}
