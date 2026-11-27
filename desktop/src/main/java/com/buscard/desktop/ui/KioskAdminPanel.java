package com.buscard.desktop.ui;

import com.buscard.common.BusCardClient;
import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardInfo;
import com.buscard.common.CardStatusException;
import com.buscard.common.PassengerProfile;
import com.buscard.common.RsaKeys;
import com.buscard.common.TxRecord;
import com.buscard.desktop.card.CardSlotManager;
import com.buscard.desktop.net.BackendClient;
import com.buscard.desktop.net.BackendClient.RouteInfo;
import com.buscard.desktop.util.PhotoUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Tab 1: Modern 2-column Kiosk & Card Personalization Dashboard. */
public class KioskAdminPanel extends JPanel implements CardSlotManager.CardSlotListener {

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### đ");

    private final CardSlotManager slotManager;
    private final BackendClient backend;

    // Visual card preview
    private SmartCardVisualPanel cardVisual;

    // Issue section
    private JTextField txtCardId;
    private JTextField txtExpiryYears;
    private JComboBox<Category> cmbCategory;
    private JPasswordField txtInitialPin;
    private JButton btnPersonalize;

    // Profile & Photo section
    private JButton btnUnlockProfile;
    private JTextField txtFullName;
    private JTextField txtDob;
    private JTextField txtPhone;
    private JComboBox<Category> cmbProfileCategory;
    private JLabel lblPhotoPreview;
    private JButton btnChoosePhoto;
    private JButton btnSaveProfile;
    private byte[] pendingPhotoBytes;

    // Wallet Top-up
    private JTextField txtTopUpAmount;
    private JButton btnTopUp;

    // Pass Buy
    private JComboBox<PassType> cmbPassType;
    private JComboBox<RouteInfo> cmbPassRoute;
    private JLabel lblPassPrice;
    private JButton btnBuyPass;

    // Admin & PIN
    private JButton btnChangePin;
    private JButton btnUnblockPin;
    private JButton btnToggleLock;
    private JButton btnRenewCard;
    private JButton btnRevokeCard;

