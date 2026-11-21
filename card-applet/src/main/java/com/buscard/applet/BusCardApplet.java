package com.buscard.applet;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.MessageDigest;
import javacard.security.RSAPublicKey;
import javacard.security.RandomData;
import javacard.security.Signature;
import javacardx.crypto.Cipher;

/**
 * Bus card applet.
 *
 * <p>Holds only the core card data: identity, status, wallet balance, monthly pass, current trip,
 * and an AES-encrypted passenger profile + photo. Detailed history lives in the backend.
 *
 * <p>Security model:
 * <ul>
 *   <li>Card RSA key pair is generated on-card at personalization; the private key never leaves.</li>
 *   <li>INTERNAL_AUTH signs a reader challenge to prove the card is genuine.</li>
 *   <li>Every balance-changing command returns a card-signed transaction record with a monotonic counter.</li>
 *   <li>Issuer-authorized commands (top-up, status change, PIN unblock...) must carry an RSA signature by the
 *       issuer (backend) over a fresh card challenge.</li>
 *   <li>Profile/photo are encrypted with a random DEK, wrapped by KEK = SHA-256(PIN || salt)[0..16].</li>
 * </ul>
 *
 * <p>Only Java Card classic API is used (byte/short arithmetic, no allocation after install).
 * Keep constants in sync with {@code com.buscard.common.CardConstants}.
 */
public class BusCardApplet extends Applet {

    // ---------------------------------------------------------------- APDU
    static final byte CLA = (byte) 0x80;

    static final byte INS_GET_INFO = (byte) 0x10;
    static final byte INS_GET_PUBLIC_KEY = (byte) 0x11;
    static final byte INS_INTERNAL_AUTH = (byte) 0x12;
    static final byte INS_GET_CHALLENGE = (byte) 0x13;
    static final byte INS_VERIFY_PIN = (byte) 0x20;
    static final byte INS_CHANGE_PIN = (byte) 0x21;
    static final byte INS_UNBLOCK_PIN = (byte) 0x22;
    static final byte INS_READ_PROFILE = (byte) 0x30;
    static final byte INS_UPDATE_PROFILE = (byte) 0x31;
    static final byte INS_READ_PHOTO = (byte) 0x32;
    static final byte INS_WRITE_PHOTO = (byte) 0x33;
    static final byte INS_TOP_UP = (byte) 0x40;
    static final byte INS_TAP_IN = (byte) 0x41;
    static final byte INS_TAP_OUT = (byte) 0x42;
    static final byte INS_BUY_PASS = (byte) 0x43;
    static final byte INS_GET_LAST_TX = (byte) 0x44;
    static final byte INS_SET_STATUS = (byte) 0x50;
    static final byte INS_UPDATE_PARAMS = (byte) 0x51;
    static final byte INS_PERSONALIZE = (byte) 0x60;
    static final byte INS_ACTIVATE = (byte) 0x61;

    // ---------------------------------------------------------------- status words
    static final short SW_INSUFFICIENT_FUNDS = (short) 0x6A90;
    static final short SW_CARD_EXPIRED = (short) 0x6A91;
    static final short SW_CARD_BLOCKED = (short) 0x6A92;
    static final short SW_NOT_ON_BOARD = (short) 0x6A93;
    static final short SW_PASS_WRONG_ROUTE = (short) 0x6A94;
    static final short SW_BALANCE_LIMIT = (short) 0x6A95;
    static final short SW_ISSUER_AUTH_FAILED = (short) 0x6A96;
    static final short SW_PIN_BLOCKED = (short) 0x6983;
    static final short SW_PIN_TRIES_BASE = (short) 0x63C0;

    // ---------------------------------------------------------------- card states
    static final byte ST_INSTALLED = 1;
    static final byte ST_PERSONALIZED = 2;
    static final byte ST_ACTIVE = 3;
    static final byte ST_LOCKED = 4;
    static final byte ST_REVOKED = 5;

    static final byte LOCK_NONE = 0;
    static final byte LOCK_PIN = 1;
    static final byte LOCK_ADMIN = 2;

    // ---------------------------------------------------------------- domain codes
    static final byte PASS_NONE = 0;
    static final byte PASS_SINGLE_ROUTE = 1;
    static final byte PASS_INTERLINE = 2;

    static final byte ROUTE_FLAT = 0;      // fare charged at tap-in
    static final byte ROUTE_DISTANCE = 1;  // fare charged at tap-out by number of stations

    static final byte TX_TOP_UP = 1;
    static final byte TX_TAP_IN = 2;
    static final byte TX_TAP_OUT = 3;
    static final byte TX_BUY_PASS = 4;

    static final byte FLAG_PASS_USED = 0x01;
    static final byte FLAG_PENALTY = 0x02;

    // ---------------------------------------------------------------- sizes
    static final short CARD_ID_LEN = 16;
    static final short DATE_LEN = 4;        // YYYY(2) MM(1) DD(1)
    static final short DATETIME_LEN = 6;    // date(4) HH(1) mm(1)
    static final short AMOUNT_LEN = 4;      // unsigned 32-bit VND, big endian
    static final short CHALLENGE_LEN = 16;
    static final short RSA_BITS = 1024;
    static final short RSA_LEN = 128;
    static final byte PIN_TRY_LIMIT = 5;
    static final byte PIN_MIN = 4;
    static final byte PIN_MAX = 8;
    static final short PROFILE_MAX = 192;
    static final short PHOTO_CHUNK = 224;
    static final short PHOTO_MAX = (short) (37 * PHOTO_CHUNK); // 8288 bytes

