package com.buscard.desktop.card;

import com.buscard.applet.BusCardApplet;
import com.buscard.common.ApduTransport;
import com.buscard.common.BusCardClient;
import com.buscard.common.CardConstants;
import com.buscard.common.CardInfo;
import com.licel.jcardsim.base.PersistentSimulatorRuntime;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import javacard.framework.AID;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;

/**
 * Manages smart card insertion/removal: supports persistent JCardSim virtual cards (stored in ./cards/slot_X)
 * and physical PC/SC card readers.
 */
public class CardSlotManager {

    public static final AID AID_BUSCARD = AIDUtil.create(CardConstants.AID_HEX);
    private static final Path CARDS_DIR = resolveCardsDir();
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### đ");

    public static Path getCardsDir() {
        return CARDS_DIR;
    }

    private static Path resolveCardsDir() {
        if (Files.exists(Paths.get("backend")) && Files.exists(Paths.get("desktop"))) {
            return Paths.get("cards");
        }
        if (Files.exists(Paths.get("vietbus_card"))) {
            return Paths.get("vietbus_card", "cards");
        }
        return Paths.get("cards");
    }

    public static class CardSlotInfo {
        public final String slotId;
        public final String displayName;
        public final boolean isPhysical;
        public final boolean isTransient;

        public CardSlotInfo(String slotId, String displayName, boolean isPhysical, boolean isTransient) {
            this.slotId = slotId;
            this.displayName = displayName;
            this.isPhysical = isPhysical;
            this.isTransient = isTransient;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public interface CardSlotListener {
        void onCardInserted(String cardName, CardInfo info);
        void onCardEjected();
        void onCardUpdated(CardInfo info);
    }

    private final List<CardSlotListener> listeners = new ArrayList<>();
    private Consumer<String> apduLogger = s -> { };

    private BusCardClient activeClient;
    private CardSimulator activeSimulator;
    private Card activePhysicalCard;
    private String activeSlotId;
    private boolean isPersistentActive = false;

    public CardSlotManager() {
        try {
            Files.createDirectories(CARDS_DIR);
            for (String defaultSlot : List.of("slot_1", "slot_2", "slot_3")) {
                Files.createDirectories(CARDS_DIR.resolve(defaultSlot));
            }
        } catch (Exception ignored) {
        }
    }

    public void addListener(CardSlotListener listener) {
        listeners.add(listener);
    }

    public void setApduLogger(Consumer<String> logger) {
        this.apduLogger = logger;
        if (activeClient != null) {
            activeClient.onApdu(logger);
        }
    }

    public boolean isCardInserted() {
        return activeClient != null;
    }

    public BusCardClient getClient() {
        return activeClient;
    }

    public String getActiveSlotId() {
        return activeSlotId;
    }

    public String getActiveCardName() {
        return activeSlotId != null ? activeSlotId : "Chưa cắm thẻ";
    }

    public void persistActiveCardAs(String cardId) {
        if (activeSlotId != null) {
            refreshCardInfo();
        }
    }

    public boolean isActiveSlotPersistent() {
        return isPersistentActive;
    }

    /** Lists all selectable slots (persistent slots, extra saved slots, RAM slot, physical readers). */
    public List<CardSlotInfo> listAvailableSlots() {
        List<CardSlotInfo> list = new ArrayList<>();

        // 1. Standard persistent slots (slot_1, slot_2, slot_3)
        for (int i = 1; i <= 3; i++) {
            String slotId = "slot_" + i;
            list.add(buildSlotInfo(slotId, "Thẻ ảo Slot " + i));
        }

        // 2. Any additional custom folders in ./cards
        File dir = CARDS_DIR.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles(File::isDirectory);
            if (files != null) {
                Arrays.stream(files)
                        .map(File::getName)
                        .filter(name -> !name.equals("slot_1") && !name.equals("slot_2") && !name.equals("slot_3"))
                        .sorted()
                        .forEach(name -> list.add(buildSlotInfo(name, "Thẻ ảo: " + name)));
            }
        }

        // 3. Transient in-RAM slot (does not save to disk)
        list.add(new CardSlotInfo("transient", "Thẻ ảo trong RAM (Dùng tạm - không lưu file)", false, true));

        // 4. Physical PC/SC readers
        for (String terminal : listPhysicalTerminals()) {
            list.add(new CardSlotInfo(terminal, "Đầu đọc cứng: " + terminal, true, false));
        }

        return list;
    }

