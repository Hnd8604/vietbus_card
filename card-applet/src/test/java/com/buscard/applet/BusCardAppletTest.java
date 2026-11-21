package com.buscard.applet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buscard.common.BusCardClient;
import com.buscard.common.CardConstants;
import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.LockReason;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardConstants.RouteMode;
import com.buscard.common.CardConstants.TxType;
import com.buscard.common.CardInfo;
import com.buscard.common.CardStatusException;
import com.buscard.common.IssuerAuthority;
import com.buscard.common.PassengerProfile;
import com.buscard.common.RsaKeys;
import com.buscard.common.TxRecord;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;
import javacard.framework.AID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class BusCardAppletTest {

    static final AID AID_BUSCARD = AIDUtil.create(CardConstants.AID_HEX);
    static final String PIN = "123456";
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 7, 30);
    static final LocalDate CARD_EXPIRY = LocalDate.of(2030, 12, 31);
    static final int ROUTE_32 = 32;
    static final int ROUTE_86 = 86;

    static KeyPair issuerKeys;
    static IssuerAuthority issuer;

    CardSimulator sim;
    BusCardClient card;
    PublicKey cardKey;

    @BeforeAll
    static void issuer() {
        issuerKeys = RsaKeys.generate1024();
        issuer = IssuerAuthority.withKey(issuerKeys.getPrivate());
    }

    @BeforeEach
    void newCard() {
        sim = new CardSimulator();
        sim.installApplet(AID_BUSCARD, BusCardApplet.class);
        card = new BusCardClient(sim::transmitCommand);
        card.select();
        cardKey = card.personalize("BC2026000001", CARD_EXPIRY, Category.NORMAL,
                (RSAPublicKey) issuerKeys.getPublic(), PIN);
        card.activate(issuer);
    }

    // ------------------------------------------------------------------ lifecycle & authenticity

    @Test
    void personalizedCardReportsPublicInfo() {
        CardInfo info = card.getInfo();
        assertEquals(CardState.ACTIVE, info.state());
        assertEquals("BC2026000001", info.cardId());
        assertEquals(CARD_EXPIRY, info.cardExpiry());
        assertEquals(0, info.balance());
        assertEquals(CardConstants.PIN_TRY_LIMIT, info.pinTriesRemaining());
        assertEquals(cardKey, card.getPublicKey());
    }

    @Test
    void personalizeOnlyOnce() {
        expectSw(CardConstants.SW_CONDITIONS, () -> card.personalize("X", CARD_EXPIRY, Category.NORMAL,
                (RSAPublicKey) issuerKeys.getPublic(), PIN));
    }

    @Test
    void internalAuthProvesGenuineCardAndDetectsClone() {
        assertTrue(card.authenticate(cardKey, card.getInfo()));

        // A "clone" with the same id cannot produce signatures for the registered key.
        CardSimulator otherSim = new CardSimulator();
        otherSim.installApplet(AID_BUSCARD, BusCardApplet.class);
        BusCardClient clone = new BusCardClient(otherSim::transmitCommand);
        clone.select();
        clone.personalize("BC2026000001", CARD_EXPIRY, Category.NORMAL, (RSAPublicKey) issuerKeys.getPublic(), PIN);
        assertFalse(clone.authenticate(cardKey, clone.getInfo()));
    }

    // ------------------------------------------------------------------ PIN & encrypted profile

    @Test
    void profileRequiresPinAndRoundTrips() {
        PassengerProfile p = new PassengerProfile("Nguyễn Văn An", LocalDate.of(2004, 5, 17), "0912345678",
                Category.STUDENT);
        expectSw(CardConstants.SW_SECURITY_STATUS, () -> card.updateProfile(p));

        card.verifyPin(PIN);
        card.updateProfile(p);
        assertEquals(p, card.readProfile());

        // New session (re-select) => PIN required again.
        card.select();
        expectSw(CardConstants.SW_SECURITY_STATUS, () -> card.readProfile());
        card.verifyPin(PIN);
        assertEquals(p, card.readProfile());
    }

    @Test
    void changePinKeepsProfileReadable() {
        PassengerProfile p = sampleProfile();
        card.verifyPin(PIN);
        card.updateProfile(p);
        card.changePin("8888");

        card.select();
        expectSw(0x63C4, () -> card.verifyPin(PIN));
        card.verifyPin("8888");
        assertEquals(p, card.readProfile());
    }

    @Test
    void fiveWrongPinsLockCardUntilIssuerUnblocks() {
        card.verifyPin(PIN);
        card.updateProfile(sampleProfile());
        card.select();

        for (int left = 4; left >= 1; left--) {
            expectSw(0x63C0 | left, () -> card.verifyPin("0000"));
        }
        expectSw(CardConstants.SW_PIN_BLOCKED, () -> card.verifyPin("0000"));
        expectSw(CardConstants.SW_PIN_BLOCKED, () -> card.verifyPin(PIN)); // even the right PIN

        CardInfo info = card.getInfo();
        assertEquals(CardState.LOCKED, info.state());
        assertEquals(LockReason.PIN, info.lockReason());
        expectSw(CardConstants.SW_CARD_BLOCKED, () -> tapFlat(ROUTE_32, 7_000));
        // A PIN lock cannot be lifted by a plain status change.
        expectSw(CardConstants.SW_CONDITIONS, () -> card.setStatus(issuer, CardState.ACTIVE));

        card.unblockPin(issuer, "2468");
        assertEquals(CardState.ACTIVE, card.getInfo().state());
        card.verifyPin("2468");
        assertNull(card.readProfile(), "old profile is unrecoverable without the old PIN and must be wiped");
    }

    // ------------------------------------------------------------------ wallet

    @Test
    void topUpNeedsPinAndIssuerSignature() {
        expectSw(CardConstants.SW_SECURITY_STATUS, () -> card.topUp(issuer, 50_000, NOW));

        card.verifyPin(PIN);
        IssuerAuthority forged = IssuerAuthority.withKey(RsaKeys.generate1024().getPrivate());
        expectSw(CardConstants.SW_ISSUER_AUTH_FAILED, () -> card.topUp(forged, 50_000, NOW));
        assertEquals(0, card.getInfo().balance());

        TxRecord tx = card.topUp(issuer, 50_000, NOW);
        assertEquals(TxType.TOP_UP, tx.type());
        assertEquals(50_000, tx.amount());
        assertEquals(50_000, tx.balanceAfter());
        assertEquals(1, tx.counter());
        assertTrue(tx.verify(cardKey));
        assertEquals(50_000, card.getInfo().balance());
    }

    @Test
    void issuerSignatureCannotBeReplayed() {
        card.verifyPin(PIN);
        byte[][] captured = new byte[1][];
        IssuerAuthority recording = (ch, id, ins, payload) -> captured[0] = issuer.sign(ch, id, ins, payload);
        card.topUp(recording, 20_000, NOW);
        // Replaying the captured signature against a new challenge must fail.
        expectSw(CardConstants.SW_ISSUER_AUTH_FAILED, () -> card.topUp((ch, id, ins, p) -> captured[0], 20_000, NOW));
        assertEquals(20_000, card.getInfo().balance());
    }

    @Test
    void walletCapIsEnforced() {
        card.verifyPin(PIN);
        card.topUp(issuer, 9_000_000, NOW);
        expectSw(CardConstants.SW_BALANCE_LIMIT, () -> card.topUp(issuer, 2_000_000, NOW));
        assertEquals(9_000_000, card.getInfo().balance());
    }

    // ------------------------------------------------------------------ taps

    @Test
    void flatRouteChargesAtTapInWithSignedRecord() {
        topUp(20_000);
        TxRecord tx = tapFlat(ROUTE_32, 7_000);
        assertEquals(TxType.TAP_IN, tx.type());
        assertEquals(7_000, tx.amount());
        assertEquals(13_000, tx.balanceAfter());
        assertEquals(ROUTE_32, tx.route());
        assertEquals(NOW, tx.time());
        assertEquals(2, tx.counter());
        assertTrue(tx.verify(cardKey));
        assertFalse(card.getInfo().onBoard());
        assertEquals(tx.counter(), card.getLastTx().counter());
    }

    @Test
    void insufficientFundsIsRejectedAtomically() {
        topUp(5_000);
        expectSw(CardConstants.SW_INSUFFICIENT_FUNDS, () -> tapFlat(ROUTE_32, 7_000));
        CardInfo info = card.getInfo();
        assertEquals(5_000, info.balance());
        assertEquals(1, info.txCounter(), "a rejected tap must not consume a counter value");
    }

    @Test
    void distanceRouteChargesAtTapOut() {
        topUp(30_000);
        TxRecord in = card.tapIn(ROUTE_86, 3, NOW, 5_000, 0, RouteMode.DISTANCE);
        assertEquals(0, in.amount());
        CardInfo info = card.getInfo();
        assertTrue(info.onBoard());
        assertEquals(ROUTE_86, info.tripRoute());
        assertEquals(3, info.tripStation());

        expectSw(CardConstants.SW_NOT_ON_BOARD, () -> card.tapOut(ROUTE_32, 9, NOW.plusMinutes(20), 9_000));
        TxRecord out = card.tapOut(ROUTE_86, 9, NOW.plusMinutes(20), 9_000);
        assertEquals(TxType.TAP_OUT, out.type());
        assertEquals(9_000, out.amount());
        assertEquals(21_000, out.balanceAfter());
        assertFalse(card.getInfo().onBoard());
        expectSw(CardConstants.SW_NOT_ON_BOARD, () -> card.tapOut(ROUTE_86, 9, NOW.plusMinutes(21), 9_000));
    }

    @Test
    void distanceRouteRequiresMinimumBalanceToBoard() {
        topUp(3_000);
        expectSw(CardConstants.SW_INSUFFICIENT_FUNDS, () -> card.tapIn(ROUTE_86, 1, NOW, 5_000, 0, RouteMode.DISTANCE));
        assertFalse(card.getInfo().onBoard());
    }

    @Test
    void forgottenTapOutIsChargedPenaltyOnNextTapIn() {
        topUp(50_000);
        card.tapIn(ROUTE_86, 1, NOW, 5_000, 0, RouteMode.DISTANCE);
        TxRecord next = card.tapIn(ROUTE_32, 4, NOW.plusHours(2), 7_000, 15_000, RouteMode.FLAT);
        assertTrue(next.penaltyApplied());
        assertEquals(22_000, next.amount());
        assertEquals(28_000, next.balanceAfter());
    }

    @Test
    void expiredCardIsRejected() {
        topUp(20_000);
        LocalDateTime afterExpiry = CARD_EXPIRY.plusDays(1).atTime(8, 0);
        expectSw(CardConstants.SW_CARD_EXPIRED,
                () -> card.tapIn(ROUTE_32, 1, afterExpiry, 7_000, 0, RouteMode.FLAT));
        // Last valid day still works.
        card.tapIn(ROUTE_32, 1, CARD_EXPIRY.atTime(22, 0), 7_000, 0, RouteMode.FLAT);
    }

    @Test
    void adminLockAndRevoke() {
        topUp(20_000);
        card.setStatus(issuer, CardState.LOCKED);
        assertEquals(LockReason.ADMIN, card.getInfo().lockReason());
        expectSw(CardConstants.SW_CARD_BLOCKED, () -> tapFlat(ROUTE_32, 7_000));
        expectSw(CardConstants.SW_CARD_BLOCKED, () -> card.verifyPin(PIN));

        card.setStatus(issuer, CardState.ACTIVE);
        tapFlat(ROUTE_32, 7_000);

        card.setStatus(issuer, CardState.REVOKED);
        expectSw(CardConstants.SW_CARD_BLOCKED, () -> tapFlat(ROUTE_32, 7_000));
        expectSw(CardConstants.SW_CONDITIONS, () -> card.setStatus(issuer, CardState.ACTIVE));
    }

    // ------------------------------------------------------------------ monthly pass

    @Test
    void monthlyPassMakesRidesFreeOnItsRouteOnly() {
        topUp(250_000);
        card.verifyPin(PIN);
        LocalDate passEnd = NOW.toLocalDate().plusDays(30);
        TxRecord buy = card.buyPass(PassType.SINGLE_ROUTE, ROUTE_32, passEnd, 200_000, NOW);
        assertEquals(TxType.BUY_PASS, buy.type());
        assertEquals(50_000, buy.balanceAfter());

        CardInfo info = card.getInfo();
        assertEquals(PassType.SINGLE_ROUTE, info.passType());
        assertEquals(passEnd, info.passExpiry());

        TxRecord free = tapFlat(ROUTE_32, 7_000);
        assertEquals(0, free.amount());
        assertTrue(free.passUsed());

        // Other route: charged normally from the wallet.
        assertEquals(7_000, tapFlat(ROUTE_86, 7_000).amount());

        // After the pass expires the route is charged again.
        TxRecord later = card.tapIn(ROUTE_32, 1, passEnd.plusDays(1).atTime(7, 0), 7_000, 0, RouteMode.FLAT);
        assertEquals(7_000, later.amount());
    }

    @Test
    void passOnOtherRouteWithEmptyWalletReportsWrongRoute() {
        topUp(200_000);
        card.verifyPin(PIN);
        card.buyPass(PassType.SINGLE_ROUTE, ROUTE_32, NOW.toLocalDate().plusDays(30), 200_000, NOW);
        expectSw(CardConstants.SW_PASS_WRONG_ROUTE, () -> tapFlat(ROUTE_86, 7_000));
    }

    @Test
    void interlinePassCoversDistanceRoutes() {
        topUp(300_000);
        card.verifyPin(PIN);
        card.buyPass(PassType.INTERLINE, 0, NOW.toLocalDate().plusDays(30), 300_000, NOW);
        card.tapIn(ROUTE_86, 1, NOW, 5_000, 0, RouteMode.DISTANCE);
        TxRecord out = card.tapOut(ROUTE_86, 12, NOW.plusMinutes(40), 12_000);
        assertEquals(0, out.amount());
        assertTrue(out.passUsed());
    }

    @Test
    void buyPassNeedsPinAndFunds() {
        topUp(100_000);
        card.select();
        expectSw(CardConstants.SW_SECURITY_STATUS,
                () -> card.buyPass(PassType.INTERLINE, 0, NOW.toLocalDate().plusDays(30), 300_000, NOW));
        card.verifyPin(PIN);
        expectSw(CardConstants.SW_INSUFFICIENT_FUNDS,
                () -> card.buyPass(PassType.INTERLINE, 0, NOW.toLocalDate().plusDays(30), 300_000, NOW));
    }

    // ------------------------------------------------------------------ photo

    @Test
    void photoRoundTripsEncrypted() {
        byte[] jpeg = new byte[5_000];
        new Random(42).nextBytes(jpeg);
        card.verifyPin(PIN);
        card.writePhoto(jpeg);
        int len = card.getInfo().photoLength();
        assertEquals(5_008, len); // 2-byte length prefix, padded to 16

        card.select();
        expectSw(CardConstants.SW_SECURITY_STATUS, () -> card.readPhoto(len));
        card.verifyPin(PIN);
        assertArrayEquals(jpeg, card.readPhoto(len));
    }

    // ------------------------------------------------------------------ helpers

    private void topUp(long amount) {
        card.verifyPin(PIN);
        card.topUp(issuer, amount, NOW);
        card.select(); // end the PIN session like a real kiosk would
    }

    private TxRecord tapFlat(int route, long fare) {
        return card.tapIn(route, 1, NOW, fare, 0, RouteMode.FLAT);
    }

    private static PassengerProfile sampleProfile() {
        return new PassengerProfile("Trần Thị Bình", LocalDate.of(1950, 1, 2), "0987654321", Category.ELDERLY);
    }

    private static void expectSw(int sw, Executable action) {
        CardStatusException e = assertThrows(CardStatusException.class, action);
        assertEquals(sw, e.sw(), () -> String.format("expected SW %04X but got %04X", sw, e.sw()));
    }
}
