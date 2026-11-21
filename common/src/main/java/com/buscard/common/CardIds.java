package com.buscard.common;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Card ids are printable ASCII (e.g. "BC2026000001"), right-padded with zero bytes to 16 bytes on the card.
 */
public final class CardIds {

    private CardIds() {
    }

    public static byte[] toBytes(String cardId) {
        byte[] ascii = cardId.getBytes(StandardCharsets.US_ASCII);
        if (ascii.length == 0 || ascii.length > CardConstants.CARD_ID_LEN) {
            throw new IllegalArgumentException("Card id must be 1..16 ASCII chars: " + cardId);
        }
        return Arrays.copyOf(ascii, CardConstants.CARD_ID_LEN);
    }

    public static String toText(byte[] raw) {
        int n = 0;
        while (n < raw.length && raw[n] != 0) {
            n++;
        }
        return new String(raw, 0, n, StandardCharsets.US_ASCII);
    }
}
