package com.buscard.desktop.ui;

import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardInfo;
import com.buscard.desktop.card.CardSlotManager;
import com.buscard.desktop.card.CardSlotManager.CardSlotInfo;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Top header toolbar managing card insertion, ejection, reset, and compact live telemetry pills. */
public class CardSlotPanel extends JPanel implements CardSlotManager.CardSlotListener {

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### đ");

    private final CardSlotManager slotManager;
    private final JComboBox<CardSlotInfo> cardSelector;
    private final JButton btnInsert;
    private final JButton btnEject;
    private final JButton btnResetSlot;

    private final JLabel lblCardName;
    private final JLabel lblState;
    private final JLabel lblBalance;
    private final JLabel lblPass;
    private final JLabel lblPinTries;

    public CardSlotPanel(CardSlotManager slotManager) {
        this.slotManager = slotManager;
        this.slotManager.addListener(this);

        setLayout(new BorderLayout(12, 0));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        // Left control: Card slot selection and insert/eject/reset buttons
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);

        JLabel lblSlot = new JLabel("Khe cắm:");
        lblSlot.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblSlot.setForeground(UiTheme.TEXT_PRIMARY);
        leftPanel.add(lblSlot);

        cardSelector = new JComboBox<>();
        cardSelector.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cardSelector.setPreferredSize(new Dimension(285, 32));
        refreshCardSelector();
        leftPanel.add(cardSelector);

        btnInsert = new JButton("▶ Cắm thẻ");
        btnInsert.setPreferredSize(new Dimension(105, 32));
        UiTheme.stylePrimaryButton(btnInsert);
        btnInsert.addActionListener(e -> onInsertClicked());
        leftPanel.add(btnInsert);

        btnEject = new JButton("⏹ Rút thẻ");
        btnEject.setPreferredSize(new Dimension(95, 32));
        UiTheme.styleSoftButton(btnEject);
        btnEject.setEnabled(false);
        btnEject.addActionListener(e -> onEjectClicked());
        leftPanel.add(btnEject);

        btnResetSlot = new JButton("🗑 Reset");
        btnResetSlot.setPreferredSize(new Dimension(88, 32));
        UiTheme.styleDangerButton(btnResetSlot);
        btnResetSlot.setToolTipText("Xóa toàn bộ dữ liệu thẻ trong khe cắm đã chọn để đưa về thẻ trắng ban đầu");
        btnResetSlot.addActionListener(e -> onResetSlotClicked());
        leftPanel.add(btnResetSlot);

        add(leftPanel, BorderLayout.WEST);

        // Right info telemetry pills
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        rightPanel.setOpaque(false);

        lblCardName = new JLabel("Chưa cắm thẻ");
        lblCardName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblCardName.setForeground(UiTheme.TEXT_MUTED);

        lblState = new JLabel("");
        lblState.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblState.setOpaque(true);
        lblState.setVisible(false);
        lblState.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        lblBalance = new JLabel("");
        lblBalance.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblBalance.setForeground(UiTheme.SUCCESS);

        lblPass = new JLabel("");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblPass.setForeground(UiTheme.PRIMARY);

        lblPinTries = new JLabel("");
        lblPinTries.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblPinTries.setForeground(UiTheme.TEXT_MUTED);

        rightPanel.add(lblCardName);
        rightPanel.add(lblState);
        rightPanel.add(lblBalance);
        rightPanel.add(lblPass);
        rightPanel.add(lblPinTries);

        add(rightPanel, BorderLayout.CENTER);
    }

    public void refreshCardSelector() {
        CardSlotInfo current = (CardSlotInfo) cardSelector.getSelectedItem();
        cardSelector.removeAllItems();

        List<CardSlotInfo> slots = slotManager.listAvailableSlots();
        for (CardSlotInfo s : slots) {
            cardSelector.addItem(s);
        }

        if (current != null) {
            for (int i = 0; i < cardSelector.getItemCount(); i++) {
                CardSlotInfo item = cardSelector.getItemAt(i);
                if (item.slotId.equals(current.slotId)) {
                    cardSelector.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void onInsertClicked() {
        CardSlotInfo selected = (CardSlotInfo) cardSelector.getSelectedItem();
        if (selected == null) return;

        try {
            slotManager.insertSlot(selected);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối thẻ: " + ex.getMessage(),
                    "Lỗi cắm thẻ", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onEjectClicked() {
        slotManager.eject();
        refreshCardSelector();
    }

    private void onResetSlotClicked() {
        CardSlotInfo selected = (CardSlotInfo) cardSelector.getSelectedItem();
        if (selected == null) return;

        if (selected.isPhysical) {
            JOptionPane.showMessageDialog(this, "Không thể reset thẻ vật lý qua chức năng này.", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selected.isTransient) {
            JOptionPane.showMessageDialog(this, "Thẻ trong RAM sẽ tự mất khi rút thẻ, không cần reset file.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn xóa dữ liệu của [" + selected.displayName + "]\nđể đưa về thẻ trắng ban đầu?",
                "Xác nhận làm trắng thẻ", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            slotManager.resetCardSlot(selected.slotId);
            refreshCardSelector();
            JOptionPane.showMessageDialog(this, "Đã làm trắng thẻ thành công! Bạn có thể cắm thẻ để phát hành lại từ đầu.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    @Override
    public void onCardInserted(String cardName, CardInfo info) {
        btnInsert.setEnabled(false);
        btnEject.setEnabled(true);
        btnResetSlot.setEnabled(false);
        cardSelector.setEnabled(false);
        updateTelemetry(cardName, info);
    }

    @Override
    public void onCardEjected() {
        btnInsert.setEnabled(true);
        btnEject.setEnabled(false);
        btnResetSlot.setEnabled(true);
        cardSelector.setEnabled(true);

        lblCardName.setText("Chưa cắm thẻ");
        lblCardName.setForeground(UiTheme.TEXT_MUTED);
        lblState.setVisible(false);
        lblBalance.setText("");
        lblPass.setText("");
        lblPinTries.setText("");
    }

    @Override
    public void onCardUpdated(CardInfo info) {
        updateTelemetry(slotManager.getActiveSlotId(), info);
    }

    private void updateTelemetry(String cardName, CardInfo info) {
        if (info == null) return;

        String idStr = (info.cardId() != null && !info.cardId().isEmpty()) ? info.cardId() : cardName;
        lblCardName.setText(idStr);
        lblCardName.setForeground(UiTheme.TEXT_PRIMARY);

        CardState state = info.state();
        lblState.setText(state.name());
        lblState.setVisible(true);
        lblState.putClientProperty(FlatClientProperties.STYLE, "arc: 999");
        if (state == CardState.ACTIVE) {
            lblState.setBackground(UiTheme.SUCCESS_BG);
            lblState.setForeground(UiTheme.SUCCESS);
        } else if (state == CardState.LOCKED || state == CardState.REVOKED) {
            lblState.setBackground(UiTheme.DANGER_BG);
            lblState.setForeground(UiTheme.DANGER);
        } else {
            lblState.setBackground(UiTheme.WARNING_BG);
            lblState.setForeground(UiTheme.WARNING);
        }

        lblBalance.setText(MONEY_FMT.format(info.balance()));

        if (info.hasValidPass(java.time.LocalDate.now())) {
            lblPass.setText("• Vé " + (info.passRoute() == 0 ? "Liên tuyến" : "Tuyến " + info.passRoute()));
        } else {
            lblPass.setText("");
        }

        lblPinTries.setText("• PIN: " + info.pinTriesRemaining() + "/3");
    }
}