    private CardSlotInfo buildSlotInfo(String slotId, String prefix) {
        Path slotDir = CARDS_DIR.resolve(slotId);
        Path metaFile = slotDir.resolve("card_meta.properties");
        if (Files.exists(metaFile)) {
            try (FileInputStream in = new FileInputStream(metaFile.toFile())) {
                Properties p = new Properties();
                p.load(in);
                String cardId = p.getProperty("cardId", "");
                String balanceStr = p.getProperty("balance", "0");
                String state = p.getProperty("state", "ACTIVE");
                long balance = Long.parseLong(balanceStr);
                String label = prefix + " [" + cardId + " | " + MONEY_FMT.format(balance) + " | " + state + "]";
                return new CardSlotInfo(slotId, label, false, false);
            } catch (Exception ignored) {
            }
        }

        File appletImg = slotDir.resolve(SimulatorAtr()).resolve(AIDUtil.toString(AID_BUSCARD)).toFile();
        if (appletImg.exists()) {
            return new CardSlotInfo(slotId, prefix + " (Đã có dữ liệu thẻ)", false, false);
        } else {
            return new CardSlotInfo(slotId, prefix + " (Thẻ trắng mới)", false, false);
        }
    }

    /** Connects to a specific slot (persistent slot, transient, or physical). */
    public CardInfo insertSlot(CardSlotInfo slot) throws Exception {
        if (slot.isPhysical) {
            return insertPhysicalCard(slot.slotId);
        } else if (slot.isTransient) {
            return insertBlankSimulatedCard();
        } else {
            return insertSavedSimulatedCard(slot.slotId);
        }
    }

    /** Inserts a persistent simulated card saved under ./cards/{slotId}. */
    public CardInfo insertSavedSimulatedCard(String slotId) {
        eject();
        Path slotDir = CARDS_DIR.resolve(slotId);
        try {
            Files.createDirectories(slotDir);
        } catch (Exception ignored) {
        }

        System.setProperty(PersistentSimulatorRuntime.PERSISTENT_BASE_DIR, slotDir.toAbsolutePath().toString());
        PersistentSimulatorRuntime runtime = new PersistentSimulatorRuntime();
        CardSimulator sim = new CardSimulator(runtime);

        // Check if applet image already exists in this folder
        File appletImg = slotDir.resolve(SimulatorAtr()).resolve(AIDUtil.toString(AID_BUSCARD)).toFile();
        if (appletImg.exists()) {
            sim.loadApplet(AID_BUSCARD, BusCardApplet.class);
        } else {
            sim.installApplet(AID_BUSCARD, BusCardApplet.class);
        }

        BusCardClient client = new BusCardClient(sim::transmitCommand);
        client.onApdu(apduLogger);
        client.select();

        this.activeSimulator = sim;
        this.activeClient = client;
        this.activeSlotId = slotId;
        this.isPersistentActive = true;

        CardInfo info = client.getInfo();
        saveSlotMetadata(slotId, info);
        listeners.forEach(l -> l.onCardInserted(slotId, info));
        return info;
    }

    /** Inserts a volatile simulated card in memory. */
    public CardInfo insertBlankSimulatedCard() {
        eject();
        CardSimulator sim = new CardSimulator();
        sim.installApplet(AID_BUSCARD, BusCardApplet.class);
        BusCardClient client = new BusCardClient(sim::transmitCommand);
        client.onApdu(apduLogger);
        client.select();

        this.activeSimulator = sim;
        this.activeClient = client;
        this.activeSlotId = "transient";
        this.isPersistentActive = false;

        CardInfo info = client.getInfo();
        listeners.forEach(l -> l.onCardInserted("Thẻ trắng RAM", info));
        return info;
    }

