package org.example;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

public final class AppIcons {
    private interface Painter {
        void paint(Graphics2D g, int size);
    }

    private static Icon badge(int size, Color background, Painter painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(background);
                g2.fillOval(0, 0, size, size);
                painter.paint(g2, size);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    public static Icon community(int size) {
        return badge(size, UiTheme.ICON_COMMUNITY, (g, s) -> {
            g.setColor(Color.WHITE);
            int r = s / 6;
            g.fillOval(s/2 - r*2 - r/2, s/2 - r, r*2, r*2);
            g.fillOval(s/2 + r/2, s/2 - r, r*2, r*2);
            g.fillOval(s/2 - r, s/3 - r, r*2, r*2);
        });
    }

    public static Icon create(int size) {
        return badge(size, UiTheme.ICON_CREATE, (g, s) -> {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(s / 6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(s/3, s*2/3, s*2/3, s/3);
            Path2D tip = new Path2D.Double();
            tip.moveTo(s*2/3, s/3);
            tip.lineTo(s*2/3 + s/8, s/3 - s/8);
            tip.lineTo(s*3/4, s/4 + s/8);
            tip.closePath();
            g.fill(tip);
        });
    }

    public static Icon active(int size) {
        return badge(size, UiTheme.ICON_ACTIVE, (g, s) -> {
            g.setColor(Color.WHITE);
            int barWidth = s / 6;
            int gap = s / 10;
            int baseY = s * 3 / 4;
            g.fillRect(s/4 - barWidth/2, baseY - s/4, barWidth, s/4);
            g.fillRect(s/4 + gap, baseY - s*2/5, barWidth, s*2/5);
            g.fillRect(s/4 + gap*2 + barWidth, baseY - s/2, barWidth, s/2);
        });
    }

    public static Icon live(int size) {
        return badge(size, UiTheme.ICON_LIVE, (g, s) -> { });
    }

    public static Icon results(int size) {
        return badge(size, UiTheme.ICON_RESULTS, (g, s) -> {
            g.setColor(Color.WHITE);
            Path2D star = new Path2D.Double();
            double cx = s / 2.0;
            double cy = s / 2.0;
            double outerR = s * 0.4;
            double innerR = s * 0.18;
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI / 2 + i * Math.PI / 5;
                double r = (i % 2 == 0) ? outerR : innerR;
                double px = cx + r * Math.cos(angle);
                double py = cy - r * Math.sin(angle);
                if (i == 0) {
                    star.moveTo(px, py);
                } else {
                    star.lineTo(px, py);
                }
            }
            star.closePath();
            g.fill(star);
        });
    }

    public static Icon robotOnLight(int size) {
        return badge(size, Color.WHITE, (g, s) -> paintRobotFace(g, s, UiTheme.BRAND_DARK_BLUE));
    }

    private static void paintRobotFace(Graphics2D g, int size, Color faceColor) {
        g.setColor(faceColor);
        int headSize = size * 3 / 5;
        int headX = (size - headSize) / 2;
        int headY = size / 4;
        g.fillRoundRect(headX, headY, headSize, headSize, headSize / 4, headSize / 4);
        Color eyeColor = faceColor.equals(Color.WHITE) ? UiTheme.BRAND_DARK_BLUE : Color.WHITE;
        g.setColor(eyeColor);
        int eyeSize = Math.max(2, headSize / 6);
        g.fillOval(headX + headSize/4 - eyeSize/2, headY + headSize/2 - eyeSize/2, eyeSize, eyeSize);
        g.fillOval(headX + headSize*3/4 - eyeSize/2, headY + headSize/2 - eyeSize/2, eyeSize, eyeSize);
    }

    private AppIcons() {
    }
}