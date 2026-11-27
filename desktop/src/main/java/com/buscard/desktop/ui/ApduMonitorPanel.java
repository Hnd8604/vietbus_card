package com.buscard.desktop.ui;

import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardInfo;
import com.buscard.desktop.card.CardSlotManager;
import com.buscard.desktop.net.BackendClient;
import com.buscard.desktop.net.BackendClient.CardSummary;
import com.buscard.desktop.net.BackendClient.TxSummary;
import com.buscard.desktop.net.OfflineTxQueue.PendingTx;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** Tab 3: Audit tables (per-card, system-wide, issued cards, offline queue) + dark APDU hex console. */
public class ApduMonitorPanel extends JPanel implements CardSlotManager.CardSlotListener {

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### đ");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final String VIEW_CURRENT_CARD = "1. Giao dịch của thẻ đang cắm";
    private static final String VIEW_ALL_TX = "2. Toàn bộ giao dịch hệ thống (Backend)";
    private static final String VIEW_ALL_CARDS = "3. Danh sách thẻ đã phát hành (Backend)";
    private static final String VIEW_OFFLINE_QUEUE = "4. Hàng đợi Offline (Store & Forward)";

    private final CardSlotManager slotManager;
    private final BackendClient backend;

    private final JComboBox<String> cmbViewMode;
    private final JButton btnSyncQueue;
    private final DefaultTableModel tableModel;
    private final JTable txTable;
    private final JTextArea apduLogArea;
    private final JCheckBox chkAutoScroll;

