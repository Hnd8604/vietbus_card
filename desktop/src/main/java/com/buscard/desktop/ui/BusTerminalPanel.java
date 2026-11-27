package com.buscard.desktop.ui;

import com.buscard.common.BusCardClient;
import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.RouteMode;
import com.buscard.common.CardInfo;
import com.buscard.common.CardStatusException;
import com.buscard.common.RsaKeys;
import com.buscard.common.TxRecord;
import com.buscard.desktop.card.CardSlotManager;
import com.buscard.desktop.net.BackendClient;
import com.buscard.desktop.net.BackendClient.CardLookupResult;
import com.buscard.desktop.net.BackendClient.RouteInfo;
import com.buscard.desktop.net.BackendClient.StationInfo;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.security.PublicKey;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Tab 2: Sleek Bus Validator POS Terminal UI supporting FLAT & DISTANCE fares + offline Store-and-Forward. */
public class BusTerminalPanel extends JPanel implements CardSlotManager.CardSlotListener {

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### đ");

    private final CardSlotManager slotManager;
    private final BackendClient backend;

    private JComboBox<RouteInfo> cmbRoute;
    private JComboBox<StationInfo> cmbStation;
    private JLabel lblRouteMode;

    private JButton btnTapIn;
    private JButton btnTapOut;

    private JPanel resultPanel;
    private JLabel lblStatusTitle;
    private JLabel lblAmountCharged;
    private JLabel lblBalanceRemaining;
    private JLabel lblSecurityVerdict;
    private JLabel lblExtraDetails;

