package com.buscard.common;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;

/** Encoding of the card's wire formats: 4-byte dates, 6-byte date-times, 4-byte amounts, 2-byte ids. */
public final class CardCodec {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private CardCodec() {
    }

    /** YYYY(2) MM(1) DD(1). All-zero means "no date". */
    public static byte[] date(LocalDate d) {
        return new byte[] {(byte) (d.getYear() >> 8), (byte) d.getYear(), (byte) d.getMonthValue(), (byte) d.getDayOfMonth()};
    }

    public static LocalDate readDate(byte[] b, int off) {
        int year = ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
        if (year == 0) {
            return null;
        }
        return LocalDate.of(year, b[off + 2], b[off + 3]);
    }

    /** date(4) HH(1) mm(1). */
    public static byte[] dateTime(LocalDateTime t) {
        byte[] d = date(t.toLocalDate());
        return new byte[] {d[0], d[1], d[2], d[3], (byte) t.getHour(), (byte) t.getMinute()};
    }

    public static LocalDateTime readDateTime(byte[] b, int off) {
        LocalDate d = readDate(b, off);
        return d == null ? null : d.atTime(b[off + 4], b[off + 5]);
    }

    public static byte[] amount(long vnd) {
        if (vnd < 0 || vnd > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Amount out of range: " + vnd);
        }
        return new byte[] {(byte) (vnd >> 24), (byte) (vnd >> 16), (byte) (vnd >> 8), (byte) vnd};
    }

    public static long readAmount(byte[] b, int off) {
        return ((long) (b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    public static byte[] u16(int v) {
        if (v < 0 || v > 0xFFFF) {
            throw new IllegalArgumentException("Value out of range: " + v);
        }
        return new byte[] {(byte) (v >> 8), (byte) v};
    }

    public static int readU16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    public static String hex(byte[] b) {
        return HEX.formatHex(b);
    }

    public static byte[] unhex(String s) {
        return HEX.parseHex(s);
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}
