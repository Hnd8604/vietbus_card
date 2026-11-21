package com.buscard.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.buscard.common.CardConstants.Category;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CodecTest {

    @Test
    void datesRoundTripAndOrderBytewise() {
        LocalDate d = LocalDate.of(2026, 11, 21);
        assertEquals(d, CardCodec.readDate(CardCodec.date(d), 0));
        LocalDateTime t = LocalDateTime.of(2026, 1, 2, 23, 59);
        assertEquals(t, CardCodec.readDateTime(CardCodec.dateTime(t), 0));
        // The card compares dates as unsigned big-endian bytes.
        assertTrue(java.util.Arrays.compareUnsigned(CardCodec.date(LocalDate.of(2026, 12, 1)),
                CardCodec.date(LocalDate.of(2027, 1, 1))) < 0);
    }

    @Test
    void amountsAreUnsigned32Bit() {
        assertEquals(4_000_000_000L, CardCodec.readAmount(CardCodec.amount(4_000_000_000L), 0));
        assertThrows(IllegalArgumentException.class, () -> CardCodec.amount(-1));
    }

    @Test
    void cardIdsArePaddedTo16() {
        byte[] b = CardIds.toBytes("BC2026000001");
        assertEquals(16, b.length);
        assertEquals("BC2026000001", CardIds.toText(b));
    }

    @Test
    void profileTlvRoundTripsUnicode() {
        PassengerProfile p = new PassengerProfile("Phạm Thị Hồng Nhung", LocalDate.of(1999, 9, 9), "0123456789",
                Category.DISABLED);
        assertEquals(p, PassengerProfile.decode(p.encode()));
    }

    @Test
    void issuerKeyEncodesToCardFormat() {
        KeyPair kp = RsaKeys.generate1024();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        byte[] mod = RsaKeys.modulus(pub);
        byte[] exp = RsaKeys.exponent(pub);
        assertEquals(128, mod.length);
        byte[] cardFormat = CardCodec.concat(CardCodec.u16(mod.length), mod, CardCodec.u16(exp.length), exp);
        assertEquals(pub, RsaKeys.fromCardFormat(cardFormat));
        assertArrayEquals(pub.getEncoded(), RsaKeys.fromBase64(RsaKeys.toBase64(pub)).getEncoded());
    }

    @Test
    void statusWordsHaveVietnameseMessages() {
        assertEquals("Sai mã PIN, còn 3 lần thử", CardStatusException.describe(0x63C3));
        assertEquals("Số dư không đủ", CardStatusException.describe(CardConstants.SW_INSUFFICIENT_FUNDS));
    }
}
