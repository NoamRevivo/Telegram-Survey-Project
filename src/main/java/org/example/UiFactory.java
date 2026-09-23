package org.example;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

public final class UiFactory {
    private UiFactory() {
    }

    public static JPanel emptyState(Icon icon, String title, String hint) {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(Box.createVerticalGlue());
        column.add(centered(new JLabel(icon, SwingConstants.CENTER)));
        column.add(Box.createVerticalStrut(14));
        column.add(centered(styled(new JLabel(title, SwingConstants.CENTER),
                Font.BOLD, UiTheme.FONT_TITLE, UiTheme.BRAND_DARK_BLUE)));
        column.add(Box.createVerticalStrut(8));
        column.add(centered(styled(new JLabel(hint, SwingConstants.CENTER),
                Font.PLAIN, UiTheme.FONT_BODY, UiTheme.MUTED_TEXT)));
        column.add(Box.createVerticalGlue());

        JPanel card = new JPanel(new BorderLayout());
        card.add(column, BorderLayout.CENTER);
        return card;
    }

    public static JTable readOnlyTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(AppConfig.TABLE_ROW_HEIGHT);
        table.setFont(table.getFont().deriveFont(UiTheme.FONT_BODY));
        table.getTableHeader().setFont(
                table.getTableHeader().getFont().deriveFont(Font.BOLD, UiTheme.FONT_BODY));
        table.setFillsViewportHeight(true);
        return table;
    }

    public static DefaultTableModel readOnlyModel(Object[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static JPanel titledScroll(String title, Component content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder(title));
        wrapper.add(new JScrollPane(content), BorderLayout.CENTER);
        return wrapper;
    }

    public static <T extends JComponent> T centered(T component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        return component;
    }

    public static JLabel styled(JLabel label, int fontStyle, float size, Color foreground) {
        label.setFont(label.getFont().deriveFont(fontStyle, size));
        if (foreground != null) {
            label.setForeground(foreground);
        }
        return label;
    }
}