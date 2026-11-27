package com.buscard.desktop.ui;

import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardInfo;
import com.buscard.common.PassengerProfile;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.text.DecimalFormat;
import javax.swing.JPanel;

/** Live custom-drawn visual preview of the inserted VietBus Java Card. */
public class SmartCardVisualPanel extends JPanel {

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### đ");

    private CardInfo info;
    private PassengerProfile unlockedProfile;
    private String slotLabel;

    public SmartCardVisualPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(340, 215));
        setMinimumSize(new Dimension(320, 205));
    }

    public void setCardData(String slotLabel, CardInfo info, PassengerProfile profile) {
        this.slotLabel = slotLabel;
        this.info = info;
        this.unlockedProfile = profile;
        repaint();
    }

    public void setUnlockedProfile(PassengerProfile profile) {
        this.unlockedProfile = profile;
        repaint();
    }

    public void clearCard() {
        this.slotLabel = null;
        this.info = null;
        this.unlockedProfile = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int pad = 8;
        int w = getWidth() - pad * 2;
        int h = Math.min(getHeight() - pad * 2, (int) (w * 0.62));
        int x = pad;
        int y = (getHeight() - h) / 2;

        if (info == null) {
            // Empty slot dashed frame
            g2.setColor(new Color(0xF8FAFC));
            g2.fill(new RoundRectangle2D.Double(x, y, w, h, 20, 20));
            float[] dash = {6f, 6f};
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
            g2.setColor(new Color(0x94A3B8));
            g2.draw(new RoundRectangle2D.Double(x + 1, y + 1, w - 2, h - 2, 20, 20));

            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.setColor(new Color(0x64748B));
            drawCentered(g2, "CHƯA CẮM THẺ", x, y + h / 2 - 8, w);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            drawCentered(g2, "Chọn khe cắm phía trên và nhấn Cắm thẻ", x, y + h / 2 + 16, w);
            g2.dispose();
            return;
        }

        // Card drop shadow
        g2.setColor(new Color(15, 23, 42, 30));
        g2.fill(new RoundRectangle2D.Double(x + 2, y + 4, w, h, 22, 22));

        // Card gradient based on CardState
        Color c1;
        Color c2;
        switch (info.state()) {
            case ACTIVE -> {
                c1 = new Color(0x0F4C81); // Deep ocean blue
                c2 = new Color(0x0D9488); // Vibrant transit teal
            }
            case INSTALLED, PERSONALIZED -> {
                c1 = new Color(0x475569); // Slate-600
                c2 = new Color(0x64748B);
            }
            case LOCKED -> {
                c1 = new Color(0xB45309); // Amber-700
                c2 = new Color(0xD97706);
            }
            case REVOKED -> {
                c1 = new Color(0x7F1D1D); // Crimson-900
                c2 = new Color(0xDC2626);
            }
            default -> {
                c1 = new Color(0x334155);
                c2 = new Color(0x475569);
            }
        }

        RoundRectangle2D cardShape = new RoundRectangle2D.Double(x, y, w, h, 22, 22);
        g2.setPaint(new GradientPaint(x, y, c1, x + w, y + h, c2));
        g2.fill(cardShape);

        // Subtle decorative circles
        g2.setClip(cardShape);
        g2.setColor(new Color(255, 255, 255, 18));
        g2.fillOval(x + w - 120, y - 40, 170, 170);
        g2.fillOval(x + w - 70, y + h - 80, 140, 140);
        g2.setClip(null);

        // Header branding
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.drawString("VIETBUS PASS", x + 20, y + 30);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(new Color(255, 255, 255, 200));
        g2.drawString("JAVA CARD 3.0.5  •  AES-128  •  RSA-1024", x + 20, y + 45);

        // State pill top-right
        String stateText = info.state().name();
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        int pillW = g2.getFontMetrics().stringWidth(stateText) + 18;
        g2.setColor(new Color(255, 255, 255, 45));
        g2.fill(new RoundRectangle2D.Double(x + w - pillW - 18, y + 16, pillW, 22, 12, 12));
        g2.setColor(Color.WHITE);
        g2.drawString(stateText, x + w - pillW - 9, y + 31);

        // Metallic EMV Chip
        int chipX = x + 22;
        int chipY = y + 62;
        g2.setPaint(new GradientPaint(chipX, chipY, new Color(0xFDE047), chipX + 42, chipY + 32, new Color(0xCA8A04)));
        g2.fill(new RoundRectangle2D.Double(chipX, chipY, 42, 31, 7, 7));
        g2.setColor(new Color(120, 80, 0, 150));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(chipX, chipY, 42, 31, 7, 7));
        g2.drawLine(chipX + 14, chipY, chipX + 14, chipY + 31);
        g2.drawLine(chipX + 28, chipY, chipX + 28, chipY + 31);
        g2.drawLine(chipX, chipY + 15, chipX + 14, chipY + 15);
        g2.drawLine(chipX + 28, chipY + 15, chipX + 42, chipY + 15);

        // Contactless NFC symbol next to chip
        g2.setColor(new Color(255, 255, 255, 210));
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int nfcX = chipX + 52;
        int nfcY = chipY + 15;
        for (int r = 5; r <= 13; r += 4) {
            g2.draw(new Arc2D.Double(nfcX - r, nfcY - r, r * 2, r * 2, -40, 80, Arc2D.OPEN));
        }

        // Balance on right side
        if (info.state() != CardState.INSTALLED) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(new Color(255, 255, 255, 190));
            String balLabel = "SỐ DƯ VÍ THẺ";
            int blW = g2.getFontMetrics().stringWidth(balLabel);
            g2.drawString(balLabel, x + w - blW - 20, y + 68);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 19));
            g2.setColor(Color.WHITE);
            String balStr = MONEY_FMT.format(info.balance());
            int bsW = g2.getFontMetrics().stringWidth(balStr);
            g2.drawString(balStr, x + w - bsW - 20, y + 90);
        }

        // Card ID
        g2.setFont(new Font("Consolas", Font.BOLD, 16));
        g2.setColor(Color.WHITE);
        String idDisplay = (info.cardId() != null && !info.cardId().isEmpty())
                ? info.cardId()
                : "THẺ TRẮNG (" + (slotLabel != null ? slotLabel : "BLANK") + ")";
        g2.drawString(idDisplay, x + 20, y + 128);

        // Owner & Category bottom area
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        String ownerText = unlockedProfile != null
                ? unlockedProfile.fullName().toUpperCase()
                : (info.state() == CardState.INSTALLED ? "CHƯA CÁ NHÂN HÓA" : "HỒ SƠ ĐANG MÃ HÓA AES");
        g2.setColor(new Color(255, 255, 255, 235));
        g2.drawString(ownerText, x + 20, y + h - 34);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.setColor(new Color(255, 255, 255, 200));
        String metaLeft = info.state() == CardState.INSTALLED
                ? "Sẵn sàng phát hành"
                : info.category().name() + "  •  Hạn: " + info.cardExpiry();
        g2.drawString(metaLeft, x + 20, y + h - 16);

        // Monthly pass tag bottom-right
        if (info.passType() != PassType.NONE && info.passExpiry() != null) {
            String passStr = info.passType() == PassType.INTERLINE
                    ? "VÉ LIÊN TUYẾN"
                    : "VÉ TUYẾN " + info.passRoute();
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            int pw = g2.getFontMetrics().stringWidth(passStr) + 14;
            g2.setColor(new Color(16, 185, 129, 220));
            g2.fill(new RoundRectangle2D.Double(x + w - pw - 18, y + h - 32, pw, 20, 8, 8));
            g2.setColor(Color.WHITE);
            g2.drawString(passStr, x + w - pw - 11, y + h - 18);
        }

        g2.dispose();
    }

    private void drawCentered(Graphics2D g2, String text, int x, int y, int width) {
        int tw = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, x + (width - tw) / 2, y);
    }
}
