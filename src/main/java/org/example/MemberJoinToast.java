package org.example;

import javax.swing.*;
import java.awt.*;

public class MemberJoinToast extends JWindow {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 56;
    private static final int VISIBLE_MILLIS = 2200;

    public MemberJoinToast(Window owner, String message) {
        super(owner);
        setSize(WIDTH, HEIGHT);
        setAlwaysOnTop(true);
        try {
            setBackground(new Color(0, 0, 0, 0));
        } catch (IllegalComponentStateException | UnsupportedOperationException ex) {
        }

        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.BRAND_DARK_BLUE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));

        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        bubble.add(label, BorderLayout.CENTER);

        setContentPane(bubble);
    }

    public void showAnimated() {
        Window owner = getOwner();
        if (owner == null) {
            return;
        }
        Rectangle ownerBounds = owner.getBounds();
        int targetX = ownerBounds.x + (ownerBounds.width - WIDTH) / 2;
        int targetY = ownerBounds.y + ownerBounds.height - HEIGHT - 50;
        int startY = ownerBounds.y + ownerBounds.height;

        setLocation(targetX, startY);
        setOpacity(0f);
        setVisible(true);

        int steps = 12;
        int[] step = {0};
        Timer riseTimer = new Timer(15, null);
        riseTimer.addActionListener(e -> {
            step[0]++;
            float progress = Math.min(1f, step[0] / (float) steps);
            int y = (int) (startY + (targetY - startY) * progress);
            setLocation(targetX, y);
            setOpacity(progress);
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
        int steps = 10;
        int[] step = {0};
        Timer fadeTimer = new Timer(20, null);
        fadeTimer.addActionListener(e -> {
            step[0]++;
            float opacity = Math.max(0f, 1f - step[0] / (float) steps);
            setOpacity(opacity);
            if (opacity <= 0f) {
                fadeTimer.stop();
                dispose();
            }
        });
        fadeTimer.start();
    }
}