    /** Saves metadata for a slot so it can be previewed in UI before insertion. */
    public void saveSlotMetadata(String slotId, CardInfo info) {
        if (slotId == null || "transient".equals(slotId)) return;
        Path slotDir = CARDS_DIR.resolve(slotId);
        Path metaFile = slotDir.resolve("card_meta.properties");
        try {
            Properties p = new Properties();
            p.setProperty("cardId", info.cardId() != null ? info.cardId() : "");
            p.setProperty("state", info.state().name());
            p.setProperty("balance", String.valueOf(info.balance()));
            p.setProperty("category", info.category().name());
            p.setProperty("passType", info.passType().name());
            p.setProperty("txCounter", String.valueOf(info.txCounter()));
            try (FileOutputStream out = new FileOutputStream(metaFile.toFile())) {
                p.store(out, "VietBus Card Slot Metadata");
            }
        } catch (Exception ignored) {
        }
    }

    /** Wipes a persistent slot back to a fresh blank card. */
    public void resetCardSlot(String slotId) {
        if (slotId == null || "transient".equals(slotId)) return;
        if (slotId.equals(activeSlotId)) {
            eject();
        }
        Path slotDir = CARDS_DIR.resolve(slotId);
        if (Files.exists(slotDir)) {
            try {
                Files.walk(slotDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                Files.createDirectories(slotDir);
            } catch (Exception ignored) {
            }
        }
    }

    /** Checks for physical PC/SC smart card readers and connects if present. */
    public CardInfo insertPhysicalCard(String terminalName) throws Exception {
        eject();
        TerminalFactory factory = TerminalFactory.getDefault();
        CardTerminal terminal = factory.terminals().getTerminal(terminalName);
        if (!terminal.isCardPresent()) {
            throw new IllegalStateException("Không tìm thấy thẻ vật lý trong đầu đọc: " + terminalName);
        }
        Card card = terminal.connect("T=1");
        CardChannel channel = card.getBasicChannel();
        ApduTransport transport = cmd -> {
            try {
                return channel.transmit(cmd);
            } catch (javax.smartcardio.CardException e) {
                throw new RuntimeException("Lỗi giao tiếp đầu đọc thẻ: " + e.getMessage(), e);
            }
        };

        BusCardClient client = new BusCardClient(transport);
        client.onApdu(apduLogger);
        client.select();

        this.activePhysicalCard = card;
        this.activeClient = client;
        this.activeSlotId = terminalName;
        this.isPersistentActive = false;

        CardInfo info = client.getInfo();
        listeners.forEach(l -> l.onCardInserted(terminalName, info));
        return info;
    }

    public List<String> listPhysicalTerminals() {
        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            return factory.terminals().list().stream().map(CardTerminal::getName).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public void eject() {
        if (activeSimulator != null) {
            try {
                activeSimulator.reset();
            } catch (Exception ignored) {
            }
            activeSimulator = null;
        }
        if (activePhysicalCard != null) {
            try {
                activePhysicalCard.disconnect(false);
            } catch (Exception ignored) {
            }
            activePhysicalCard = null;
        }
        if (activeClient != null) {
            activeClient = null;
            activeSlotId = null;
            isPersistentActive = false;
            listeners.forEach(CardSlotListener::onCardEjected);
        }
    }

    public void refreshCardInfo() {
        if (activeClient != null) {
            try {
                CardInfo info = activeClient.getInfo();
                if (isPersistentActive && activeSlotId != null) {
                    saveSlotMetadata(activeSlotId, info);
                }
                listeners.forEach(l -> l.onCardUpdated(info));
            } catch (Exception ignored) {
            }
        }
    }

    private static String SimulatorAtr() {
        return System.getProperty(com.licel.jcardsim.base.Simulator.ATR_SYSTEM_PROPERTY,
                com.licel.jcardsim.base.Simulator.DEFAULT_ATR);
    }
}