    public BusTerminalPanel(CardSlotManager slotManager, BackendClient backend) {
        this.slotManager = slotManager;
        this.backend = backend;
        this.slotManager.addListener(this);

        setLayout(new BorderLayout(0, 16));
        setBackground(UiTheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        // Top route & station configuration card
        JPanel configBody = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0; configBody.add(new JLabel("Tuyến xe buýt:"), c);
        cmbRoute = new JComboBox<>();
        cmbRoute.setFont(new Font("Segoe UI", Font.BOLD, 13));
        cmbRoute.addActionListener(e -> onRouteSelected());
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1; configBody.add(cmbRoute, c);

        c.gridx = 0; c.gridy = 1; c.gridwidth = 1; c.weightx = 0; configBody.add(new JLabel("Trạm hiện tại:"), c);
        cmbStation = new JComboBox<>();
        cmbStation.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        c.gridx = 1; c.weightx = 0.6; configBody.add(cmbStation, c);

        lblRouteMode = new JLabel("ĐỒNG GIÁ FLAT");
        lblRouteMode.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRouteMode.setForeground(UiTheme.PRIMARY);
        lblRouteMode.setOpaque(true);
        lblRouteMode.setBackground(new Color(0xE0F2FE));
        lblRouteMode.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        lblRouteMode.putClientProperty(FlatClientProperties.STYLE, "arc: 999");
        c.gridx = 2; c.weightx = 0.4; configBody.add(lblRouteMode, c);

        add(UiTheme.createSectionCard("🚌 Cấu hình thiết bị quẹt thẻ trên xe (Validator)", configBody), BorderLayout.NORTH);

        // Center tactile NFC tap buttons + LCD Screen
        JPanel centerContainer = new JPanel(new BorderLayout(0, 16));
        centerContainer.setOpaque(false);

        JPanel actionPanel = new JPanel(new GridLayout(1, 2, 18, 0));
        actionPanel.setOpaque(false);
        actionPanel.setPreferredSize(new Dimension(540, 118));

        btnTapIn = new JButton("<html><center><div style='font-size:15px;font-weight:bold'>🚌 QUẸT LÊN XE</div><div style='margin-top:4px;font-size:11px'>(TAP IN • Kiểm tra RSA & Trừ/Giữ chỗ)</div></center></html>");
        btnTapIn.putClientProperty(FlatClientProperties.STYLE,
                "background: #059669; foreground: #FFFFFF; hoverBackground: #047857; "
                        + "pressedBackground: #065F46; disabledBackground: #CBD5E1; arc: 16; borderWidth: 0");
        btnTapIn.addActionListener(e -> onTapInClicked());

        btnTapOut = new JButton("<html><center><div style='font-size:15px;font-weight:bold'>🏁 QUẸT XUỐNG XE</div><div style='margin-top:4px;font-size:11px'>(TAP OUT • Tính cước theo chặng)</div></center></html>");
        btnTapOut.putClientProperty(FlatClientProperties.STYLE,
                "background: #0284C7; foreground: #FFFFFF; hoverBackground: #0369A1; "
                        + "pressedBackground: #075985; disabledBackground: #CBD5E1; arc: 16; borderWidth: 0");
        btnTapOut.addActionListener(e -> onTapOutClicked());

        actionPanel.add(btnTapIn);
        actionPanel.add(btnTapOut);
        centerContainer.add(actionPanel, BorderLayout.NORTH);

        // LCD Display card
        resultPanel = new JPanel(new GridLayout(5, 1, 6, 6));
        resultPanel.setBackground(Color.WHITE);
        resultPanel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        lblStatusTitle = new JLabel("SẴN SÀNG QUẸT THẺ", SwingConstants.CENTER);
        lblStatusTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblStatusTitle.setForeground(UiTheme.TEXT_MUTED);

        lblAmountCharged = new JLabel("", SwingConstants.CENTER);
        lblAmountCharged.setFont(new Font("Segoe UI", Font.BOLD, 18));

        lblBalanceRemaining = new JLabel("", SwingConstants.CENTER);
        lblBalanceRemaining.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblBalanceRemaining.setForeground(UiTheme.TEXT_PRIMARY);

        lblSecurityVerdict = new JLabel("", SwingConstants.CENTER);
        lblSecurityVerdict.setFont(new Font("Segoe UI", Font.BOLD, 12));

        lblExtraDetails = new JLabel("", SwingConstants.CENTER);
        lblExtraDetails.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblExtraDetails.setForeground(UiTheme.TEXT_MUTED);

        resultPanel.add(lblStatusTitle);
        resultPanel.add(lblAmountCharged);
        resultPanel.add(lblBalanceRemaining);
        resultPanel.add(lblSecurityVerdict);
        resultPanel.add(lblExtraDetails);

        centerContainer.add(UiTheme.createSectionCard("📺 Màn hình máy quẹt thẻ (POS Validator LCD)", resultPanel), BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);

        onCardEjected();
    }

    public void reloadRoutes() {
        RouteInfo prev = (RouteInfo) cmbRoute.getSelectedItem();
        List<RouteInfo> routes = backend.getRoutes();
        cmbRoute.removeAllItems();
        for (RouteInfo r : routes) {
            cmbRoute.addItem(r);
        }
        if (prev != null) {
            for (int i = 0; i < cmbRoute.getItemCount(); i++) {
                if (cmbRoute.getItemAt(i).routeId() == prev.routeId()) {
                    cmbRoute.setSelectedIndex(i);
                    break;
                }
            }
        }
        onRouteSelected();
    }

    private void onRouteSelected() {
        RouteInfo r = (RouteInfo) cmbRoute.getSelectedItem();
        cmbStation.removeAllItems();
        if (r != null) {
            lblRouteMode.setText(r.mode() == RouteMode.FLAT
                    ? "ĐỒNG GIÁ FLAT: " + MONEY_FMT.format(r.baseFare()) + " (Chỉ quẹt lên)"
                    : "THEO CỰ LY: " + MONEY_FMT.format(r.baseFare()) + " + " + MONEY_FMT.format(r.farePerStation()) + "/trạm");
            for (StationInfo s : r.stations()) {
                cmbStation.addItem(s);
            }
        }
    }

    private void onTapInClicked() {
        if (!slotManager.isCardInserted()) {
            showError("Vui lòng cắm thẻ vào khe cắm trước!");
            return;
        }
        RouteInfo r = (RouteInfo) cmbRoute.getSelectedItem();
        StationInfo s = (StationInfo) cmbStation.getSelectedItem();
        if (r == null || s == null) {
            showError("Chưa tải được danh mục tuyến xe. Hãy kết nối Backend ít nhất 1 lần để lưu bộ nhớ đệm!");
            return;
        }

        BusCardClient client = slotManager.getClient();
        try {
            CardInfo info = client.getInfo();
            if (info.state() != CardState.ACTIVE) {
                showError("Thẻ không ở trạng thái HOẠT ĐỘNG (Trạng thái: " + info.state() + ")");
                return;
            }

            CardLookupResult lookup = backend.lookupCard(info.cardId());
            PublicKey cardKey = client.getPublicKey();
            PublicKey expectedKey = cardKey;
            String authNote;

            if (lookup.found()) {
                if (lookup.card().state() == CardState.LOCKED || lookup.card().state() == CardState.REVOKED) {
                    showFraudAlert("THẺ NẰM TRONG DANH SÁCH ĐEN (BLACKLIST) TRÊN HỆ THỐNG: " + lookup.card().state());
                    return;
                }
                expectedKey = RsaKeys.fromBase64(lookup.card().publicKeyX509());
                if (!Arrays.equals(cardKey.getEncoded(), expectedKey.getEncoded())) {
                    showFraudAlert("PHÁT HIỆN THẺ NHÂN BẢN / TRÁO KHÓA! Public Key trên thẻ không khớp khóa gốc tại Trung tâm.");
                    return;
                }
                authNote = lookup.backendOnline()
                        ? "✓ Xác thực thẻ thật: HỢP LỆ (Khớp Public Key gốc trên Backend)"
                        : "✓ Xác thực thẻ thật: HỢP LỆ (Khớp Public Key bộ nhớ đệm Offline)";
            } else if (lookup.backendOnline()) {
                showFraudAlert("Thẻ '" + info.cardId() + "' chưa được đăng ký hợp lệ trên Backend!");
                return;
            } else {
                authNote = "✓ Xác thực thẻ thật: HỢP LỆ (RSA-1024 cục bộ - Offline)";
            }

            if (!client.authenticate(expectedKey, info)) {
                showFraudAlert("PHÁT HIỆN THẺ NHÂN BẢN / GIẢ MẠO! Chữ ký thử thách RSA không hợp lệ.");
                return;
            }

            long fare = computeRideFare(r, info.category());
            long penalty = r.maxFare();
            RouteMode mode = r.mode();

            TxRecord tx = client.tapIn(r.routeId(), s.stationId(), LocalDateTime.now(), fare, penalty, mode);
            boolean syncedOnline = backend.recordTransactionOrQueue(tx, null, null, null);
            if (syncedOnline) {
                backend.syncCard(client.getInfo());
            }
            slotManager.refreshCardInfo();

            showSuccessTapIn(tx, r, s, authNote, syncedOnline);
        } catch (CardStatusException ex) {
            slotManager.refreshCardInfo();
            showError("Từ chối lên xe: " + ex.getMessage());
        } catch (Exception ex) {
            slotManager.refreshCardInfo();
            showError("Lỗi hệ thống: " + ex.getMessage());
        }
    }

    private void onTapOutClicked() {
        if (!slotManager.isCardInserted()) {
            showError("Vui lòng cắm thẻ vào khe cắm trước!");
            return;
        }
        RouteInfo r = (RouteInfo) cmbRoute.getSelectedItem();
        StationInfo s = (StationInfo) cmbStation.getSelectedItem();
        if (r == null || s == null) {
            showError("Chưa có dữ liệu tuyến xe!");
            return;
        }

        if (r.mode() == RouteMode.FLAT) {
            showError("Tuyến " + r.code() + " là tuyến ĐỒNG GIÁ (" + MONEY_FMT.format(r.baseFare()) + "). Cước đã thanh toán khi Lên Xe (TAP IN). Bạn không cần quẹt khi xuống xe!");
            return;
        }

        BusCardClient client = slotManager.getClient();
        try {
            CardInfo info = client.getInfo();
            if (!info.onBoard()) {
                showError("Thẻ chưa quẹt lên xe (onBoard = false)!");
                return;
            }
            if (info.tripRoute() != r.routeId()) {
                showError("Thẻ đang trên tuyến " + info.tripRoute() + ", không khớp tuyến hiện tại (" + r.routeId() + ")!");
                return;
            }

            int fromSeq = 1;
            for (StationInfo st : r.stations()) {
                if (st.stationId() == info.tripStation()) {
                    fromSeq = st.sequenceOrder();
                    break;
                }
            }
            int stops = Math.abs(s.sequenceOrder() - fromSeq);
            long raw = Math.min(r.maxFare(), r.baseFare() + stops * r.farePerStation());
            int percent = (info.category() == Category.NORMAL) ? 100 : (info.category() == Category.STUDENT ? 50 : 0);
            long fare = (raw * percent) / 100;

            TxRecord tx = client.tapOut(r.routeId(), s.stationId(), LocalDateTime.now(), fare);
            boolean syncedOnline = backend.recordTransactionOrQueue(tx, null, null, null);
            if (syncedOnline) {
                backend.syncCard(client.getInfo());
            }
            slotManager.refreshCardInfo();

            showSuccessTapOut(tx, r, s, syncedOnline);
        } catch (CardStatusException ex) {
            slotManager.refreshCardInfo();
            showError("Từ chối xuống xe: " + ex.getMessage());
        } catch (Exception ex) {
            slotManager.refreshCardInfo();
            showError("Lỗi hệ thống: " + ex.getMessage());
        }
    }

    private long computeRideFare(RouteInfo r, Category cat) {
        int percent = switch (cat) {
            case NORMAL -> 100;
            case STUDENT -> 50;
            case ELDERLY, DISABLED -> 0;
        };
        return (r.baseFare() * percent) / 100;
    }

    private void showSuccessTapIn(TxRecord tx, RouteInfo r, StationInfo s, String authNote, boolean syncedOnline) {
        resultPanel.setBackground(syncedOnline ? UiTheme.SUCCESS_BG : UiTheme.WARNING_BG);
        lblStatusTitle.setText(syncedOnline
                ? "✓ QUẸT LÊN XE THÀNH CÔNG (ONLINE)"
                : "✓ QUẸT LÊN XE THÀNH CÔNG (OFFLINE - ĐÃ LƯU HÀNG ĐỢI)");
        lblStatusTitle.setForeground(syncedOnline ? UiTheme.SUCCESS : UiTheme.WARNING);

        if (tx.passUsed()) {
            lblAmountCharged.setText("ĐÃ ÁP DỤNG VÉ THÁNG (0 đ)");
            lblAmountCharged.setForeground(UiTheme.SUCCESS);
        } else {
            lblAmountCharged.setText("Trừ tiền: " + MONEY_FMT.format(tx.amount()));
            lblAmountCharged.setForeground(new Color(0xC2410C));
        }

        lblBalanceRemaining.setText("Số dư còn lại: " + MONEY_FMT.format(tx.balanceAfter()));
        lblSecurityVerdict.setText(authNote);
        lblSecurityVerdict.setForeground(UiTheme.SUCCESS);

        String extra = "Tuyến " + r.code() + " • Trạm: " + s.name();
        if (r.mode() == RouteMode.FLAT) {
            extra += " • [ĐỒNG GIÁ: Đã thanh toán xong, không cần quẹt xuống]";
        } else {
            extra += " • [THEO CỰ LY: Nhớ quẹt thẻ (TAP OUT) khi xuống xe]";
        }
        if (tx.penaltyApplied()) {
            extra += " • [ĐÃ THU PHẠT CHUYẾN TRƯỚC]";
        }
        lblExtraDetails.setText(extra);
    }

    private void showSuccessTapOut(TxRecord tx, RouteInfo r, StationInfo s, boolean syncedOnline) {
        resultPanel.setBackground(syncedOnline ? UiTheme.SUCCESS_BG : UiTheme.WARNING_BG);
        lblStatusTitle.setText(syncedOnline
                ? "✓ QUẸT XUỐNG XE THÀNH CÔNG (ONLINE)"
                : "✓ QUẸT XUỐNG XE THÀNH CÔNG (OFFLINE - ĐÃ LƯU HÀNG ĐỢI)");
        lblStatusTitle.setForeground(syncedOnline ? UiTheme.SUCCESS : UiTheme.WARNING);

        if (tx.passUsed()) {
            lblAmountCharged.setText("ĐÃ ÁP DỤNG VÉ THÁNG (0 đ)");
            lblAmountCharged.setForeground(UiTheme.SUCCESS);
        } else {
            lblAmountCharged.setText("Cước chặng: " + MONEY_FMT.format(tx.amount()));
            lblAmountCharged.setForeground(new Color(0xC2410C));
        }

        lblBalanceRemaining.setText("Số dư còn lại: " + MONEY_FMT.format(tx.balanceAfter()));
        lblSecurityVerdict.setText(syncedOnline
                ? "✓ Đã đóng chuyến đi an toàn • Đã đối soát lên Backend"
                : "✓ Đã đóng chuyến đi an toàn • Lưu hàng đợi Store & Forward");
        lblSecurityVerdict.setForeground(UiTheme.SUCCESS);
        lblExtraDetails.setText("Tuyến " + r.code() + " • Trạm xuống: " + s.name());
    }

    private void showFraudAlert(String msg) {
        resultPanel.setBackground(UiTheme.DANGER_BG);
        lblStatusTitle.setText("⛔ PHÁT HIỆN GIAN LẬN / THẺ BỊ CHẶN");
        lblStatusTitle.setForeground(UiTheme.DANGER);
        lblAmountCharged.setText("KHÓA GIAO DỊCH");
        lblAmountCharged.setForeground(UiTheme.DANGER);
        lblBalanceRemaining.setText("");
        lblSecurityVerdict.setText(msg);
        lblSecurityVerdict.setForeground(UiTheme.DANGER);
        lblExtraDetails.setText("Thẻ không vượt qua bước kiểm tra an ninh RSA / Blacklist.");
        JOptionPane.showMessageDialog(this, msg, "CẢNH BÁO AN NINH THẺ", JOptionPane.ERROR_MESSAGE);
    }

    private void showError(String msg) {
        resultPanel.setBackground(UiTheme.DANGER_BG);
        lblStatusTitle.setText("✗ GIAO DỊCH BỊ TỪ CHỐI");
        lblStatusTitle.setForeground(UiTheme.DANGER);
        lblAmountCharged.setText(msg);
        lblAmountCharged.setForeground(UiTheme.DANGER);
        lblBalanceRemaining.setText("");
        lblSecurityVerdict.setText("");
        lblExtraDetails.setText("");
    }

    @Override
    public void onCardInserted(String cardName, CardInfo info) {
        btnTapIn.setEnabled(info.state() == CardState.ACTIVE);
        btnTapOut.setEnabled(info.state() == CardState.ACTIVE);
        if (info.state() == CardState.ACTIVE) {
            resultPanel.setBackground(Color.WHITE);
            lblStatusTitle.setText("THẺ HỢP LỆ — MỜI QUẸT THẺ");
            lblStatusTitle.setForeground(UiTheme.PRIMARY);
            lblAmountCharged.setText("Số dư hiện tại: " + MONEY_FMT.format(info.balance()));
            lblAmountCharged.setForeground(UiTheme.SUCCESS);
            lblBalanceRemaining.setText("Mã thẻ: " + info.cardId() + " (" + info.category() + ")");
            lblSecurityVerdict.setText(info.onBoard() ? "Đang trong chuyến đi tuyến " + info.tripRoute() : "Sẵn sàng lên xe");
            lblSecurityVerdict.setForeground(UiTheme.TEXT_MUTED);
            lblExtraDetails.setText("");
        }
    }

    @Override
    public void onCardEjected() {
        btnTapIn.setEnabled(false);
        btnTapOut.setEnabled(false);
        lblStatusTitle.setText("VUI LÒNG CẮM THẺ ĐỂ QUẸT");
        lblStatusTitle.setForeground(UiTheme.TEXT_MUTED);
        lblAmountCharged.setText("");
        lblBalanceRemaining.setText("");
        lblSecurityVerdict.setText("");
        lblExtraDetails.setText("");
        resultPanel.setBackground(Color.WHITE);
    }

    @Override
    public void onCardUpdated(CardInfo info) {
        btnTapIn.setEnabled(info.state() == CardState.ACTIVE);
        btnTapOut.setEnabled(info.state() == CardState.ACTIVE);
    }
}
