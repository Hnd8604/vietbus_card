package com.buscard.common;

import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.LockReason;
import com.buscard.common.CardConstants.PassType;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Public card data returned by GET_INFO (no PIN required). */
public record CardInfo(
        CardState state,
        LockReason lockReason,
        String cardId,
        LocalDate cardExpiry,
        Category category,
        long balance,
        PassType passType,
        int passRoute,
        LocalDate passExpiry,
        boolean onBoard,
        int tripRoute,
        int tripStation,
        LocalDateTime tripTime,
        long txCounter,
        int pinTriesRemaining,
        int photoLength) {

    public static final int LENGTH = 52;

    public static CardInfo parse(byte[] b) {
        if (b.length != LENGTH) {
            throw new IllegalArgumentException("GET_INFO length " + b.length);
        }
        int o = 0;
        CardState state = CardState.of(b[o++]);
        LockReason lock = LockReason.of(b[o++]);
        byte[] id = new byte[CardConstants.CARD_ID_LEN];
        System.arraycopy(b, o, id, 0, id.length);
        o += id.length;
        LocalDate expiry = CardCodec.readDate(b, o);
        o += 4;
        Category category = state == CardState.INSTALLED ? Category.NORMAL : Category.of(b[o]);
        o++;
        long balance = CardCodec.readAmount(b, o);
        o += 4;
        PassType passType = PassType.of(b[o++]);
        int passRoute = CardCodec.readU16(b, o);
        o += 2;
        LocalDate passExpiry = CardCodec.readDate(b, o);
        o += 4;
        boolean onBoard = b[o++] != 0;
        int tripRoute = CardCodec.readU16(b, o);
        o += 2;
        int tripStation = CardCodec.readU16(b, o);
        o += 2;
        LocalDateTime tripTime = CardCodec.readDateTime(b, o);
        o += 6;
        long counter = CardCodec.readAmount(b, o);
        o += 4;
        int tries = b[o++];
        int photoLen = CardCodec.readU16(b, o);
        return new CardInfo(state, lock, CardIds.toText(id), expiry, category, balance, passType, passRoute,
                passExpiry, onBoard, tripRoute, tripStation, tripTime, counter, tries, photoLen);
    }

    /** Card expiry is inclusive: a card expiring today still works today. */
    public boolean isExpiredOn(LocalDate day) {
        return cardExpiry != null && day.isAfter(cardExpiry);
    }

    public boolean hasValidPass(LocalDate day) {
        return passType != PassType.NONE && passExpiry != null && !day.isAfter(passExpiry);
    }

    public boolean passCoversRoute(int routeId, LocalDate day) {
        return hasValidPass(day) && (passType == PassType.INTERLINE || passRoute == routeId);
    }
}