    // Transaction record: cardId(16) counter(4) type(1) amount(4) balanceAfter(4)
    //                     route(2) station(2) dateTime(6) flags(1)
    static final short REC_COUNTER = 16;
    static final short REC_TYPE = 20;
    static final short REC_AMOUNT = 21;
    static final short REC_BALANCE = 25;
    static final short REC_ROUTE = 29;
    static final short REC_STATION = 31;
    static final short REC_TIME = 33;
    static final short REC_FLAGS = 39;
    static final short REC_LEN = 40;

    // Scratch (RAM) layout
    static final short S_DIGEST = 0;    // 32 bytes
    static final short S_KEY = 32;      // 16 bytes: DEK material / IV
    static final short S_AMOUNT = 48;   // 4 bytes: computed amount
    static final short S_AMOUNT2 = 52;  // 4 bytes: second operand / result
    static final short S_DATA = 64;     // 224 bytes: cipher in/out
    static final short SCRATCH_LEN = (short) (S_DATA + PHOTO_CHUNK);

    // ---------------------------------------------------------------- persistent state
    private byte state;
    private byte lockReason;
    private final byte[] cardId;
    private final byte[] cardExpiry;
    private byte category;
    private final byte[] balance;
    private final byte[] maxBalance;
    private final byte[] txCounter;

    private byte passType;
    private final byte[] passRoute;
    private final byte[] passExpiry;

    private byte onBoard;
    private final byte[] tripRoute;
    private final byte[] tripStation;
    private final byte[] tripTime;

    private final byte[] lastRecord;

    private final OwnerPIN pin;
    private final byte[] pinSalt;
    private final byte[] wrappedDek;

    private short profileLen;
    private final byte[] profileIv;
    private final byte[] profileCt;
    private short photoLen;
    private final byte[] photoIv;
    private final byte[] photoCt;

    private final KeyPair cardKeyPair;
    private final RSAPublicKey issuerKey;

    // ---------------------------------------------------------------- crypto engines & RAM
    private final AESKey kek;
    private final AESKey dek;
    private final Cipher aesEcb;
    private final Cipher aesCbc;
    private final MessageDigest sha256;
    private final RandomData rng;
    private final Signature cardSigner;
    private final Signature issuerVerifier;

    private final byte[] scratch;
    private final byte[] challenge;
    /** [0] issuer challenge valid, [1] DEK unlocked. Cleared on deselect. */
    private final boolean[] flags;

