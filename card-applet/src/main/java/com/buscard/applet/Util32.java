package com.buscard.applet;

/**
 * Unsigned 32-bit big-endian arithmetic on byte arrays, using only byte/short (Java Card has no mandatory int).
 * Also used to compare 4-byte dates (YYYY MM DD), which order the same way.
 */
final class Util32 {

    private Util32() {
    }

    /** Unsigned compare of a[aOff..+4] and b[bOff..+4]: -1, 0 or 1. */
    static short compare(byte[] a, short aOff, byte[] b, short bOff) {
        for (short i = 0; i < 4; i++) {
            short x = (short) (a[(short) (aOff + i)] & 0xFF);
            short y = (short) (b[(short) (bOff + i)] & 0xFF);
            if (x != y) {
                return x < y ? (short) -1 : (short) 1;
            }
        }
        return 0;
    }

    /** out = a + b. Returns true on overflow. {@code out} may alias {@code a} or {@code b}. */
    static boolean add(byte[] a, short aOff, byte[] b, short bOff, byte[] out, short oOff) {
        short carry = 0;
        for (short i = 3; i >= 0; i--) {
            short s = (short) ((a[(short) (aOff + i)] & 0xFF) + (b[(short) (bOff + i)] & 0xFF) + carry);
            out[(short) (oOff + i)] = (byte) s;
            carry = (short) ((s >> 8) & 0x01);
        }
        return carry != 0;
    }

    /** out = a - b. Returns true on borrow (a < b). {@code out} may alias {@code a} or {@code b}. */
    static boolean sub(byte[] a, short aOff, byte[] b, short bOff, byte[] out, short oOff) {
        short borrow = 0;
        for (short i = 3; i >= 0; i--) {
            short d = (short) ((a[(short) (aOff + i)] & 0xFF) - (b[(short) (bOff + i)] & 0xFF) - borrow);
            out[(short) (oOff + i)] = (byte) d;
            borrow = d < 0 ? (short) 1 : (short) 0;
        }
        return borrow != 0;
    }

    /** a[aOff..+4] += 1 (wraps). */
    static void increment(byte[] a, short aOff) {
        for (short i = 3; i >= 0; i--) {
            short idx = (short) (aOff + i);
            a[idx]++;
            if (a[idx] != 0) {
                return;
            }
        }
    }

    static boolean isZero(byte[] a, short aOff) {
        return (short) (a[aOff] | a[(short) (aOff + 1)] | a[(short) (aOff + 2)] | a[(short) (aOff + 3)]) == 0;
    }
}
