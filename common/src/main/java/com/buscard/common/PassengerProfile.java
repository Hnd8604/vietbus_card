package com.buscard.common;

import com.buscard.common.CardConstants.Category;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * Passenger profile stored AES-encrypted on the card, as TLV: tag(1) len(1) value.
 * Tags: 01 full name (UTF-8), 02 date of birth (4-byte date), 03 phone (ASCII), 04 category (1 byte).
 * The card treats the blob as opaque; max 192 bytes.
 */
public record PassengerProfile(String fullName, LocalDate dateOfBirth, String phone, Category category) {

    private static final int T_NAME = 0x01;
    private static final int T_DOB = 0x02;
    private static final int T_PHONE = 0x03;
    private static final int T_CATEGORY = 0x04;

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        tlv(out, T_NAME, fullName.getBytes(StandardCharsets.UTF_8));
        tlv(out, T_DOB, CardCodec.date(dateOfBirth));
        tlv(out, T_PHONE, phone.getBytes(StandardCharsets.US_ASCII));
        tlv(out, T_CATEGORY, new byte[] {(byte) category.code});
        byte[] b = out.toByteArray();
        if (b.length > CardConstants.PROFILE_MAX) {
            throw new IllegalArgumentException("Profile too long for card: " + b.length + " bytes");
        }
        return b;
    }

    public static PassengerProfile decode(byte[] b) {
        String name = "";
        LocalDate dob = null;
        String phone = "";
        Category category = Category.NORMAL;
        int o = 0;
        while (o + 2 <= b.length) {
            int tag = b[o] & 0xFF;
            int len = b[o + 1] & 0xFF;
            byte[] v = Arrays.copyOfRange(b, o + 2, o + 2 + len);
            switch (tag) {
                case T_NAME -> name = new String(v, StandardCharsets.UTF_8);
                case T_DOB -> dob = CardCodec.readDate(v, 0);
                case T_PHONE -> phone = new String(v, StandardCharsets.US_ASCII);
                case T_CATEGORY -> category = Category.of(v[0]);
                default -> { /* forward compatible: ignore unknown tags */ }
            }
            o += 2 + len;
        }
        return new PassengerProfile(name, dob, phone, category);
    }

    private static void tlv(ByteArrayOutputStream out, int tag, byte[] value) {
        if (value.length > 255) {
            throw new IllegalArgumentException("TLV value too long");
        }
        out.write(tag);
        out.write(value.length);
        out.writeBytes(value);
    }
}
