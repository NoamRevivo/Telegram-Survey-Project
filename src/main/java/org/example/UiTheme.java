package org.example;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;

public final class UiTheme {
    public static final Color BRAND_BLUE = new Color(0x2AABEE);
    public static final Color BRAND_DARK_BLUE = new Color(0x229ED9);
    public static final Color SUCCESS_GREEN = new Color(0x2E7D32);
    public static final Color ERROR_RED = new Color(0xC62828);
    public static final Color ERROR_RED_SOFT = new Color(0xFF7A70);
    public static final Color WARNING_ORANGE = new Color(0xEF6C00);
    public static final Color PAGE_BACKGROUND = new Color(0xF1F4F8);
    public static final Color MUTED_TEXT = new Color(0x6B7684);
    public static final Color PANEL_BORDER = new Color(0xD0D7E2);
    public static final Color HIGHLIGHT_GREEN = new Color(0xC8FFC8);
    public static final Color TABLE_STRIPE = new Color(0xF3F6FA);

    public static final Color ICON_COMMUNITY = new Color(0x378ADD);
    public static final Color ICON_CREATE = new Color(0xBA7517);
    public static final Color ICON_ACTIVE = new Color(0x0F6E56);
    public static final Color ICON_LIVE = new Color(0xE24B4A);
    public static final Color ICON_RESULTS = new Color(0x854F0B);

    public static final Color ROW_WAITING = new Color(0xEDF1F6);
    public static final Color ROW_IN_PROGRESS = new Color(0xFFF3C4);
    public static final Color ROW_COMPLETED = new Color(0xD6F5DC);
    public static final Color ROW_MISSED = new Color(0xFFD6D6);

    public static final float FONT_COUNTDOWN = 38f;
    public static final float FONT_HEADLINE = 22f;
    public static final float FONT_TITLE = 20f;
    public static final float FONT_SECTION = 18f;
    public static final float FONT_SUBTITLE = 17f;
    public static final float FONT_BODY = 14f;
    public static final float FONT_SMALL = 13f;
    public static final float FONT_TINY = 12f;
    public static final float FONT_BUTTON = 15f;
    public static final float FONT_HERO_ICON = 40f;
    public static final int FONT_APP_ICON = 30;

    public static final int PAGE_PAD = 14;
    public static final int GAP = 10;
    public static final int GAP_SMALL = 6;

    public static final int ICON_TAB = 20;
    public static final int ICON_HEADER = 32;
    public static final int ICON_EMPTY_STATE = 48;
    public static final int APP_ICON_SIZE = 64;

    private UiTheme() {
    }

    public static void applyRtl(Component component) {
        if (component != null) {
            component.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        }
    }
}