package org.example;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;

public final class UiTheme {
    public static final Color BRAND_BLUE = new Color(0x2AABEE);
    public static final Color BRAND_DARK_BLUE = new Color(0x229ED9);
    public static final Color SUCCESS_GREEN = new Color(0x2E7D32);
    public static final Color ERROR_RED = new Color(0xC62828);
    public static final Color WARNING_ORANGE = new Color(0xEF6C00);
    public static final Color PAGE_BACKGROUND = new Color(0xF1F4F8);
    public static final Color MUTED_TEXT = new Color(0x6B7684);
    public static final Color PANEL_BORDER = new Color(0xD0D7E2);

    public static final Color ROW_WAITING = new Color(0xEDF1F6);
    public static final Color ROW_IN_PROGRESS = new Color(0xFFF3C4);
    public static final Color ROW_COMPLETED = new Color(0xD6F5DC);
    public static final Color ROW_MISSED = new Color(0xFFD6D6);

    private UiTheme() {
    }

    public static void applyRtl(Component component) {
        if (component != null) {
            component.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        }
    }
}