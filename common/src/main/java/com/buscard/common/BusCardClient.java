package com.buscard.common;

import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardConstants.RouteMode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/**
 * High-level host API for the bus card applet. Every method maps to one (or a few) APDUs and throws
 * {@link CardStatusException} on a non-9000 status word.
 */
public class BusCardClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApduTransport transport;
    private Consumer<String> apduLog = s -> { };
    private byte[] cardIdBytes;

    public BusCardClient(ApduTransport transport) {
        this.transport = transport;
    }

    /** Receives one line per APDU exchange (for the terminal's debug panel). */
    public BusCardClient onApdu(Consumer<String> log) {
        this.apduLog = log;
        return this;
    }

    // ------------------------------------------------------------------ public

    public void select() {
        byte[] aid = CardCodec.unhex(CardConstants.AID_HEX);
        exchange(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aid, 256), "SELECT");
    }

    public CardInfo getInfo() {
        CardInfo info = CardInfo.parse(send(CardConstants.INS_GET_INFO, 0, 0, null, "GET_INFO"));
        if (info.state() != CardState.INSTALLED) {
            cardIdBytes = CardIds.toBytes(info.cardId());
        }
        return info;
    }

    public PublicKey getPublicKey() {
        return RsaKeys.fromCardFormat(send(CardConstants.INS_GET_PUBLIC_KEY, 0, 0, null, "GET_PUBLIC_KEY"));
    }

    /**
     * Proves the card holds the private key matching {@code registeredKey} (anti-clone check at every tap).
     * Returns true only if the card's signature over a fresh random challenge verifies.
     */
    public boolean authenticate(PublicKey registeredKey, CardInfo info) {
        byte[] challenge = new byte[CardConstants.CHALLENGE_LEN];
        RANDOM.nextBytes(challenge);
        byte[] sig = send(CardConstants.INS_INTERNAL_AUTH, 0, 0, challenge, "INTERNAL_AUTH");
        return RsaKeys.verify(registeredKey, sig, challenge, CardIds.toBytes(info.cardId()),
                CardCodec.amount(info.txCounter()));
    }

    // ------------------------------------------------------------------ lifecycle

    /** Personalizes a blank card. Returns the card public key generated on-card. */
    public PublicKey personalize(String cardId, LocalDate cardExpiry, Category category,
                                 RSAPublicKey issuerPublicKey, String initialPin) {
        byte[] exp = RsaKeys.exponent(issuerPublicKey);
        byte[] pin = pinBytes(initialPin);
        byte[] data = CardCodec.concat(CardIds.toBytes(cardId), CardCodec.date(cardExpiry),
                new byte[] {(byte) category.code}, RsaKeys.modulus(issuerPublicKey),
                new byte[] {(byte) exp.length}, exp, new byte[] {(byte) pin.length}, pin);
        byte[] resp = send(CardConstants.INS_PERSONALIZE, 0, 0, data, "PERSONALIZE");
        cardIdBytes = CardIds.toBytes(cardId);
        return RsaKeys.fromCardFormat(resp);
    }

    public void activate(IssuerAuthority issuer) {
        issuerCommand(issuer, CardConstants.INS_ACTIVATE, new byte[0], "ACTIVATE");
    }

    public void setStatus(IssuerAuthority issuer, CardState target) {
        issuerCommand(issuer, CardConstants.INS_SET_STATUS, new byte[] {(byte) target.code}, "SET_STATUS");
    }

    public void updateParams(IssuerAuthority issuer, Category category, LocalDate cardExpiry) {
        issuerCommand(issuer, CardConstants.INS_UPDATE_PARAMS,
                CardCodec.concat(new byte[] {(byte) category.code}, CardCodec.date(cardExpiry)), "UPDATE_PARAMS");
    }

    // ------------------------------------------------------------------ PIN

    public void verifyPin(String pin) {
        send(CardConstants.INS_VERIFY_PIN, 0, 0, pinBytes(pin), "VERIFY_PIN");
    }

    public void changePin(String newPin) {
        send(CardConstants.INS_CHANGE_PIN, 0, 0, pinBytes(newPin), "CHANGE_PIN");
    }

    /** Admin PIN reset. Wipes the encrypted profile/photo (they must be re-written afterwards). */
    public void unblockPin(IssuerAuthority issuer, String newPin) {
        byte[] pin = pinBytes(newPin);
        issuerCommand(issuer, CardConstants.INS_UNBLOCK_PIN,
                CardCodec.concat(new byte[] {(byte) pin.length}, pin), "UNBLOCK_PIN");
    }

    // ------------------------------------------------------------------ profile & photo (PIN)

    public PassengerProfile readProfile() {
        byte[] b = send(CardConstants.INS_READ_PROFILE, 0, 0, null, "READ_PROFILE");
        return b.length == 0 ? null : PassengerProfile.decode(b);
    }

    public void updateProfile(PassengerProfile profile) {
        send(CardConstants.INS_UPDATE_PROFILE, 0, 0, profile.encode(), "UPDATE_PROFILE");
    }

    /** Stores an image (already compressed, e.g. JPEG) as length(2) || bytes, zero-padded to 16. */
    public void writePhoto(byte[] image) {
        int total = image.length + 2;
        if (total > CardConstants.PHOTO_MAX) {
            throw new IllegalArgumentException("Photo too large for card: " + image.length + " bytes (max "
                    + (CardConstants.PHOTO_MAX - 2) + ")");
        }
        byte[] blob = Arrays.copyOf(CardCodec.concat(CardCodec.u16(image.length), image), (total + 15) & ~15);
        for (int i = 0, off = 0; off < blob.length; i++, off += CardConstants.PHOTO_CHUNK) {
            byte[] chunk = Arrays.copyOfRange(blob, off, Math.min(blob.length, off + CardConstants.PHOTO_CHUNK));
            send(CardConstants.INS_WRITE_PHOTO, i >> 8, i & 0xFF, chunk, "WRITE_PHOTO");
        }
    }

    /** Returns the stored image, or null if none. */
    public byte[] readPhoto(int photoLength) {
        if (photoLength == 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i * CardConstants.PHOTO_CHUNK < photoLength; i++) {
            out.writeBytes(send(CardConstants.INS_READ_PHOTO, i >> 8, i & 0xFF, null, "READ_PHOTO"));
        }
        byte[] blob = out.toByteArray();
        int len = CardCodec.readU16(blob, 0);
        return Arrays.copyOfRange(blob, 2, 2 + len);
    }

    // ------------------------------------------------------------------ wallet & trips

    /** Requires PIN + issuer signature (the backend authorizes and records the payment). */
    public TxRecord topUp(IssuerAuthority issuer, long amount, LocalDateTime now) {
        byte[] payload = CardCodec.concat(CardCodec.amount(amount), CardCodec.dateTime(now));
        return TxRecord.parse(issuerCommand(issuer, CardConstants.INS_TOP_UP, payload, "TOP_UP"));
    }

    /**
     * @param fare    FLAT: fare to charge now (after discount). DISTANCE: minimum balance required to board.
     * @param penalty charged if the previous DISTANCE trip was never closed (max fare of that route).
     */
    public TxRecord tapIn(int routeId, int stationId, LocalDateTime now, long fare, long penalty, RouteMode mode) {
        byte[] data = CardCodec.concat(CardCodec.u16(routeId), CardCodec.u16(stationId), CardCodec.dateTime(now),
                CardCodec.amount(fare), CardCodec.amount(penalty), new byte[] {(byte) mode.code});
        return TxRecord.parse(send(CardConstants.INS_TAP_IN, 0, 0, data, "TAP_IN"));
    }

    public TxRecord tapOut(int routeId, int stationId, LocalDateTime now, long fare) {
        byte[] data = CardCodec.concat(CardCodec.u16(routeId), CardCodec.u16(stationId), CardCodec.dateTime(now),
                CardCodec.amount(fare));
        return TxRecord.parse(send(CardConstants.INS_TAP_OUT, 0, 0, data, "TAP_OUT"));
    }

    /** Requires PIN. {@code routeId} is ignored by the card for INTERLINE passes (send 0). */
    public TxRecord buyPass(PassType type, int routeId, LocalDate newExpiry, long price, LocalDateTime now) {
        byte[] data = CardCodec.concat(new byte[] {(byte) type.code}, CardCodec.u16(routeId),
                CardCodec.date(newExpiry), CardCodec.amount(price), CardCodec.dateTime(now));
        return TxRecord.parse(send(CardConstants.INS_BUY_PASS, 0, 0, data, "BUY_PASS"));
    }

    public TxRecord getLastTx() {
        return TxRecord.parse(send(CardConstants.INS_GET_LAST_TX, 0, 0, null, "GET_LAST_TX"));
    }

    // ------------------------------------------------------------------ plumbing

    private byte[] issuerCommand(IssuerAuthority issuer, int ins, byte[] payload, String name) {
        if (cardIdBytes == null) {
            getInfo();
        }
        byte[] challenge = send(CardConstants.INS_GET_CHALLENGE, 0, 0, null, "GET_CHALLENGE");
        byte[] sig = issuer.sign(challenge, cardIdBytes, ins, payload);
        return send(ins, 0, 0, CardCodec.concat(payload, sig), name);
    }

    private byte[] send(int ins, int p1, int p2, byte[] data, String name) {
        CommandAPDU cmd = (data == null || data.length == 0)
                ? new CommandAPDU(CardConstants.CLA, ins, p1, p2, 256)
                : new CommandAPDU(CardConstants.CLA, ins, p1, p2, data, 256);
        return exchange(cmd, name);
    }

    private byte[] exchange(CommandAPDU cmd, String name) {
        ResponseAPDU r = transport.transmit(cmd);
        apduLog.accept(String.format("%-14s >> %s%n%-14s << %s %04X", name, CardCodec.hex(cmd.getBytes()),
                "", CardCodec.hex(r.getData()), r.getSW()));
        if (r.getSW() != CardConstants.SW_OK) {
            throw new CardStatusException(r.getSW(), name);
        }
        return r.getData();
    }

    private static byte[] pinBytes(String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            throw new IllegalArgumentException("PIN phải gồm 4-8 chữ số");
        }
        return pin.getBytes(StandardCharsets.US_ASCII);
    }
}
