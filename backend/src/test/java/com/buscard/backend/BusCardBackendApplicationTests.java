package com.buscard.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buscard.backend.domain.CardEntity;
import com.buscard.backend.domain.TransactionEntity;
import com.buscard.backend.service.CardService;
import com.buscard.backend.service.FareService;
import com.buscard.backend.service.IssuerService;
import com.buscard.backend.service.TransactionService;
import com.buscard.common.CardCodec;
import com.buscard.common.CardConstants;
import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardConstants.RouteMode;
import com.buscard.common.CardConstants.TxType;
import com.buscard.common.CardIds;
import com.buscard.common.RsaKeys;
import com.buscard.common.TxRecord;
import java.security.KeyPair;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BusCardBackendApplicationTests {

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private CardService cardService;

    @Autowired
    private FareService fareService;

    @Autowired
    private TransactionService transactionService;

    @Test
    void issuerKeysInitializeAndSignValidChallenge() {
        assertNotNull(issuerService.getPublicKey());
        assertNotNull(issuerService.getPublicKeyBase64());

        String cardId = "BC2026TEST01";
        cardService.registerCard(cardId, issuerService.getPublicKeyBase64(), Category.NORMAL, LocalDate.of(2030, 1, 1));

        byte[] challenge = new byte[16];
        byte[] payload = new byte[] {0x01, 0x02};
        byte[] sig = issuerService.signCommand(cardId, CardConstants.INS_TOP_UP, challenge, payload);
        assertNotNull(sig);
        assertEquals(128, sig.length);

        boolean ok = RsaKeys.verify(issuerService.getPublicKey(), sig, challenge, CardIds.toBytes(cardId),
                new byte[] {(byte) CardConstants.INS_TOP_UP}, payload);
        assertTrue(ok, "Issuer signature must verify with issuer public key");
    }

    @Test
    void fareCalculationAppliesDiscountsCorrectly() {
        // Route 1 is FLAT (7,000 VND)
        var normalQuote = fareService.quoteTrip(1, Category.NORMAL, null, null);
        assertEquals(7_000, normalQuote.fare());

        var studentQuote = fareService.quoteTrip(1, Category.STUDENT, null, null);
        assertEquals(3_500, studentQuote.fare());

        var elderlyQuote = fareService.quoteTrip(1, Category.ELDERLY, null, null);
        assertEquals(0, elderlyQuote.fare());

        // Route 86 is DISTANCE (Ga HN -> Long Bien: 2 stops => 5000 + 2*2500 = 10000)
        var distQuote = fareService.quoteTrip(86, Category.NORMAL, 8601, 8603);
        assertEquals(RouteMode.DISTANCE, distQuote.mode());
        assertEquals(10_000, distQuote.fare());

        // Monthly pass single route (Route 1: 140,000)
        assertEquals(140_000, fareService.quoteMonthlyPass(PassType.SINGLE_ROUTE, 1, Category.NORMAL));
        assertEquals(70_000, fareService.quoteMonthlyPass(PassType.SINGLE_ROUTE, 1, Category.STUDENT));
    }

    @Test
    void cardRegistrationAndTransactionSignatureVerification() {
        String cardId = "BC2026TEST02";
        KeyPair cardKeys = RsaKeys.generate1024();
        String pubBase64 = RsaKeys.toBase64(cardKeys.getPublic());

        CardEntity card = cardService.registerCard(cardId, pubBase64, Category.NORMAL, LocalDate.of(2030, 1, 1));
        assertEquals(CardState.PERSONALIZED, card.getState());
        assertEquals(0, card.getBalance());

        // Construct raw 40-byte tx record and sign with card's private key
        byte[] raw = CardCodec.concat(
                CardIds.toBytes(cardId),
                CardCodec.amount(1), // counter
                new byte[] {(byte) TxType.TOP_UP.code},
                CardCodec.amount(50_000), // amount
                CardCodec.amount(50_000), // balanceAfter
                CardCodec.u16(0), // route
                CardCodec.u16(0), // station
                CardCodec.dateTime(LocalDateTime.of(2026, 11, 26, 10, 0)),
                new byte[] {0} // flags
        );
        assertEquals(TxRecord.LENGTH, raw.length);
        byte[] sig = RsaKeys.sign(cardKeys.getPrivate(), raw);

        String rawB64 = Base64.getEncoder().encodeToString(raw);
        String sigB64 = Base64.getEncoder().encodeToString(sig);

        TransactionEntity saved = transactionService.recordTransaction(rawB64, sigB64, null, null, null);
        assertNotNull(saved.getId());
        assertTrue(saved.isVerified());
        assertEquals(50_000, saved.getBalanceAfter());

        CardEntity updatedCard = cardService.getCard(cardId);
        assertEquals(50_000, updatedCard.getBalance());
        assertEquals(1, updatedCard.getLastTxCounter());
    }
}
