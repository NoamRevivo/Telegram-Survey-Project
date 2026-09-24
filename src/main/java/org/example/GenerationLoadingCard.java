package org.example;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * כרטיס ההמתנה לשירות יצירת השאלות: כותרת, פס התקדמות וספירת שניות שחלפו.
 */
final class GenerationLoadingCard extends JPanel {
    private static final int TITLE_GAP = 10;
    private static final int BAR_GAP = 16;

    private final JLabel titleLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel elapsedLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar bar = new JProgressBar();
    private Timer elapsedTimer;

    GenerationLoadingCard() {
        super(new BorderLayout());
        JLabel sparkle = UiFactory.centered(new JLabel("✨", SwingConstants.CENTER));
        sparkle.setFont(sparkle.getFont().deriveFont(Font.PLAIN, UiTheme.FONT_HERO_ICON));

        UiFactory.centered(UiFactory.styled(titleLabel, Font.BOLD,
                UiTheme.FONT_SUBTITLE, UiTheme.BRAND_DARK_BLUE));
        UiFactory.centered(UiFactory.styled(elapsedLabel, Font.PLAIN,
                UiTheme.FONT_SMALL, UiTheme.MUTED_TEXT));

        bar.setIndeterminate(true);
        bar.setForeground(UiTheme.BRAND_BLUE);
        Dimension barSize = new Dimension(AppConfig.LOADING_BAR_WIDTH, AppConfig.PROGRESS_BAR_HEIGHT);
        bar.setMaximumSize(barSize);
        bar.setPreferredSize(barSize);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.add(Box.createVerticalGlue());
        column.add(sparkle);
        column.add(Box.createVerticalStrut(TITLE_GAP));
        column.add(titleLabel);
        column.add(Box.createVerticalStrut(BAR_GAP));
        column.add(bar);
        column.add(Box.createVerticalStrut(TITLE_GAP));
        column.add(elapsedLabel);
        column.add(Box.createVerticalGlue());
        add(column, BorderLayout.CENTER);
    }

    void begin(String title) {
        titleLabel.setText(title);
        elapsedLabel.setText("שולח בקשה לשרת…");
        bar.setIndeterminate(true);

        long startedAt = System.currentTimeMillis();
        stopTimer();
        elapsedTimer = new Timer(AppConfig.ELAPSED_TICK_MILLIS, e -> {
            int seconds = (int) ((System.currentTimeMillis() - startedAt) / 1000);
            elapsedLabel.setText(seconds < AppConfig.SLOW_RESPONSE_SECONDS
                    ? "חלפו " + seconds + " שניות…"
                    : "חלפו " + seconds + " שניות — עוד רגע, השרת עדיין עונה…");
        });
        elapsedTimer.start();
    }

    void showCancelling() {
        elapsedLabel.setText("מבטל…");
    }

    void end() {
        stopTimer();
        bar.setIndeterminate(false);
    }

    private void stopTimer() {
        if (elapsedTimer != null) {
            elapsedTimer.stop();
            elapsedTimer = null;
        }
    }
}