    public KioskAdminPanel(CardSlotManager slotManager, BackendClient backend) {
        this.slotManager = slotManager;
        this.backend = backend;
        this.slotManager.addListener(this);

        setLayout(new BorderLayout());
        setBackground(UiTheme.BG_CANVAS);

        JPanel root = new JPanel(new BorderLayout(16, 0));
        root.setBackground(UiTheme.BG_CANVAS);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // LEFT COLUMN: Live Smart Card preview + Card Lifecycle & Security controls
        JPanel leftColumn = new JPanel();
        leftColumn.setOpaque(false);
        leftColumn.setPreferredSize(new Dimension(365, 620));
        leftColumn.setLayout(new BoxLayout(leftColumn, BoxLayout.Y_AXIS));

        JPanel cardPreviewWrap = new JPanel(new BorderLayout());
        cardVisual = new SmartCardVisualPanel();
        cardPreviewWrap.add(cardVisual, BorderLayout.CENTER);
        JPanel visualCardSection = UiTheme.createSectionCard("💳 Thẻ thông minh trực quan", cardPreviewWrap);
        visualCardSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        leftColumn.add(visualCardSection);
        leftColumn.add(Box.createVerticalStrut(14));

        JPanel securitySection = UiTheme.createSectionCard("🔐 Bảo mật & Vòng đời thẻ", buildSecuritySection());
        leftColumn.add(securitySection);
        leftColumn.add(Box.createVerticalGlue());

        // RIGHT COLUMN: Issue -> Profile (AES) -> Wallet & Pass
        JPanel rightColumn = new JPanel();
        rightColumn.setOpaque(false);
        rightColumn.setLayout(new BoxLayout(rightColumn, BoxLayout.Y_AXIS));

        JPanel issueCard = UiTheme.createSectionCard("1. Phát hành & Cá nhân hóa thẻ mới", buildIssueSection());
        rightColumn.add(issueCard);
        rightColumn.add(Box.createVerticalStrut(14));

        JPanel profileCard = UiTheme.createSectionCard("2. Thông tin chủ thẻ & Ảnh (Mã hóa AES-128)", buildProfileSection());
        rightColumn.add(profileCard);
        rightColumn.add(Box.createVerticalStrut(14));

        rightColumn.add(buildFinanceSection());

        root.add(leftColumn, BorderLayout.WEST);
        root.add(rightColumn, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(root);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        onCardEjected();
    }

    private JPanel buildIssueSection() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;

        c.gridx = 0; c.gridy = 0; p.add(new JLabel("Mã thẻ (1-16 ký tự):"), c);
        txtCardId = new JTextField("BC2026000001", 12);
        c.gridx = 1; c.weightx = 1; p.add(txtCardId, c);

        c.gridx = 2; c.weightx = 0; p.add(new JLabel("Thời hạn (năm):"), c);
        txtExpiryYears = new JTextField("5", 5);
        c.gridx = 3; c.weightx = 0.4; p.add(txtExpiryYears, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0; p.add(new JLabel("Đối tượng:"), c);
        cmbCategory = new JComboBox<>(Category.values());
        c.gridx = 1; c.weightx = 1; p.add(cmbCategory, c);

        c.gridx = 2; c.weightx = 0; p.add(new JLabel("Mã PIN ban đầu:"), c);
        txtInitialPin = new JPasswordField("123456", 8);
        c.gridx = 3; c.weightx = 0.4; p.add(txtInitialPin, c);

        btnPersonalize = new JButton("★ Cá nhân hóa & Kích hoạt thẻ (RSA-1024)");
        btnPersonalize.setPreferredSize(new Dimension(240, 36));
        UiTheme.stylePrimaryButton(btnPersonalize);
        btnPersonalize.addActionListener(e -> onPersonalizeClicked());
        c.gridx = 0; c.gridy = 2; c.gridwidth = 4; c.weightx = 1;
        c.insets = new Insets(10, 6, 2, 6);
        p.add(btnPersonalize, c);

        return p;
    }

    private JPanel buildProfileSection() {
        JPanel main = new JPanel(new BorderLayout(16, 10));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topBar.setOpaque(false);
        btnUnlockProfile = new JButton("🔑 Nhập PIN để giải mã & xem hồ sơ");
        btnUnlockProfile.setPreferredSize(new Dimension(270, 34));
        UiTheme.styleSoftButton(btnUnlockProfile);
        btnUnlockProfile.addActionListener(e -> onUnlockProfileClicked());
        topBar.add(btnUnlockProfile);
        main.add(topBar, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0; fields.add(new JLabel("Họ và tên:"), c);
        txtFullName = new JTextField(16);
        c.gridx = 1; c.weightx = 1; fields.add(txtFullName, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0; fields.add(new JLabel("Ngày sinh (YYYY-MM-DD):"), c);
        txtDob = new JTextField(12);
        c.gridx = 1; c.weightx = 1; fields.add(txtDob, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0; fields.add(new JLabel("Số điện thoại:"), c);
        txtPhone = new JTextField(12);
        c.gridx = 1; c.weightx = 1; fields.add(txtPhone, c);

        c.gridx = 0; c.gridy = 3; c.weightx = 0; fields.add(new JLabel("Đối tượng ưu tiên:"), c);
        cmbProfileCategory = new JComboBox<>(Category.values());
        c.gridx = 1; c.weightx = 1; fields.add(cmbProfileCategory, c);

        btnSaveProfile = new JButton("💾 Mã hóa AES & Lưu thông tin");
        btnSaveProfile.setPreferredSize(new Dimension(200, 34));
        UiTheme.stylePrimaryButton(btnSaveProfile);
        btnSaveProfile.setEnabled(false);
        btnSaveProfile.addActionListener(e -> onSaveProfileClicked());
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        c.insets = new Insets(8, 4, 2, 4);
        fields.add(btnSaveProfile, c);

        main.add(fields, BorderLayout.CENTER);

        // Photo preview panel
        JPanel photoPanel = new JPanel(new BorderLayout(6, 6));
        photoPanel.setOpaque(false);
        photoPanel.setPreferredSize(new Dimension(145, 190));

        lblPhotoPreview = new JLabel("Chưa có ảnh", SwingConstants.CENTER);
        lblPhotoPreview.setBackground(new Color(0xF8FAFC));
        lblPhotoPreview.setForeground(UiTheme.TEXT_MUTED);
        lblPhotoPreview.setOpaque(true);
        lblPhotoPreview.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER_SUBTLE, 1, true));
        photoPanel.add(lblPhotoPreview, BorderLayout.CENTER);

        btnChoosePhoto = new JButton("📁 Chọn ảnh...");
        UiTheme.styleSoftButton(btnChoosePhoto);
        btnChoosePhoto.setEnabled(false);
        btnChoosePhoto.addActionListener(e -> onChoosePhotoClicked());
        photoPanel.add(btnChoosePhoto, BorderLayout.SOUTH);

        main.add(photoPanel, BorderLayout.EAST);
        return main;
    }

    private JPanel buildFinanceSection() {
        JPanel p = new JPanel(new GridLayout(1, 2, 14, 0));
        p.setOpaque(false);

        // Top up card
        JPanel topUpBody = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        c.gridx = 0; c.gridy = 0; c.weightx = 0; topUpBody.add(new JLabel("Số tiền (VND):"), c);
        txtTopUpAmount = new JTextField("100000", 8);
        c.gridx = 1; c.weightx = 1; topUpBody.add(txtTopUpAmount, c);

        JPanel quickButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        quickButtons.setOpaque(false);
        for (long a : new long[] {50_000, 100_000, 200_000}) {
            JButton b = new JButton("+" + (a / 1000) + "k");
            UiTheme.styleSoftButton(b);
            b.addActionListener(e -> txtTopUpAmount.setText(String.valueOf(a)));
            quickButtons.add(b);
        }
        c.gridx = 0; c.gridy = 1; c.gridwidth = 2;
        topUpBody.add(quickButtons, c);

        btnTopUp = new JButton("💰 Nạp tiền vào thẻ");
        btnTopUp.setPreferredSize(new Dimension(180, 36));
        UiTheme.styleSuccessButton(btnTopUp);
        btnTopUp.addActionListener(e -> onTopUpClicked());
        c.gridy = 2;
        c.insets = new Insets(10, 4, 2, 4);
        topUpBody.add(btnTopUp, c);
        p.add(UiTheme.createSectionCard("3A. Nạp tiền ví điện tử", topUpBody));

        // Buy pass card
        JPanel passBody = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0; passBody.add(new JLabel("Loại vé:"), c);
        cmbPassType = new JComboBox<>(new PassType[] {PassType.SINGLE_ROUTE, PassType.INTERLINE});
        cmbPassType.addActionListener(e -> updatePassPriceLabel());
        c.gridx = 1; c.weightx = 1; passBody.add(cmbPassType, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0; passBody.add(new JLabel("Tuyến:"), c);
        cmbPassRoute = new JComboBox<>();
        cmbPassRoute.addActionListener(e -> updatePassPriceLabel());
        c.gridx = 1; c.weightx = 1; passBody.add(cmbPassRoute, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0; passBody.add(new JLabel("Giá vé:"), c);
        lblPassPrice = new JLabel("0 đ");
        lblPassPrice.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblPassPrice.setForeground(UiTheme.PRIMARY);
        c.gridx = 1; c.weightx = 1; passBody.add(lblPassPrice, c);

        btnBuyPass = new JButton("🎫 Đăng ký vé tháng (30 ngày)");
        btnBuyPass.setPreferredSize(new Dimension(200, 36));
        UiTheme.stylePrimaryButton(btnBuyPass);
        btnBuyPass.addActionListener(e -> onBuyPassClicked());
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        c.insets = new Insets(8, 4, 2, 4);
        passBody.add(btnBuyPass, c);
        p.add(UiTheme.createSectionCard("3B. Vé tháng ưu đãi", passBody));

        return p;
    }

    private JPanel buildSecuritySection() {
        JPanel p = new JPanel(new GridLayout(5, 1, 0, 8));

        btnChangePin = new JButton("🔐 Đổi mã PIN người dùng");
        UiTheme.styleSoftButton(btnChangePin);
        btnChangePin.addActionListener(e -> onChangePinClicked());
        p.add(btnChangePin);

        btnRenewCard = new JButton("📅 Gia hạn thời hạn thẻ");
        UiTheme.styleSoftButton(btnRenewCard);
        btnRenewCard.addActionListener(e -> onRenewCardClicked());
        p.add(btnRenewCard);

        btnToggleLock = new JButton("⛔ Khóa / Mở khóa thẻ");
        UiTheme.styleSoftButton(btnToggleLock);
        btnToggleLock.addActionListener(e -> onToggleLockClicked());
        p.add(btnToggleLock);

        btnUnblockPin = new JButton("🔓 Mở khóa & Reset PIN (Admin)");
        UiTheme.styleSoftButton(btnUnblockPin);
        btnUnblockPin.setForeground(UiTheme.WARNING);
        btnUnblockPin.addActionListener(e -> onUnblockPinClicked());
        p.add(btnUnblockPin);

        btnRevokeCard = new JButton("🚫 Hủy thẻ vĩnh viễn (Blacklist)");
        UiTheme.styleDangerButton(btnRevokeCard);
        btnRevokeCard.addActionListener(e -> onRevokeCardClicked());
        p.add(btnRevokeCard);

        return p;
    }

    public void reloadRoutes() {
        List<RouteInfo> routes = backend.getRoutes();
        cmbPassRoute.removeAllItems();
        for (RouteInfo r : routes) {
            cmbPassRoute.addItem(r);
        }
        updatePassPriceLabel();
    }

    private void updatePassPriceLabel() {
        if (!slotManager.isCardInserted()) {
            lblPassPrice.setText("Chưa cắm thẻ");
            return;
        }
        CardInfo info = slotManager.getClient().getInfo();
        PassType type = (PassType) cmbPassType.getSelectedItem();
        RouteInfo r = (RouteInfo) cmbPassRoute.getSelectedItem();
        if (type == PassType.INTERLINE) {
            long p = (info.category() == Category.NORMAL) ? 280_000 : 140_000;
            lblPassPrice.setText(MONEY_FMT.format(p));
            cmbPassRoute.setEnabled(false);
        } else {
            cmbPassRoute.setEnabled(true);
            if (r != null) {
                long p = (info.category() == Category.NORMAL) ? r.monthlyPassPrice() : r.monthlyPassPrice() / 2;
                lblPassPrice.setText(MONEY_FMT.format(p));
            }
        }
    }

    // ---------------------------------------------------------------- ACTIONS

    private void onPersonalizeClicked() {
        if (!slotManager.isCardInserted()) return;
        String cardId = txtCardId.getText().trim();
        String pin = new String(txtInitialPin.getPassword());
        if (cardId.isEmpty() || pin.length() < 4 || pin.length() > 8) {
            JOptionPane.showMessageDialog(this, "Mã thẻ không được để trống, mã PIN phải từ 4-8 số.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int years = Integer.parseInt(txtExpiryYears.getText().trim());
        LocalDate expiry = LocalDate.now().plusYears(years);
        Category cat = (Category) cmbCategory.getSelectedItem();

        try {
            RSAPublicKey issuerKey = backend.getIssuerPublicKey();
            BusCardClient client = slotManager.getClient();
            PublicKey cardPublicKey = client.personalize(cardId, expiry, cat, issuerKey, pin);

            backend.registerCard(cardId, RsaKeys.toBase64(cardPublicKey), cat, expiry);
            client.activate(backend);
            backend.syncCard(client.getInfo());

            slotManager.persistActiveCardAs(cardId);
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Cá nhân hóa và kích hoạt thẻ thành công!\nMã thẻ: " + cardId, "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi cá nhân hóa thẻ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onUnlockProfileClicked() {
        if (!slotManager.isCardInserted()) return;
        String pin = promptPin("Nhập mã PIN để mở khóa hồ sơ & ảnh:");
        if (pin == null) return;

        BusCardClient client = slotManager.getClient();
        try {
            client.verifyPin(pin);
            PassengerProfile prof = client.readProfile();
            if (prof != null) {
                txtFullName.setText(prof.fullName());
                txtDob.setText(prof.dateOfBirth() != null ? prof.dateOfBirth().toString() : "");
                txtPhone.setText(prof.phone());
                cmbProfileCategory.setSelectedItem(prof.category());
                cardVisual.setUnlockedProfile(prof);
            } else {
                cmbProfileCategory.setSelectedItem(client.getInfo().category());
            }

            CardInfo info = client.getInfo();
            if (info.photoLength() > 0) {
                byte[] jpeg = client.readPhoto(info.photoLength());
                BufferedImage img = PhotoUtils.bytesToImage(jpeg);
                if (img != null) {
                    lblPhotoPreview.setText("");
                    lblPhotoPreview.setIcon(new ImageIcon(img.getScaledInstance(135, 165, Image.SCALE_SMOOTH)));
                }
            } else {
                lblPhotoPreview.setIcon(null);
                lblPhotoPreview.setText("Chưa có ảnh");
            }

            btnSaveProfile.setEnabled(true);
            btnChoosePhoto.setEnabled(true);
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Xác thực PIN thành công! Đã giải mã hồ sơ chủ thẻ.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (CardStatusException ex) {
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Sai mã PIN: " + ex.getMessage(), "Lỗi xác thực", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi đọc hồ sơ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onChoosePhotoClicked() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Hình ảnh (JPG, PNG, JPEG)", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try {
                pendingPhotoBytes = PhotoUtils.preparePhotoForCard(f);
                BufferedImage img = PhotoUtils.bytesToImage(pendingPhotoBytes);
                if (img != null) {
                    lblPhotoPreview.setText("");
                    lblPhotoPreview.setIcon(new ImageIcon(img.getScaledInstance(135, 165, Image.SCALE_SMOOTH)));
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi nén ảnh: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onSaveProfileClicked() {
        if (!slotManager.isCardInserted()) return;
        String name = txtFullName.getText().trim();
        String phone = txtPhone.getText().trim();
        LocalDate dob;
        try {
            dob = LocalDate.parse(txtDob.getText().trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ngày sinh phải đúng định dạng YYYY-MM-DD (ví dụ 2003-05-15).", "Lỗi nhập liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Category cat = (Category) cmbProfileCategory.getSelectedItem();

        BusCardClient client = slotManager.getClient();
        try {
            CardInfo current = client.getInfo();
            PassengerProfile prof = new PassengerProfile(name, dob, phone, cat);
            client.updateProfile(prof);
            if (pendingPhotoBytes != null) {
                client.writePhoto(pendingPhotoBytes);
                pendingPhotoBytes = null;
            }
            if (cat != null && cat != current.category()) {
                client.updateParams(backend, cat, current.cardExpiry());
            }
            cardVisual.setUnlockedProfile(prof);
            backend.syncCard(client.getInfo());
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Đã mã hóa AES, cập nhật đối tượng & lưu hồ sơ lên thẻ thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi lưu hồ sơ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onTopUpClicked() {
        if (!slotManager.isCardInserted()) return;
        long amount = Long.parseLong(txtTopUpAmount.getText().trim());
        String pin = promptPin("Nhập mã PIN để xác nhận nạp " + MONEY_FMT.format(amount) + ":");
        if (pin == null) return;

        BusCardClient client = slotManager.getClient();
        try {
            client.verifyPin(pin);
            TxRecord tx = client.topUp(backend, amount, LocalDateTime.now());
            backend.recordTransactionOrQueue(tx, null, null, null);
            backend.syncCard(client.getInfo());
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Nạp tiền thành công!\nSố dư mới: " + MONEY_FMT.format(tx.balanceAfter()),
                    "Nạp tiền thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi nạp tiền: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onBuyPassClicked() {
        if (!slotManager.isCardInserted()) return;
        PassType type = (PassType) cmbPassType.getSelectedItem();
        RouteInfo r = (RouteInfo) cmbPassRoute.getSelectedItem();
        int routeId = (type == PassType.INTERLINE || r == null) ? 0 : r.routeId();

        CardInfo info = slotManager.getClient().getInfo();
        long price = (type == PassType.INTERLINE)
                ? (info.category() == Category.NORMAL ? 280_000 : 140_000)
                : (info.category() == Category.NORMAL ? r.monthlyPassPrice() : r.monthlyPassPrice() / 2);

        String pin = promptPin("Nhập mã PIN để thanh toán vé tháng " + MONEY_FMT.format(price) + ":");
        if (pin == null) return;

        BusCardClient client = slotManager.getClient();
        try {
            client.verifyPin(pin);
            LocalDate newExpiry = (info.passExpiry() != null && info.passExpiry().isAfter(LocalDate.now()))
                    ? info.passExpiry().plusDays(30)
                    : LocalDate.now().plusDays(30);

            TxRecord tx = client.buyPass(type, routeId, newExpiry, price, LocalDateTime.now());
            backend.recordTransactionOrQueue(tx, type, routeId, newExpiry);
            backend.syncCard(client.getInfo());
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Mua vé tháng thành công! Hạn dùng đến: " + newExpiry,
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi mua vé tháng: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onChangePinClicked() {
        if (!slotManager.isCardInserted()) return;
        String oldPin = promptPin("Nhập mã PIN hiện tại:");
        if (oldPin == null) return;
        String newPin = promptPin("Nhập mã PIN mới (4-8 số):");
        if (newPin == null || newPin.length() < 4 || newPin.length() > 8) {
            JOptionPane.showMessageDialog(this, "PIN mới phải từ 4-8 số", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BusCardClient client = slotManager.getClient();
        try {
            client.verifyPin(oldPin);
            client.changePin(newPin);
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Đổi mã PIN thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi đổi PIN: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onUnblockPinClicked() {
        if (!slotManager.isCardInserted()) return;
        int conf = JOptionPane.showConfirmDialog(this,
                "Mở khóa PIN qua Admin sẽ XÓA TOÀN BỘ hồ sơ/ảnh cũ vì lý do an toàn mã hóa AES!\nBạn có chắc chắn muốn reset PIN?",
                "Cảnh báo bảo mật", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (conf != JOptionPane.YES_OPTION) return;

        String newPin = promptPin("Nhập mã PIN mới cho chủ thẻ (4-8 số):");
        if (newPin == null || newPin.length() < 4) return;

        BusCardClient client = slotManager.getClient();
        try {
            client.unblockPin(backend, newPin);
            backend.syncCard(client.getInfo());
            txtFullName.setText("");
            txtDob.setText("");
            txtPhone.setText("");
            lblPhotoPreview.setIcon(null);
            lblPhotoPreview.setText("Chưa có ảnh");
            btnSaveProfile.setEnabled(false);
            btnChoosePhoto.setEnabled(false);
            cardVisual.setUnlockedProfile(null);
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Mở khóa và reset mã PIN thành công! Vui lòng mở khóa PIN mới và cập nhật lại hồ sơ/ảnh.",
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi mở khóa PIN: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onToggleLockClicked() {
        if (!slotManager.isCardInserted()) return;
        CardInfo info = slotManager.getClient().getInfo();
        CardState target = (info.state() == CardState.ACTIVE) ? CardState.LOCKED : CardState.ACTIVE;
        String action = (target == CardState.LOCKED) ? "Khóa thẻ" : "Mở khóa thẻ";

        int conf = JOptionPane.showConfirmDialog(this, "Xác nhận " + action + " này qua hệ thống phát hành?",
                action, JOptionPane.YES_NO_OPTION);
        if (conf != JOptionPane.YES_OPTION) return;

        BusCardClient client = slotManager.getClient();
        try {
            client.setStatus(backend, target);
            backend.syncCard(client.getInfo());
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, action + " thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi " + action + ": " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRenewCardClicked() {
        if (!slotManager.isCardInserted()) return;
        CardInfo info = slotManager.getClient().getInfo();
        String yearsStr = JOptionPane.showInputDialog(this,
                "Hạn thẻ hiện tại: " + info.cardExpiry() + "\nNhập số năm muốn gia hạn thêm (tính từ hôm nay):", "5");
        if (yearsStr == null || yearsStr.trim().isEmpty()) return;

        try {
            int years = Integer.parseInt(yearsStr.trim());
            if (years <= 0 || years > 20) {
                JOptionPane.showMessageDialog(this, "Số năm gia hạn phải từ 1 đến 20 năm.", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }
            LocalDate newExpiry = LocalDate.now().plusYears(years);
            BusCardClient client = slotManager.getClient();
            client.updateParams(backend, info.category(), newExpiry);
            backend.syncCard(client.getInfo());
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Gia hạn thẻ thành công!\nHạn mới: " + newExpiry, "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi gia hạn thẻ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRevokeCardClicked() {
        if (!slotManager.isCardInserted()) return;
        int conf = JOptionPane.showConfirmDialog(this,
                "CẢNH BÁO: HỦY THẺ VĨNH VIỄN (REVOKED) không thể khôi phục!\nThẻ sẽ bị đưa vào danh sách đen (Blacklist) và không thể dùng nữa.\nBạn có chắc chắn?",
                "Xác nhận hủy thẻ vĩnh viễn", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
        if (conf != JOptionPane.YES_OPTION) return;

        BusCardClient client = slotManager.getClient();
        try {
            client.setStatus(backend, CardState.REVOKED);
            backend.syncCard(client.getInfo());
            slotManager.refreshCardInfo();
            JOptionPane.showMessageDialog(this, "Đã hủy thẻ vĩnh viễn và cập nhật Blacklist lên hệ thống.", "Đã hủy thẻ", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi hủy thẻ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String promptPin(String msg) {
        JPasswordField pf = new JPasswordField();
        int ok = JOptionPane.showConfirmDialog(this, pf, msg, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        return ok == JOptionPane.OK_OPTION ? new String(pf.getPassword()) : null;
    }

    @Override
    public void onCardInserted(String cardName, CardInfo info) {
        boolean installed = info.state() == CardState.INSTALLED;
        boolean revoked = info.state() == CardState.REVOKED;
        boolean active = info.state() == CardState.ACTIVE;
        btnPersonalize.setEnabled(installed);
        btnUnlockProfile.setEnabled(!installed && !revoked);
        btnTopUp.setEnabled(active);
        btnBuyPass.setEnabled(active);
        btnChangePin.setEnabled(!installed && !revoked);
        btnUnblockPin.setEnabled(!installed && !revoked);
        btnToggleLock.setEnabled(!installed && (active || info.state() == CardState.LOCKED));
        btnToggleLock.setText(active ? "⛔ Khóa thẻ" : "🔓 Mở khóa thẻ");
        btnRenewCard.setEnabled(!installed && !revoked);
        btnRevokeCard.setEnabled(!installed && !revoked);

        if (!installed) {
            txtCardId.setText(info.cardId());
            cmbCategory.setSelectedItem(info.category());
        }
        cardVisual.setCardData(cardName, info, null);
        updatePassPriceLabel();
    }

    @Override
    public void onCardEjected() {
        btnPersonalize.setEnabled(false);
        btnUnlockProfile.setEnabled(false);
        btnSaveProfile.setEnabled(false);
        btnChoosePhoto.setEnabled(false);
        btnTopUp.setEnabled(false);
        btnBuyPass.setEnabled(false);
        btnChangePin.setEnabled(false);
        btnUnblockPin.setEnabled(false);
        btnToggleLock.setEnabled(false);
        btnRenewCard.setEnabled(false);
        btnRevokeCard.setEnabled(false);

        txtFullName.setText("");
        txtDob.setText("");
        txtPhone.setText("");
        lblPhotoPreview.setIcon(null);
        lblPhotoPreview.setText("Chưa cắm thẻ");
        cardVisual.clearCard();
    }

    @Override
    public void onCardUpdated(CardInfo info) {
        boolean installed = info.state() == CardState.INSTALLED;
        boolean revoked = info.state() == CardState.REVOKED;
        boolean active = info.state() == CardState.ACTIVE;
        btnPersonalize.setEnabled(installed);
        btnUnlockProfile.setEnabled(!installed && !revoked);
        btnTopUp.setEnabled(active);
        btnBuyPass.setEnabled(active);
        btnChangePin.setEnabled(!installed && !revoked);
        btnUnblockPin.setEnabled(!installed && !revoked);
        btnToggleLock.setEnabled(!installed && (active || info.state() == CardState.LOCKED));
        btnToggleLock.setText(active ? "⛔ Khóa thẻ" : "🔓 Mở khóa thẻ");
        btnRenewCard.setEnabled(!installed && !revoked);
        btnRevokeCard.setEnabled(!installed && !revoked);

        cardVisual.setCardData(slotManager.getActiveCardName(), info, null);
        updatePassPriceLabel();
    }
}
