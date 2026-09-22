package org.example;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Logger;

/**
 * בועת התראה מונפשת שאינה חוסמת את המשתמש.
 * מחליפה את MemberJoinToast ומשמשת לכל ההתראות החיוביות במערכת:
 * הצטרפות חבר, סיום יצירת שאלות ב-ChatGPT, והתחלת סקר.
 */
public class Toast extends JWindow {

    public enum Type {
        SUCCESS(UiTheme.SUCCESS_GREEN),
        INFO(UiTheme.BRAND_DARK_BLUE),
        WARNING(UiTheme.WARNING_ORANGE);

        private final Color background;

        Type(Color background) {
            this.background = background;
        }
    }

    private static final int WIDTH = 400;
    private static final int HEIGHT = 58;
    private static final int VISIBLE_MILLIS = 2600;

    private boolean opacitySupported = true;
    /** כמה בועות מוצגות כרגע, כדי שבועה חדשה תופיע מעל הקודמת (EDT בלבד) */
    private static int visibleCount = 0;

    public Toast(Window owner, String message, Type type) {
        super(owner);
        setSize(WIDTH, HEIGHT);
        setAlwaysOnTop(true);

        boolean transparentBg = true;
        try {
            setBackground(new Color(0, 0, 0, 0));
        } catch (IllegalComponentStateException | UnsupportedOperationException ex) {
            transparentBg = false;
        }

        final Color bubbleColor = type.background;
        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bubbleColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        bubble.setOpaque(!transparentBg);
        if (!transparentBg) {
            bubble.setBackground(bubbleColor);
        }
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));

        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        bubble.add(label, BorderLayout.CENTER);

        setContentPane(bubble);
        UiTheme.applyRtl(bubble);
    }

    /** דרך הקריאה המומלצת — מאתרת לבד את חלון האב ולא עושה דבר אם אין כזה. */
    public static void show(Component source, String message, Type type) {
        Window owner = SwingUtilities.getWindowAncestor(source);
        if (owner != null) {
            new Toast(owner, message, type).showAnimated();
        }
    }

    public void showAnimated() {
        Window owner = getOwner();
        if (owner == null) {
            return;
        }
        Rectangle ownerBounds = owner.getBounds();
        int targetX = ownerBounds.x + (ownerBounds.width - WIDTH) / 2;
        int targetY = ownerBounds.y + ownerBounds.height - HEIGHT - 50 - visibleCount * (HEIGHT + 8);
        visibleCount++;
        int startY = ownerBounds.y + ownerBounds.height;

        setLocation(targetX, startY);
        trySetOpacity(0f);
        setVisible(true);

        int steps = 12;
        int[] step = {0};
        Timer riseTimer = new Timer(15, null);
        riseTimer.addActionListener(e -> {
            step[0]++;
            float progress = Math.min(1f, step[0] / (float) steps);
            int y = (int) (startY + (targetY - startY) * progress);
            setLocation(targetX, y);
            trySetOpacity(progress);
            if (progress >= 1f) {
                riseTimer.stop();
                scheduleFadeOut();
            }
        });
        riseTimer.start();
    }

    private void scheduleFadeOut() {
        Timer waitTimer = new Timer(VISIBLE_MILLIS, e -> fadeOutAndClose());
        waitTimer.setRepeats(false);
        waitTimer.start();
    }

    private void fadeOutAndClose() {
        if (!opacitySupported) {
            visibleCount--;
            dispose();
            return;
        }
        int steps = 10;
        int[] step = {0};
        Timer fadeTimer = new Timer(20, null);
        fadeTimer.addActionListener(e -> {
            step[0]++;
            float opacity = Math.max(0f, 1f - step[0] / (float) steps);
            trySetOpacity(opacity);
            if (opacity <= 0f) {
                fadeTimer.stop();
                visibleCount--;
                dispose();
            }
        });
        fadeTimer.start();
    }

    private void trySetOpacity(float value) {
        if (!opacitySupported) {
            return;
        }
        try {
            setOpacity(value);
        } catch (IllegalComponentStateException | UnsupportedOperationException | IllegalArgumentException ex) {
            opacitySupported = false;
            Logger.getLogger(Toast.class.getName()).fine("שקיפות חלון לא נתמכת — הבועה תוצג ללא דהייה");
        }
    }
}