    protected BusCardApplet() {
        state = ST_INSTALLED;
        lockReason = LOCK_NONE;
        cardId = new byte[CARD_ID_LEN];
        cardExpiry = new byte[DATE_LEN];
        balance = new byte[AMOUNT_LEN];
        // 10,000,000 VND wallet cap = 0x00989680
        maxBalance = new byte[] {(byte) 0x00, (byte) 0x98, (byte) 0x96, (byte) 0x80};
        txCounter = new byte[4];
        passRoute = new byte[2];
        passExpiry = new byte[DATE_LEN];
        tripRoute = new byte[2];
        tripStation = new byte[2];
        tripTime = new byte[DATETIME_LEN];
        lastRecord = new byte[REC_LEN];

        pin = new OwnerPIN(PIN_TRY_LIMIT, PIN_MAX);
        pinSalt = new byte[16];
        wrappedDek = new byte[16];
        profileIv = new byte[16];
        profileCt = new byte[PROFILE_MAX];
        photoIv = new byte[16];
        photoCt = new byte[PHOTO_MAX];

        cardKeyPair = new KeyPair(KeyPair.ALG_RSA_CRT, RSA_BITS);
        issuerKey = (RSAPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PUBLIC, RSA_BITS, false);

        kek = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES_TRANSIENT_DESELECT, KeyBuilder.LENGTH_AES_128, false);
        dek = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES_TRANSIENT_DESELECT, KeyBuilder.LENGTH_AES_128, false);
        aesEcb = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);
        aesCbc = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        sha256 = MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);
        rng = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        cardSigner = Signature.getInstance(Signature.ALG_RSA_SHA_256_PKCS1, false);
        issuerVerifier = Signature.getInstance(Signature.ALG_RSA_SHA_256_PKCS1, false);

        scratch = JCSystem.makeTransientByteArray(SCRATCH_LEN, JCSystem.CLEAR_ON_DESELECT);
        challenge = JCSystem.makeTransientByteArray(CHALLENGE_LEN, JCSystem.CLEAR_ON_DESELECT);
        flags = JCSystem.makeTransientBooleanArray((short) 2, JCSystem.CLEAR_ON_DESELECT);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BusCardApplet().register();
    }

    public boolean select() {
        clearSession();
        return true;
    }

    public void deselect() {
        clearSession();
    }

    /**
     * Explicitly clears session security state. The transient arrays are CLEAR_ON_DESELECT already, but a
     * persisted simulator image (jcardsim + Kryo) may restore them as plain arrays, so do not rely on that.
     */
    private void clearSession() {
        pin.reset();
        flags[0] = false;
        flags[1] = false;
        Util.arrayFillNonAtomic(challenge, (short) 0, CHALLENGE_LEN, (byte) 0);
        Util.arrayFillNonAtomic(scratch, (short) 0, SCRATCH_LEN, (byte) 0);
        dek.clearKey();
        kek.clearKey();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_GET_INFO: getInfo(apdu); break;
            case INS_GET_PUBLIC_KEY: getPublicKey(apdu); break;
            case INS_INTERNAL_AUTH: internalAuth(apdu); break;
            case INS_GET_CHALLENGE: getChallenge(apdu); break;
            case INS_VERIFY_PIN: verifyPin(apdu); break;
            case INS_CHANGE_PIN: changePin(apdu); break;
            case INS_UNBLOCK_PIN: unblockPin(apdu); break;
            case INS_READ_PROFILE: readProfile(apdu); break;
            case INS_UPDATE_PROFILE: updateProfile(apdu); break;
            case INS_READ_PHOTO: readPhoto(apdu); break;
            case INS_WRITE_PHOTO: writePhoto(apdu); break;
            case INS_TOP_UP: topUp(apdu); break;
            case INS_TAP_IN: tapIn(apdu); break;
            case INS_TAP_OUT: tapOut(apdu); break;
            case INS_BUY_PASS: buyPass(apdu); break;
            case INS_GET_LAST_TX: getLastTx(apdu); break;
            case INS_SET_STATUS: setStatus(apdu); break;
            case INS_UPDATE_PARAMS: updateParams(apdu); break;
            case INS_PERSONALIZE: personalize(apdu); break;
            case INS_ACTIVATE: activate(apdu); break;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    // ================================================================ public info / authentication

    /**
     * state(1) lockReason(1) cardId(16) cardExpiry(4) category(1) balance(4) passType(1) passRoute(2)
     * passExpiry(4) onBoard(1) tripRoute(2) tripStation(2) tripTime(6) txCounter(4) pinTries(1) photoLen(2)
     * = 52 bytes.
     */
    private void getInfo(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short o = 0;
        buf[o++] = state;
        buf[o++] = lockReason;
        o = Util.arrayCopyNonAtomic(cardId, (short) 0, buf, o, CARD_ID_LEN);
        o = Util.arrayCopyNonAtomic(cardExpiry, (short) 0, buf, o, DATE_LEN);
        buf[o++] = category;
        o = Util.arrayCopyNonAtomic(balance, (short) 0, buf, o, AMOUNT_LEN);
        buf[o++] = passType;
        o = Util.arrayCopyNonAtomic(passRoute, (short) 0, buf, o, (short) 2);
        o = Util.arrayCopyNonAtomic(passExpiry, (short) 0, buf, o, DATE_LEN);
        buf[o++] = onBoard;
        o = Util.arrayCopyNonAtomic(tripRoute, (short) 0, buf, o, (short) 2);
        o = Util.arrayCopyNonAtomic(tripStation, (short) 0, buf, o, (short) 2);
        o = Util.arrayCopyNonAtomic(tripTime, (short) 0, buf, o, DATETIME_LEN);
        o = Util.arrayCopyNonAtomic(txCounter, (short) 0, buf, o, (short) 4);
        buf[o++] = pin.getTriesRemaining();
        o = Util.setShort(buf, o, photoLen);
        apdu.setOutgoingAndSend((short) 0, o);
    }

    /** modLen(2) modulus expLen(2) exponent. */
    private void getPublicKey(APDU apdu) {
        requireState(ST_PERSONALIZED, true);
        apdu.setOutgoingAndSend((short) 0, writeCardPublicKey(apdu.getBuffer()));
    }

    /** Data: reader challenge (8..32 bytes). Response: RSA-SHA256 signature over challenge || cardId || txCounter. */
    private void internalAuth(APDU apdu) {
        requireState(ST_PERSONALIZED, true);
        byte[] buf = apdu.getBuffer();
        short len = receive(apdu);
        if (len < 8 || len > 32) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        // Move the challenge out of the way: the signature is written at offset 0.
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, scratch, S_DATA, len);
        cardSigner.init(cardKeyPair.getPrivate(), Signature.MODE_SIGN);
        cardSigner.update(scratch, S_DATA, len);
        cardSigner.update(cardId, (short) 0, CARD_ID_LEN);
        short sigLen = cardSigner.sign(txCounter, (short) 0, (short) 4, buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, sigLen);
    }

    /** Fresh 16-byte challenge for one issuer-authorized command in this session. */
    private void getChallenge(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        rng.generateData(challenge, (short) 0, CHALLENGE_LEN);
        flags[0] = true;
        Util.arrayCopyNonAtomic(challenge, (short) 0, buf, (short) 0, CHALLENGE_LEN);
        apdu.setOutgoingAndSend((short) 0, CHALLENGE_LEN);
    }

    /**
     * Verifies issuer signature over challenge(16) || cardId(16) || INS(1) || payload.
     * The signature (128 bytes) follows the payload in the command data. Consumes the challenge.
     */
    private void verifyIssuer(byte ins, byte[] buf, short payloadOff, short payloadLen) {
        if (!flags[0]) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        flags[0] = false;
        issuerVerifier.init(issuerKey, Signature.MODE_VERIFY);
        issuerVerifier.update(challenge, (short) 0, CHALLENGE_LEN);
        issuerVerifier.update(cardId, (short) 0, CARD_ID_LEN);
        scratch[S_DATA] = ins;
        issuerVerifier.update(scratch, S_DATA, (short) 1);
        boolean ok = issuerVerifier.verify(buf, payloadOff, payloadLen,
                buf, (short) (payloadOff + payloadLen), RSA_LEN);
        Util.arrayFillNonAtomic(challenge, (short) 0, CHALLENGE_LEN, (byte) 0);
        if (!ok) {
            ISOException.throwIt(SW_ISSUER_AUTH_FAILED);
        }
    }

    /** Receives an issuer-signed command and returns the payload length (data minus signature). */
    private short receiveIssuerCommand(APDU apdu, byte ins) {
        short len = receive(apdu);
        if (len < RSA_LEN) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short payloadLen = (short) (len - RSA_LEN);
        verifyIssuer(ins, apdu.getBuffer(), ISO7816.OFFSET_CDATA, payloadLen);
        return payloadLen;
    }

    // ================================================================ lifecycle

    /**
     * Data: cardId(16) cardExpiry(4) category(1) issuerModulus(128) issuerExpLen(1) issuerExp pinLen(1) pin.
     * Generates the card key pair on-card. Response: card public key.
     */
    private void personalize(APDU apdu) {
        if (state != ST_INSTALLED) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        byte[] buf = apdu.getBuffer();
        short len = receive(apdu);
        short o = ISO7816.OFFSET_CDATA;
        short end = (short) (o + len);
        if (len < (short) (CARD_ID_LEN + DATE_LEN + 1 + RSA_LEN + 1)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        Util.arrayCopy(buf, o, cardId, (short) 0, CARD_ID_LEN);
        o += CARD_ID_LEN;
        Util.arrayCopy(buf, o, cardExpiry, (short) 0, DATE_LEN);
        o += DATE_LEN;
        category = buf[o++];
        issuerKey.setModulus(buf, o, RSA_LEN);
        o += RSA_LEN;
        short expLen = (short) (buf[o++] & 0xFF);
        if (expLen == 0 || expLen > 4 || (short) (o + expLen + 1) > end) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        issuerKey.setExponent(buf, o, expLen);
        o += expLen;
        short pinLen = (short) (buf[o++] & 0xFF);
        if ((short) (o + pinLen) != end) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        resetPinAndDataKey(buf, o, pinLen);

        cardKeyPair.genKeyPair();
        Util.arrayFillNonAtomic(balance, (short) 0, AMOUNT_LEN, (byte) 0);
        Util.arrayFillNonAtomic(txCounter, (short) 0, (short) 4, (byte) 0);
        state = ST_PERSONALIZED;
        apdu.setOutgoingAndSend((short) 0, writeCardPublicKey(buf));
    }

    /** Issuer-signed, empty payload. PERSONALIZED -> ACTIVE. */
    private void activate(APDU apdu) {
        if (state != ST_PERSONALIZED) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        if (receiveIssuerCommand(apdu, INS_ACTIVATE) != 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        state = ST_ACTIVE;
    }

    /** Issuer-signed. Payload: newState(1) = ACTIVE | LOCKED | REVOKED. */
    private void setStatus(APDU apdu) {
        if (state != ST_ACTIVE && state != ST_LOCKED) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        if (receiveIssuerCommand(apdu, INS_SET_STATUS) != 1) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte target = apdu.getBuffer()[ISO7816.OFFSET_CDATA];
        JCSystem.beginTransaction();
        if (target == ST_LOCKED) {
            state = ST_LOCKED;
            lockReason = LOCK_ADMIN;
        } else if (target == ST_REVOKED) {
            state = ST_REVOKED;
        } else if (target == ST_ACTIVE) {
            if (state == ST_LOCKED && lockReason == LOCK_PIN) {
                // A PIN-blocked card must be recovered via UNBLOCK_PIN (new PIN), not a plain unlock.
                JCSystem.abortTransaction();
                ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            }
            state = ST_ACTIVE;
            lockReason = LOCK_NONE;
        } else {
            JCSystem.abortTransaction();
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        JCSystem.commitTransaction();
    }

    /** Issuer-signed. Payload: category(1) cardExpiry(4). */
    private void updateParams(APDU apdu) {
        requireState(ST_PERSONALIZED, true);
        if (receiveIssuerCommand(apdu, INS_UPDATE_PARAMS) != (short) (1 + DATE_LEN)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] buf = apdu.getBuffer();
        JCSystem.beginTransaction();
        category = buf[ISO7816.OFFSET_CDATA];
        Util.arrayCopy(buf, (short) (ISO7816.OFFSET_CDATA + 1), cardExpiry, (short) 0, DATE_LEN);
        JCSystem.commitTransaction();
    }

    // ================================================================ PIN & data key

    private void verifyPin(APDU apdu) {
        if (state == ST_LOCKED) {
            ISOException.throwIt(lockReason == LOCK_PIN ? SW_PIN_BLOCKED : SW_CARD_BLOCKED);
        }
        if (state != ST_PERSONALIZED && state != ST_ACTIVE) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        byte[] buf = apdu.getBuffer();
        short len = receive(apdu);
        if (pin.getTriesRemaining() == 0) {
            ISOException.throwIt(SW_PIN_BLOCKED);
        }
        if (len < PIN_MIN || len > PIN_MAX || !pin.check(buf, ISO7816.OFFSET_CDATA, (byte) len)) {
            byte left = pin.getTriesRemaining();
            if (left == 0) {
                JCSystem.beginTransaction();
                state = ST_LOCKED;
                lockReason = LOCK_PIN;
                JCSystem.commitTransaction();
                ISOException.throwIt(SW_PIN_BLOCKED);
            }
            ISOException.throwIt((short) (SW_PIN_TRIES_BASE | left));
        }
        // Correct PIN: unwrap DEK with KEK derived from the PIN.
        deriveKek(buf, ISO7816.OFFSET_CDATA, len);
        aesEcb.init(kek, Cipher.MODE_DECRYPT);
        aesEcb.doFinal(wrappedDek, (short) 0, (short) 16, scratch, S_KEY);
        dek.setKey(scratch, S_KEY);
        Util.arrayFillNonAtomic(scratch, S_DIGEST, S_AMOUNT, (byte) 0);
        flags[1] = true;
    }

    /** Requires PIN. Data: newPin. Re-wraps the existing DEK under the new PIN; profile stays readable. */
    private void changePin(APDU apdu) {
        requirePin();
        byte[] buf = apdu.getBuffer();
        short len = receive(apdu);
        checkPinLength(len);
        dek.getKey(scratch, S_KEY);
        JCSystem.beginTransaction();
        pin.update(buf, ISO7816.OFFSET_CDATA, (byte) len);
        rng.generateData(pinSalt, (short) 0, (short) 16);
        wrapDek(buf, ISO7816.OFFSET_CDATA, len);
        JCSystem.commitTransaction();
        Util.arrayFillNonAtomic(scratch, S_DIGEST, S_AMOUNT, (byte) 0);
        // pin.update() clears the validated flag; keep the session unlocked for the cardholder.
        pin.check(buf, ISO7816.OFFSET_CDATA, (byte) len);
    }

    /**
     * Issuer-signed. Payload: pinLen(1) pin. Sets a new PIN and a new DEK; the old encrypted profile/photo
     * cannot be recovered without the old PIN, so they are wiped and must be re-written from the backend.
     */
    private void unblockPin(APDU apdu) {
        requireState(ST_PERSONALIZED, true);
        short payloadLen = receiveIssuerCommand(apdu, INS_UNBLOCK_PIN);
        byte[] buf = apdu.getBuffer();
        short pinLen = (short) (buf[ISO7816.OFFSET_CDATA] & 0xFF);
        if (payloadLen != (short) (pinLen + 1)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        checkPinLength(pinLen);
        JCSystem.beginTransaction();
        resetPinAndDataKey(buf, (short) (ISO7816.OFFSET_CDATA + 1), pinLen);
        if (state == ST_LOCKED && lockReason == LOCK_PIN) {
            state = ST_ACTIVE;
            lockReason = LOCK_NONE;
        }
        JCSystem.commitTransaction();
    }

    /** New PIN, new salt, new random DEK; wipes encrypted data. */
    private void resetPinAndDataKey(byte[] pinBuf, short pinOff, short pinLen) {
        checkPinLength(pinLen);
        pin.update(pinBuf, pinOff, (byte) pinLen);
        rng.generateData(pinSalt, (short) 0, (short) 16);
        rng.generateData(scratch, S_KEY, (short) 16);
        wrapDek(pinBuf, pinOff, pinLen);
        Util.arrayFillNonAtomic(scratch, S_DIGEST, S_AMOUNT, (byte) 0);
        profileLen = 0;
        photoLen = 0;
        flags[1] = false;
    }

    /** wrappedDek = AES-ECB(KEK(pin, salt), scratch[S_KEY..+16]). */
    private void wrapDek(byte[] pinBuf, short pinOff, short pinLen) {
        deriveKek(pinBuf, pinOff, pinLen);
        aesEcb.init(kek, Cipher.MODE_ENCRYPT);
        aesEcb.doFinal(scratch, S_KEY, (short) 16, scratch, S_DIGEST);
        Util.arrayCopy(scratch, S_DIGEST, wrappedDek, (short) 0, (short) 16);
    }

    /** KEK = SHA-256(pin || salt)[0..16]. */
    private void deriveKek(byte[] pinBuf, short pinOff, short pinLen) {
        sha256.reset();
        sha256.update(pinBuf, pinOff, pinLen);
        sha256.doFinal(pinSalt, (short) 0, (short) 16, scratch, S_DIGEST);
        kek.setKey(scratch, S_DIGEST);
    }

    private void checkPinLength(short len) {
        if (len < PIN_MIN || len > PIN_MAX) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
    }

    private void requirePin() {
        requireState(ST_PERSONALIZED, false);
        if (!pin.isValidated() || !flags[1]) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
    }

    // ================================================================ encrypted profile & photo

    private void readProfile(APDU apdu) {
        requirePin();
        byte[] buf = apdu.getBuffer();
        if (profileLen == 0) {
            apdu.setOutgoingAndSend((short) 0, (short) 0);
            return;
        }
        short padded = padTo16(profileLen);
        aesCbc.init(dek, Cipher.MODE_DECRYPT, profileIv, (short) 0, (short) 16);
        aesCbc.doFinal(profileCt, (short) 0, padded, scratch, S_DATA);
        Util.arrayCopyNonAtomic(scratch, S_DATA, buf, (short) 0, profileLen);
        Util.arrayFillNonAtomic(scratch, S_DATA, padded, (byte) 0);
        apdu.setOutgoingAndSend((short) 0, profileLen);
    }

    /** Data: opaque profile blob (host-defined TLV), 1..192 bytes. */
    private void updateProfile(APDU apdu) {
        requirePin();
        byte[] buf = apdu.getBuffer();
        short len = receive(apdu);
        if (len == 0 || len > PROFILE_MAX) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short padded = padTo16(len);
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, scratch, S_DATA, len);
        Util.arrayFillNonAtomic(scratch, (short) (S_DATA + len), (short) (padded - len), (byte) 0);
        rng.generateData(scratch, S_KEY, (short) 16);
        aesCbc.init(dek, Cipher.MODE_ENCRYPT, scratch, S_KEY, (short) 16);
        aesCbc.doFinal(scratch, S_DATA, padded, scratch, S_DATA);
        JCSystem.beginTransaction();
        Util.arrayCopy(scratch, S_KEY, profileIv, (short) 0, (short) 16);
        Util.arrayCopy(scratch, S_DATA, profileCt, (short) 0, padded);
        profileLen = len;
        JCSystem.commitTransaction();
    }

    /** P1P2 = chunk index. Returns up to 224 decrypted bytes. */
    private void readPhoto(APDU apdu) {
        requirePin();
        byte[] buf = apdu.getBuffer();
        short index = Util.getShort(buf, ISO7816.OFFSET_P1);
        if (index < 0 || index >= (short) 37) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        short off = (short) (index * PHOTO_CHUNK);
        if (off >= photoLen) {
            ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
        }
        short n = (short) (photoLen - off);
        if (n > PHOTO_CHUNK) {
            n = PHOTO_CHUNK;
        }
        chunkIv(index);
        aesCbc.init(dek, Cipher.MODE_DECRYPT, scratch, S_KEY, (short) 16);
        aesCbc.doFinal(photoCt, off, n, buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, n);
    }

    /**
     * P1P2 = chunk index (must be sequential; index 0 starts a new photo). Data: 16..224 bytes, multiple of 16
     * (host pads the last chunk).
     */
    private void writePhoto(APDU apdu) {
        requirePin();
        byte[] buf = apdu.getBuffer();
        short index = Util.getShort(buf, ISO7816.OFFSET_P1);
        short len = receive(apdu);
        if (len == 0 || len > PHOTO_CHUNK || (short) (len & 0x0F) != 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        if (index < 0 || index >= (short) 37) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        short off = (short) (index * PHOTO_CHUNK);
        if (index == 0) {
            rng.generateData(photoIv, (short) 0, (short) 16);
            photoLen = 0;
        } else if (off != photoLen) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        chunkIv(index);
        aesCbc.init(dek, Cipher.MODE_ENCRYPT, scratch, S_KEY, (short) 16);
        aesCbc.doFinal(buf, ISO7816.OFFSET_CDATA, len, scratch, S_DATA);
        JCSystem.beginTransaction();
        Util.arrayCopy(scratch, S_DATA, photoCt, off, len);
        photoLen = (short) (off + len);
        JCSystem.commitTransaction();
    }

    /** Per-chunk IV = photoIv with the last two bytes XOR chunk index. */
    private void chunkIv(short index) {
        Util.arrayCopyNonAtomic(photoIv, (short) 0, scratch, S_KEY, (short) 16);
        scratch[(short) (S_KEY + 14)] ^= (byte) (index >> 8);
        scratch[(short) (S_KEY + 15)] ^= (byte) index;
    }

    // ================================================================ wallet & trips

    /** Requires PIN + issuer signature. Payload: amount(4) dateTime(6). Response: signed record. */
    private void topUp(APDU apdu) {
        requireActive();
        requirePin();
        if (receiveIssuerCommand(apdu, INS_TOP_UP) != (short) (AMOUNT_LEN + DATETIME_LEN)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] buf = apdu.getBuffer();
        short amountOff = ISO7816.OFFSET_CDATA;
        short dtOff = (short) (amountOff + AMOUNT_LEN);
        checkNotExpired(buf, dtOff);
        if (Util32.isZero(buf, amountOff)) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        // new balance -> scratch[S_AMOUNT2]
        if (Util32.add(balance, (short) 0, buf, amountOff, scratch, S_AMOUNT2)
                || Util32.compare(scratch, S_AMOUNT2, maxBalance, (short) 0) > 0) {
            ISOException.throwIt(SW_BALANCE_LIMIT);
        }
        JCSystem.beginTransaction();
        Util.arrayCopy(scratch, S_AMOUNT2, balance, (short) 0, AMOUNT_LEN);
        recordTx(TX_TOP_UP, buf, amountOff, null, (short) 0, null, (short) 0, buf, dtOff, (byte) 0);
        JCSystem.commitTransaction();
        sendLastRecord(apdu);
    }

    /**
     * Data: route(2) station(2) dateTime(6) fare(4) penalty(4) routeMode(1) = 19 bytes.
     * <ul>
     *   <li>FLAT route: {@code fare} is charged now (unless covered by a monthly pass).</li>
     *   <li>DISTANCE route: nothing charged now; {@code fare} is the minimum balance required to board.</li>
     *   <li>If the previous distance trip was never closed, {@code penalty} (max fare of that route) is charged.</li>
     * </ul>
     */
    private void tapIn(APDU apdu) {
        requireActive();
        byte[] buf = apdu.getBuffer();
        if (receive(apdu) != 19) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short routeOff = ISO7816.OFFSET_CDATA;
        short stationOff = (short) (routeOff + 2);
        short dtOff = (short) (stationOff + 2);
        short fareOff = (short) (dtOff + DATETIME_LEN);
        short penaltyOff = (short) (fareOff + AMOUNT_LEN);
        byte mode = buf[(short) (penaltyOff + AMOUNT_LEN)];
        if (mode != ROUTE_FLAT && mode != ROUTE_DISTANCE) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        checkNotExpired(buf, dtOff);

        byte txFlags = 0;
        Util.arrayFillNonAtomic(scratch, S_AMOUNT, AMOUNT_LEN, (byte) 0); // amount charged now
        if (onBoard != 0) {
            if (passCovers(tripRoute, (short) 0, buf, dtOff)) {
                txFlags |= FLAG_PASS_USED;
            } else {
                Util.arrayCopyNonAtomic(buf, penaltyOff, scratch, S_AMOUNT, AMOUNT_LEN);
                txFlags |= FLAG_PENALTY;
            }
        }
        boolean covered = passCovers(buf, routeOff, buf, dtOff);
        if (covered) {
            txFlags |= FLAG_PASS_USED;
        }
        // required = charged now + (fare if not covered); for DISTANCE the fare is only a balance check.
        Util.arrayCopyNonAtomic(scratch, S_AMOUNT, scratch, S_AMOUNT2, AMOUNT_LEN);
        if (!covered) {
            Util32.add(scratch, S_AMOUNT, buf, fareOff, scratch, S_AMOUNT2);
            if (mode == ROUTE_FLAT) {
                Util.arrayCopyNonAtomic(scratch, S_AMOUNT2, scratch, S_AMOUNT, AMOUNT_LEN);
            }
        }
        if (Util32.compare(balance, (short) 0, scratch, S_AMOUNT2) < 0) {
            rejectNoFunds(buf, dtOff);
        }

        JCSystem.beginTransaction();
        Util32.sub(balance, (short) 0, scratch, S_AMOUNT, balance, (short) 0);
        onBoard = (mode == ROUTE_DISTANCE) ? (byte) 1 : (byte) 0;
        Util.arrayCopy(buf, routeOff, tripRoute, (short) 0, (short) 2);
        Util.arrayCopy(buf, stationOff, tripStation, (short) 0, (short) 2);
        Util.arrayCopy(buf, dtOff, tripTime, (short) 0, DATETIME_LEN);
        recordTx(TX_TAP_IN, scratch, S_AMOUNT, buf, routeOff, buf, stationOff, buf, dtOff, txFlags);
        JCSystem.commitTransaction();
        sendLastRecord(apdu);
    }

    /** Data: route(2) station(2) dateTime(6) fare(4) = 14 bytes. Closes an open DISTANCE trip. */
    private void tapOut(APDU apdu) {
        requireActive();
        byte[] buf = apdu.getBuffer();
        if (receive(apdu) != 14) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short routeOff = ISO7816.OFFSET_CDATA;
        short stationOff = (short) (routeOff + 2);
        short dtOff = (short) (stationOff + 2);
        short fareOff = (short) (dtOff + DATETIME_LEN);
        if (onBoard == 0 || Util.arrayCompare(buf, routeOff, tripRoute, (short) 0, (short) 2) != 0) {
            ISOException.throwIt(SW_NOT_ON_BOARD);
        }
        byte txFlags = 0;
        if (passCovers(tripRoute, (short) 0, buf, dtOff)) {
            txFlags = FLAG_PASS_USED;
            Util.arrayFillNonAtomic(scratch, S_AMOUNT, AMOUNT_LEN, (byte) 0);
        } else {
            Util.arrayCopyNonAtomic(buf, fareOff, scratch, S_AMOUNT, AMOUNT_LEN);
        }
        if (Util32.compare(balance, (short) 0, scratch, S_AMOUNT) < 0) {
            ISOException.throwIt(SW_INSUFFICIENT_FUNDS);
        }
        JCSystem.beginTransaction();
        Util32.sub(balance, (short) 0, scratch, S_AMOUNT, balance, (short) 0);
        onBoard = 0;
        recordTx(TX_TAP_OUT, scratch, S_AMOUNT, buf, routeOff, buf, stationOff, buf, dtOff, txFlags);
        JCSystem.commitTransaction();
        sendLastRecord(apdu);
    }

    /**
     * Requires PIN. Data: passType(1) route(2) newExpiry(4) price(4) dateTime(6) = 17 bytes.
     * The terminal computes the new expiry (extending a still-valid pass); the card debits atomically.
     */
    private void buyPass(APDU apdu) {
        requireActive();
        requirePin();
        byte[] buf = apdu.getBuffer();
        if (receive(apdu) != 17) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short typeOff = ISO7816.OFFSET_CDATA;
        short routeOff = (short) (typeOff + 1);
        short expOff = (short) (routeOff + 2);
        short priceOff = (short) (expOff + DATE_LEN);
        short dtOff = (short) (priceOff + AMOUNT_LEN);
        byte type = buf[typeOff];
        if (type != PASS_SINGLE_ROUTE && type != PASS_INTERLINE) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        checkNotExpired(buf, dtOff);
        if (Util32.compare(buf, expOff, buf, dtOff) <= 0) { // new expiry must be after today
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        if (Util32.compare(balance, (short) 0, buf, priceOff) < 0) {
            ISOException.throwIt(SW_INSUFFICIENT_FUNDS);
        }
        JCSystem.beginTransaction();
        Util32.sub(balance, (short) 0, buf, priceOff, balance, (short) 0);
        passType = type;
        Util.arrayCopy(buf, routeOff, passRoute, (short) 0, (short) 2);
        Util.arrayCopy(buf, expOff, passExpiry, (short) 0, DATE_LEN);
        recordTx(TX_BUY_PASS, buf, priceOff, buf, routeOff, null, (short) 0, buf, dtOff, type);
        JCSystem.commitTransaction();
        sendLastRecord(apdu);
    }

    private void getLastTx(APDU apdu) {
        requireState(ST_PERSONALIZED, true);
        sendLastRecord(apdu);
    }

    /** True if a monthly pass is valid on {@code date} for the given route. */
    private boolean passCovers(byte[] route, short routeOff, byte[] date, short dateOff) {
        if (passType == PASS_NONE || Util32.compare(date, dateOff, passExpiry, (short) 0) > 0) {
            return false;
        }
        return passType == PASS_INTERLINE
                || Util.arrayCompare(route, routeOff, passRoute, (short) 0, (short) 2) == 0;
    }

    /** Distinguishes "pass is for another route" from plain insufficient balance. */
    private void rejectNoFunds(byte[] buf, short dtOff) {
        if (passType != PASS_NONE && Util32.compare(buf, dtOff, passExpiry, (short) 0) <= 0) {
            ISOException.throwIt(SW_PASS_WRONG_ROUTE);
        }
        ISOException.throwIt(SW_INSUFFICIENT_FUNDS);
    }

    /** Must be called inside a transaction: increments the counter and writes {@link #lastRecord}. */
    private void recordTx(byte type, byte[] amt, short amtOff, byte[] route, short routeOff,
                          byte[] station, short stationOff, byte[] dt, short dtOff, byte txFlags) {
        Util32.increment(txCounter, (short) 0);
        Util.arrayCopy(cardId, (short) 0, lastRecord, (short) 0, CARD_ID_LEN);
        Util.arrayCopy(txCounter, (short) 0, lastRecord, REC_COUNTER, (short) 4);
        lastRecord[REC_TYPE] = type;
        Util.arrayCopy(amt, amtOff, lastRecord, REC_AMOUNT, AMOUNT_LEN);
        Util.arrayCopy(balance, (short) 0, lastRecord, REC_BALANCE, AMOUNT_LEN);
        if (route != null) {
            Util.arrayCopy(route, routeOff, lastRecord, REC_ROUTE, (short) 2);
        } else {
            Util.arrayFillNonAtomic(lastRecord, REC_ROUTE, (short) 2, (byte) 0);
        }
        if (station != null) {
            Util.arrayCopy(station, stationOff, lastRecord, REC_STATION, (short) 2);
        } else {
            Util.arrayFillNonAtomic(lastRecord, REC_STATION, (short) 2, (byte) 0);
        }
        Util.arrayCopy(dt, dtOff, lastRecord, REC_TIME, DATETIME_LEN);
        lastRecord[REC_FLAGS] = txFlags;
    }

    /** Response: record(40) || RSA-SHA256 signature(128). */
    private void sendLastRecord(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(lastRecord, (short) 0, buf, (short) 0, REC_LEN);
        cardSigner.init(cardKeyPair.getPrivate(), Signature.MODE_SIGN);
        short sigLen = cardSigner.sign(lastRecord, (short) 0, REC_LEN, buf, REC_LEN);
        apdu.setOutgoingAndSend((short) 0, (short) (REC_LEN + sigLen));
    }

    // ================================================================ helpers

    private void checkNotExpired(byte[] buf, short dtOff) {
        if (Util32.compare(buf, dtOff, cardExpiry, (short) 0) > 0) {
            ISOException.throwIt(SW_CARD_EXPIRED);
        }
    }

    private void requireActive() {
        if (state == ST_LOCKED || state == ST_REVOKED) {
            ISOException.throwIt(SW_CARD_BLOCKED);
        }
        if (state != ST_ACTIVE) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
    }

    /**
     * Requires state >= minState in lifecycle order (INSTALLED < PERSONALIZED < ACTIVE < LOCKED) and not REVOKED.
     * If {@code allowLocked} is false, LOCKED cards are rejected too.
     */
    private void requireState(byte minState, boolean allowLocked) {
        if (state == ST_REVOKED || (!allowLocked && state == ST_LOCKED)) {
            ISOException.throwIt(SW_CARD_BLOCKED);
        }
        if (state < minState) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
    }

    private short writeCardPublicKey(byte[] buf) {
        RSAPublicKey pub = (RSAPublicKey) cardKeyPair.getPublic();
        short modLen = pub.getModulus(buf, (short) 2);
        Util.setShort(buf, (short) 0, modLen);
        short expLenOff = (short) (2 + modLen);
        short expLen = pub.getExponent(buf, (short) (expLenOff + 2));
        Util.setShort(buf, expLenOff, expLen);
        return (short) (expLenOff + 2 + expLen);
    }

    private static short receive(APDU apdu) {
        short len = apdu.setIncomingAndReceive();
        if (len != apdu.getIncomingLength()) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        return len;
    }

    private static short padTo16(short len) {
        return (short) ((short) (len + 15) & (short) 0xFFF0);
    }
}
