package com.buscard.applet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buscard.common.BusCardClient;
import com.buscard.common.CardConstants;
import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.RouteMode;
import com.buscard.common.CardInfo;
import com.buscard.common.CardStatusException;
import com.buscard.common.IssuerAuthority;
import com.buscard.common.PassengerProfile;
import com.buscard.common.RsaKeys;
import com.licel.jcardsim.base.PersistentSimulatorRuntime;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javacard.framework.AID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase-1 spike: can a card's EEPROM state (balance, keys, PIN, encrypted profile) survive a "restart" using
 * jcardsim's Kryo-based {@link PersistentSimulatorRuntime}? The desktop app relies on this.
 */
class PersistenceSpikeTest {

    static final AID AID_BUSCARD = AIDUtil.create(CardConstants.AID_HEX);
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 7, 30);

    @Test
    void cardStateSurvivesSimulatorRestart(@TempDir Path dir) {
        KeyPair issuerKeys = RsaKeys.generate1024();
        IssuerAuthority issuer = IssuerAuthority.withKey(issuerKeys.getPrivate());
        PassengerProfile profile = new PassengerProfile("Lê Minh", LocalDate.of(2000, 3, 4), "0900000000",
                Category.NORMAL);

        // --- first "run": issue, top up, tap
        CardSimulator first = new CardSimulator(runtimeFor(dir));
        first.installApplet(AID_BUSCARD, BusCardApplet.class);
        BusCardClient card = new BusCardClient(first::transmitCommand);
        card.select();
        PublicKey cardKey = card.personalize("BC2026000777", LocalDate.of(2030, 1, 1), Category.NORMAL,
                (RSAPublicKey) issuerKeys.getPublic(), "1234");
        card.activate(issuer);
        card.verifyPin("1234");
        card.updateProfile(profile);
        card.topUp(issuer, 40_000, NOW);
        card.tapIn(86, 2, NOW, 5_000, 0, RouteMode.DISTANCE);
        first.reset(); // "remove card": PersistentSimulatorRuntime writes the applet image

        // --- second "run": fresh runtime, load the saved image instead of installing
        // (the Simulator constructor resets its runtime, so load the image after constructing it)
        CardSimulator second = new CardSimulator(runtimeFor(dir));
        second.loadApplet(AID_BUSCARD, BusCardApplet.class);
        BusCardClient again = new BusCardClient(second::transmitCommand);
        again.select();

        CardInfo info = again.getInfo();
        assertEquals(CardState.ACTIVE, info.state());
        assertEquals(40_000, info.balance());
        assertTrue(info.onBoard(), "open trip must survive");
        assertEquals(2, info.txCounter());
        assertTrue(again.authenticate(cardKey, info), "card private key must survive");

        // Session state must NOT survive: PIN is required again.
        assertThrows(CardStatusException.class, again::readProfile);
        again.verifyPin("1234");
        assertEquals(profile, again.readProfile());
        assertEquals(31_000, again.tapOut(86, 6, NOW.plusMinutes(15), 9_000).balanceAfter());
    }

    private static PersistentSimulatorRuntime runtimeFor(Path dir) {
        System.setProperty(PersistentSimulatorRuntime.PERSISTENT_BASE_DIR, dir.toString());
        return new PersistentSimulatorRuntime();
    }
}