    public ApduMonitorPanel(CardSlotManager slotManager, BackendClient backend) {
        this.slotManager = slotManager;
        this.backend = backend;
        this.slotManager.addListener(this);

        setLayout(new BorderLayout());
        setBackground(UiTheme.BG_CANVAS);
        setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        // Top audit table card
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        txTable = new JTable(tableModel);
        txTable.setRowHeight(28);
        txTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        txTable.getTableHeader().setBackground(new Color(0xF8FAFC));

        JPanel tableBody = new JPanel(new BorderLayout(0, 8));
        JPanel viewBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        viewBar.setOpaque(false);
        viewBar.add(new JLabel("Chế độ xem:"));
        cmbViewMode = new JComboBox<>(new String[] {
                VIEW_CURRENT_CARD, VIEW_ALL_TX, VIEW_ALL_CARDS, VIEW_OFFLINE_QUEUE
        });
        cmbViewMode.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cmbViewMode.addActionListener(e -> refreshHistory());
        viewBar.add(cmbViewMode);

        JButton btnRefreshHistory = new JButton("🔄 Làm mới");
        UiTheme.styleSoftButton(btnRefreshHistory);
        btnRefreshHistory.addActionListener(e -> refreshHistory());
        viewBar.add(btnRefreshHistory);

        btnSyncQueue = new JButton();
        UiTheme.styleSoftButton(btnSyncQueue);
        btnSyncQueue.addActionListener(e -> onSyncQueueClicked());
        viewBar.add(btnSyncQueue);

        tableBody.add(viewBar, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(txTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER_SUBTLE));
        tableBody.add(tableScroll, BorderLayout.CENTER);

        JPanel tableCard = UiTheme.createSectionCard("📊 Kiểm toán & Đối soát dữ liệu (Backend & Hàng đợi Offline)", tableBody);

        // Bottom dark console: APDU Logger
        JPanel apduBody = new JPanel(new BorderLayout(0, 8));
        apduLogArea = new JTextArea();
        apduLogArea.setEditable(false);
        apduLogArea.setBackground(UiTheme.CONSOLE_BG);
        apduLogArea.setForeground(UiTheme.CONSOLE_FG);
        apduLogArea.setCaretColor(Color.WHITE);
        apduLogArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        apduLogArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane logScroll = new JScrollPane(apduLogArea);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(0x1E293B)));

        JPanel logToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        logToolbar.setOpaque(false);
        JButton btnClear = new JButton("🗑 Xóa log");
        UiTheme.styleSoftButton(btnClear);
        btnClear.addActionListener(e -> apduLogArea.setText(""));

        JButton btnCopy = new JButton("📋 Sao chép log");
        UiTheme.styleSoftButton(btnCopy);
        btnCopy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(apduLogArea.getText()), null));

        chkAutoScroll = new JCheckBox("Tự động cuộn", true);
        chkAutoScroll.setOpaque(false);

        logToolbar.add(btnClear);
        logToolbar.add(btnCopy);
        logToolbar.add(chkAutoScroll);
        apduBody.add(logToolbar, BorderLayout.NORTH);
        apduBody.add(logScroll, BorderLayout.CENTER);

        JPanel apduCard = UiTheme.createSectionCard("💻 Nhật ký gói tin APDU ISO/IEC 7816 (Thời gian thực)", apduBody);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard, apduCard);
        split.setResizeWeight(0.5);
        split.setDividerSize(10);
        split.setOpaque(false);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        backend.addQueueListener(() -> SwingUtilities.invokeLater(() -> {
            updateSyncButton();
            if (VIEW_OFFLINE_QUEUE.equals(cmbViewMode.getSelectedItem())) {
                refreshHistory();
            }
        }));
        updateSyncButton();
        refreshHistory();
    }

    private void updateSyncButton() {
        int count = backend.getPendingCount();
        btnSyncQueue.setText("📤 Đồng bộ Offline (" + count + ")");
        btnSyncQueue.setEnabled(count > 0);
        btnSyncQueue.setForeground(count > 0 ? UiTheme.WARNING : UiTheme.TEXT_MUTED);
    }

    private void onSyncQueueClicked() {
        int before = backend.getPendingCount();
        if (before == 0) return;
        int synced = backend.syncPendingQueue();
        int remaining = backend.getPendingCount();
        updateSyncButton();
        refreshHistory();
        if (remaining == 0) {
            JOptionPane.showMessageDialog(this, "Đã đồng bộ thành công " + synced + " giao dịch Offline lên Backend!",
                    "Đồng bộ hoàn tất", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Đã đồng bộ " + synced + " giao dịch. Còn lại " + remaining + " giao dịch chưa gửi được (Kiểm tra kết nối Backend).",
                    "Đồng bộ chưa hoàn tất", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void appendApduLog(String line) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalTime.now().format(TIME_FMT);
            apduLogArea.append("[" + timestamp + "] " + line + "\n");
            if (chkAutoScroll.isSelected()) {
                apduLogArea.setCaretPosition(apduLogArea.getDocument().getLength());
            }
        });
    }

    public void refreshHistory() {
        updateSyncButton();
        String mode = (String) cmbViewMode.getSelectedItem();
        if (mode == null) mode = VIEW_CURRENT_CARD;

        switch (mode) {
            case VIEW_CURRENT_CARD -> loadCurrentCardTransactions();
            case VIEW_ALL_TX -> loadAllSystemTransactions();
            case VIEW_ALL_CARDS -> loadAllIssuedCards();
            case VIEW_OFFLINE_QUEUE -> loadOfflineQueue();
        }
    }

    private void loadCurrentCardTransactions() {
        tableModel.setColumnIdentifiers(new String[] {
                "STT (Counter)", "Loại giao dịch", "Số tiền", "Số dư sau GD", "Tuyến/Trạm", "Thời gian", "Chữ ký RSA"
        });
        tableModel.setRowCount(0);
        if (!slotManager.isCardInserted()) return;
        CardInfo info = slotManager.getClient().getInfo();
        if (info.state() == CardState.INSTALLED) return;

        List<TxSummary> list = backend.getCardTransactions(info.cardId());
        for (TxSummary tx : list) {
            String routeStation = (tx.routeId() == 0 && tx.stationId() == 0) ? "N/A" : ("T" + tx.routeId() + " / S" + tx.stationId());
            String sigStatus = tx.verified() ? "✓ Hợp lệ (RSA-1024)" : "✗ Không hợp lệ";
            tableModel.addRow(new Object[] {
                    "#" + tx.txCounter(),
                    tx.txType(),
                    MONEY_FMT.format(tx.amount()),
                    MONEY_FMT.format(tx.balanceAfter()),
                    routeStation,
                    tx.txTime(),
                    sigStatus
            });
        }
    }

    private void loadAllSystemTransactions() {
        tableModel.setColumnIdentifiers(new String[] {
                "ID", "Mã thẻ", "STT", "Loại GD", "Số tiền", "Số dư sau GD", "Tuyến/Trạm", "Thời gian", "Chữ ký RSA"
        });
        tableModel.setRowCount(0);
        List<TxSummary> list = backend.listAllTransactions();
        for (TxSummary tx : list) {
            String routeStation = (tx.routeId() == 0 && tx.stationId() == 0) ? "N/A" : ("T" + tx.routeId() + " / S" + tx.stationId());
            String sigStatus = tx.verified() ? "✓ Hợp lệ" : "✗ Không hợp lệ";
            tableModel.addRow(new Object[] {
                    tx.id(),
                    tx.cardId(),
                    "#" + tx.txCounter(),
                    tx.txType(),
                    MONEY_FMT.format(tx.amount()),
                    MONEY_FMT.format(tx.balanceAfter()),
                    routeStation,
                    tx.txTime(),
                    sigStatus
            });
        }
    }

    private void loadAllIssuedCards() {
        tableModel.setColumnIdentifiers(new String[] {
                "Mã thẻ", "Trạng thái", "Lý do khóa", "Đối tượng", "Hạn thẻ", "Số dư đồng bộ", "Vé tháng", "GD cuối"
        });
        tableModel.setRowCount(0);
        List<CardSummary> list = backend.listAllCards();
        for (CardSummary c : list) {
            String pass = (c.passType() != null && c.passType().name().equals("NONE"))
                    ? "Không"
                    : c.passType() + " (T" + c.passRoute() + ", đến " + c.passExpiry() + ")";
            tableModel.addRow(new Object[] {
                    c.cardId(),
                    c.state(),
                    c.lockReason(),
                    c.category(),
                    c.cardExpiry(),
                    MONEY_FMT.format(c.balance()),
                    pass,
                    "#" + c.lastTxCounter()
            });
        }
    }

    private void loadOfflineQueue() {
        tableModel.setColumnIdentifiers(new String[] {
                "Mã thẻ", "Counter", "Loại giao dịch", "Số tiền", "Tuyến/Trạm", "Thời điểm lưu tạm"
        });
        tableModel.setRowCount(0);
        List<PendingTx> list = backend.getPendingTransactions();
        for (PendingTx p : list) {
            String routeStation = (p.routeId() == 0 && p.stationId() == 0) ? "N/A" : ("T" + p.routeId() + " / S" + p.stationId());
            tableModel.addRow(new Object[] {
                    p.cardId(),
                    "#" + p.txCounter(),
                    p.txType(),
                    MONEY_FMT.format(p.amount()),
                    routeStation,
                    p.queuedAt()
            });
        }
    }

    @Override
    public void onCardInserted(String cardName, CardInfo info) {
        refreshHistory();
    }

    @Override
    public void onCardEjected() {
        refreshHistory();
    }

    @Override
    public void onCardUpdated(CardInfo info) {
        refreshHistory();
    }
}
