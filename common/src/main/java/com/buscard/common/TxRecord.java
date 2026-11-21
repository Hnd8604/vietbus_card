package com.buscard.common;

import com.buscard.common.CardConstants.TxType;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * A card-signed transaction record (40 bytes + 128-byte RSA-SHA256 signature).
 *
 * <p>Layout: cardId(16) counter(4) type(1) amount(4) balanceAfter(4) route(2) station(2) dateTime(6) flags(1).
 */
public record TxRecord(
        String cardId,
        long counter,
        TxType type,
        long amount,
        long balanceAfter,
        int route,
        int station,
        LocalDateTime time,
        int flags,
        byte[] raw,
        byte[] signature) {

    public static final int LENGTH = 40;

    /** Parses the response of TOP_UP / TAP_IN / TAP_OUT / BUY_PASS / GET_LAST_TX. */
    public static TxRecord parse(byte[] response) {
        if (response.length != LENGTH + CardConstants.RSA_LEN) {
            throw new IllegalArgumentException("Tx response length " + response.length);
        }
        byte[] raw = Arrays.copyOf(response, LENGTH);
        byte[] sig = Arrays.copyOfRange(response, LENGTH, response.length);
        return fromRaw(raw, sig);
    }

    public static TxRecord fromRaw(byte[] raw, byte[] sig) {
        String id = CardIds.toText(Arrays.copyOf(raw, CardConstants.CARD_ID_LEN));
        return new TxRecord(id,
                CardCodec.readAmount(raw, 16),
                TxType.of(raw[20]),
                CardCodec.readAmount(raw, 21),
                CardCodec.readAmount(raw, 25),
                CardCodec.readU16(raw, 29),
                CardCodec.readU16(raw, 31),
                CardCodec.readDateTime(raw, 33),
                raw[39] & 0xFF,
                raw.clone(),
                sig.clone());
    }

    public boolean passUsed() {
        return (flags & CardConstants.FLAG_PASS_USED) != 0;
    }

    public boolean penaltyApplied() {
        return (flags & CardConstants.FLAG_PENALTY) != 0;
    }

    /** Verifies the card's signature with the card public key registered at issuance. */
    public boolean verify(PublicKey cardKey) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(cardKey);
            s.update(raw);
            return s.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
