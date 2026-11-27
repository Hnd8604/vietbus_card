package com.buscard.desktop;

import com.buscard.desktop.card.CardSlotManager;
import com.buscard.desktop.net.BackendClient;
import com.buscard.desktop.ui.ApduMonitorPanel;
import com.buscard.desktop.ui.BusTerminalPanel;
import com.buscard.desktop.ui.CardSlotPanel;
import com.buscard.desktop.ui.KioskAdminPanel;
import com.buscard.desktop.ui.UiTheme;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** Main Swing application launcher for VietBus Smart Card system. */
public class VietBusApp extends JFrame {

    private final CardSlotManager slotManager;
    private final BackendClient backend;

    private final CardSlotPanel slotPanel;
    private final KioskAdminPanel kioskPanel;
    private final BusTerminalPanel terminalPanel;
    private final ApduMonitorPanel monitorPanel;
    private final JLabel lblBackendStatus;
    private final JLabel lblPendingOffline;

    public VietBusApp() {
        super("VietBus Card – Hệ thống thẻ xe buýt thông minh (Java Card & Desktop Swing)");

        String initialUrl = System.getProperty("backend.url",
                System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080"));
        this.backend = new BackendClient(initialUrl);
        this.slotManager = new CardSlotManager();

        setLayout(new BorderLayout());
        getContentPane().setBackground(UiTheme.BG_CANVAS);

        // Header slot toolbar
        this.slotPanel = new CardSlotPanel(slotManager);
        add(slotPanel, BorderLayout.NORTH);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        kioskPanel = new KioskAdminPanel(slotManager, backend);
        tabs.addTab("🏢 Quầy vé & Phát hành (Kiosk)", kioskPanel);

        terminalPanel = new BusTerminalPanel(slotManager, backend);
        tabs.addTab("🚌 Máy quẹt trên xe buýt (Terminal)", terminalPanel);

        monitorPanel = new ApduMonitorPanel(slotManager, backend);
        tabs.addTab("📊 Kiểm toán & APDU Monitor", monitorPanel);

        // Forward APDU exchanges to monitor console
        slotManager.setApduLogger(monitorPanel::appendApduLog);

        add(tabs, BorderLayout.CENTER);

        // Footer status bar
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(2, 12, 2, 12)
        ));

        lblBackendStatus = new JLabel("Backend: Đang kiểm tra...");
        lblBackendStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JButton btnReloadBackend = new JButton("🔄 Kết nối lại & Đồng bộ");
        btnReloadBackend.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        UiTheme.styleSoftButton(btnReloadBackend);
        btnReloadBackend.addActionListener(e -> checkBackendStatus());

        JButton btnChangeServer = new JButton("⚙ Đổi Server");
        btnChangeServer.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        UiTheme.styleSoftButton(btnChangeServer);
        btnChangeServer.addActionListener(e -> onChangeServerClicked());

        lblPendingOffline = new JLabel();
        lblPendingOffline.setFont(new Font("Segoe UI", Font.BOLD, 11));

        footer.add(lblBackendStatus);
        footer.add(btnReloadBackend);
        footer.add(btnChangeServer);
        footer.add(Box.createHorizontalStrut(8));
        footer.add(lblPendingOffline);
        footer.add(Box.createHorizontalStrut(12));
        JLabel lblSpec = new JLabel("Java Card 3.0.5 • AES-128 • RSA-1024");
        lblSpec.setForeground(UiTheme.TEXT_MUTED);
        lblSpec.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footer.add(lblSpec);
        add(footer, BorderLayout.SOUTH);

        backend.addQueueListener(() -> SwingUtilities.invokeLater(this::updatePendingLabel));
        updatePendingLabel();

        setPreferredSize(new Dimension(1180, 800));
        setMinimumSize(new Dimension(960, 660));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);

        checkBackendStatus();
    }

    private void onChangeServerClicked() {
        String input = JOptionPane.showInputDialog(this,
                "Nhập địa chỉ Backend Spring Boot (ví dụ http://localhost:8080 hoặc http://192.168.1.10:8080):",
                backend.getBaseUrl());
        if (input != null && !input.trim().isEmpty()) {
            backend.setBaseUrl(input.trim());
            checkBackendStatus();
        }
    }

    private void updatePendingLabel() {
        int pending = backend.getPendingCount();
        if (pending > 0) {
            lblPendingOffline.setText("⏳ Hàng đợi Offline: " + pending + " GD chờ đồng bộ");
            lblPendingOffline.setForeground(UiTheme.WARNING);
        } else {
            lblPendingOffline.setText("✓ Đã đồng bộ hết");
            lblPendingOffline.setForeground(UiTheme.SUCCESS);
        }
    }

    private void checkBackendStatus() {
        boolean online = backend.isOnline();
        kioskPanel.reloadRoutes();
        terminalPanel.reloadRoutes();

        if (online) {
            int synced = backend.syncPendingQueue();
            lblBackendStatus.setText("● Backend: ĐANG KẾT NỐI (" + backend.getBaseUrl() + ")");
            lblBackendStatus.setForeground(UiTheme.SUCCESS);
            if (synced > 0) {
                monitorPanel.appendApduLog("SYNC_OFFLINE   >> Đã tự động đồng bộ " + synced + " giao dịch Offline lên Backend.");
                monitorPanel.refreshHistory();
            }
        } else {
            lblBackendStatus.setText("○ Backend (" + backend.getBaseUrl() + "): CHƯA BẬT (Chế độ Offline)");
            lblBackendStatus.setForeground(UiTheme.DANGER);
        }
        updatePendingLabel();
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        UiTheme.installLookAndFeelDefaults();
        SwingUtilities.invokeLater(() -> {
            VietBusApp app = new VietBusApp();
            app.setVisible(true);
        });
    }
}
