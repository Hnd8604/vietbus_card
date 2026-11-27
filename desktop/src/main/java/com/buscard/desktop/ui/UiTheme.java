package com.buscard.desktop.ui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Shared modern color palette, FlatLaf properties, and rounded card helpers for VietBus Desktop. */
public final class UiTheme {

    public static final Color BG_CANVAS = new Color(0xF1F5F9);      // Slate-100
    public static final Color BG_CARD = Color.WHITE;
    public static final Color BORDER_SUBTLE = new Color(0xE2E8F0);  // Slate-200
    public static final Color TEXT_PRIMARY = new Color(0x0F172A);   // Slate-900
    public static final Color TEXT_MUTED = new Color(0x64748B);     // Slate-500

    public static final Color PRIMARY = new Color(0x0284C7);        // Sky-600
    public static final Color PRIMARY_DARK = new Color(0x0369A1);
    public static final Color SUCCESS = new Color(0x059669);        // Emerald-600
    public static final Color SUCCESS_BG = new Color(0xECFDF5);
    public static final Color WARNING = new Color(0xD97706);        // Amber-600
    public static final Color WARNING_BG = new Color(0xFFFBEB);
    public static final Color DANGER = new Color(0xDC2626);         // Red-600
    public static final Color DANGER_BG = new Color(0xFEF2F2);
    public static final Color CONSOLE_BG = new Color(0x0F172A);     // Dark terminal
    public static final Color CONSOLE_FG = new Color(0x38BDF8);     // Cyan text

    private UiTheme() {
    }

    public static void installLookAndFeelDefaults() {
        UIManager.put("Panel.background", BG_CANVAS);
        UIManager.put("TabbedPane.background", BG_CANVAS);
        UIManager.put("TabbedPane.selectedBackground", BG_CARD);
        UIManager.put("TabbedPane.underlineColor", PRIMARY);
        UIManager.put("TabbedPane.tabHeight", 42);
        UIManager.put("TabbedPane.tabInsets", new Insets(8, 18, 8, 18));
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.gridColor", BORDER_SUBTLE);
        UIManager.put("Table.rowHeight", 28);

        Font base = new Font("Segoe UI", Font.PLAIN, 13);
        UIManager.put("defaultFont", base);
    }

    /** Creates a clean white rounded card panel with a bold header label. */
    public static JPanel createSectionCard(String title, JPanel body) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)
        ));
        card.putClientProperty(FlatClientProperties.STYLE, "arc: 14");

        if (title != null && !title.isEmpty()) {
            JLabel lbl = new JLabel(title);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lbl.setForeground(TEXT_PRIMARY);
            lbl.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SUBTLE),
                    BorderFactory.createEmptyBorder(0, 0, 8, 0)
            ));
            card.add(lbl, BorderLayout.NORTH);
        }

        body.setOpaque(false);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    public static void stylePrimaryButton(JButton btn) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.putClientProperty(FlatClientProperties.STYLE,
                "background: #0284C7; foreground: #FFFFFF; hoverBackground: #0369A1; "
                        + "pressedBackground: #075985; focusedBorderColor: #38BDF8; arc: 10; borderWidth: 0");
    }

    public static void styleSuccessButton(JButton btn) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.putClientProperty(FlatClientProperties.STYLE,
                "background: #059669; foreground: #FFFFFF; hoverBackground: #047857; "
                        + "pressedBackground: #065F46; arc: 10; borderWidth: 0");
    }

    public static void styleDangerButton(JButton btn) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.putClientProperty(FlatClientProperties.STYLE,
                "background: #FEE2E2; foreground: #B91C1C; hoverBackground: #FECACA; "
                        + "borderColor: #FCA5A5; arc: 10");
    }

    public static void styleSoftButton(JButton btn) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.putClientProperty(FlatClientProperties.STYLE,
                "background: #F1F5F9; foreground: #0F172A; hoverBackground: #E2E8F0; "
                        + "borderColor: #CBD5E1; arc: 10");
    }
}
