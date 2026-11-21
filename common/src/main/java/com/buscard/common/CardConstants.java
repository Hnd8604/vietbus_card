package com.buscard.common;

/** Host-side mirror of the applet constants. Must stay in sync with {@code BusCardApplet}. */
public final class CardConstants {

    private CardConstants() {
    }

    /** Applet AID: "BUSCARD" + version 01. */
    public static final String AID_HEX = "F04255534341524401";

    public static final int CLA = 0x80;

    public static final int INS_GET_INFO = 0x10;
    public static final int INS_GET_PUBLIC_KEY = 0x11;
    public static final int INS_INTERNAL_AUTH = 0x12;
    public static final int INS_GET_CHALLENGE = 0x13;
    public static final int INS_VERIFY_PIN = 0x20;
    public static final int INS_CHANGE_PIN = 0x21;
    public static final int INS_UNBLOCK_PIN = 0x22;
    public static final int INS_READ_PROFILE = 0x30;
    public static final int INS_UPDATE_PROFILE = 0x31;
    public static final int INS_READ_PHOTO = 0x32;
    public static final int INS_WRITE_PHOTO = 0x33;
    public static final int INS_TOP_UP = 0x40;
    public static final int INS_TAP_IN = 0x41;
    public static final int INS_TAP_OUT = 0x42;
    public static final int INS_BUY_PASS = 0x43;
    public static final int INS_GET_LAST_TX = 0x44;
    public static final int INS_SET_STATUS = 0x50;
    public static final int INS_UPDATE_PARAMS = 0x51;
    public static final int INS_PERSONALIZE = 0x60;
    public static final int INS_ACTIVATE = 0x61;

    public static final int SW_OK = 0x9000;
    public static final int SW_INSUFFICIENT_FUNDS = 0x6A90;
    public static final int SW_CARD_EXPIRED = 0x6A91;
    public static final int SW_CARD_BLOCKED = 0x6A92;
    public static final int SW_NOT_ON_BOARD = 0x6A93;
    public static final int SW_PASS_WRONG_ROUTE = 0x6A94;
    public static final int SW_BALANCE_LIMIT = 0x6A95;
    public static final int SW_ISSUER_AUTH_FAILED = 0x6A96;
    public static final int SW_PIN_BLOCKED = 0x6983;
    public static final int SW_SECURITY_STATUS = 0x6982;
    public static final int SW_CONDITIONS = 0x6985;

    public static final int RSA_LEN = 128;
    public static final int CARD_ID_LEN = 16;
    public static final int CHALLENGE_LEN = 16;
    public static final int PIN_TRY_LIMIT = 5;
    public static final int PROFILE_MAX = 192;
    public static final int PHOTO_CHUNK = 224;
    public static final int PHOTO_MAX = 37 * PHOTO_CHUNK;
    public static final long MAX_BALANCE = 10_000_000L;

    public enum CardState {
        INSTALLED(1), PERSONALIZED(2), ACTIVE(3), LOCKED(4), REVOKED(5);

        public final int code;

        CardState(int code) {
            this.code = code;
        }

        public static CardState of(int code) {
            for (CardState s : values()) {
                if (s.code == code) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Unknown card state " + code);
        }
    }

    public enum LockReason {
        NONE(0), PIN(1), ADMIN(2);

        public final int code;

        LockReason(int code) {
            this.code = code;
        }

        public static LockReason of(int code) {
            for (LockReason r : values()) {
                if (r.code == code) {
                    return r;
                }
            }
            throw new IllegalArgumentException("Unknown lock reason " + code);
        }
    }

    /** Passenger category; the discount rate itself is configured in the backend. */
    public enum Category {
        NORMAL(0, "Thông thường"), STUDENT(1, "Học sinh - Sinh viên"),
        ELDERLY(2, "Người cao tuổi"), DISABLED(3, "Người khuyết tật");

        public final int code;
        public final String label;

        Category(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public static Category of(int code) {
            for (Category c : values()) {
                if (c.code == code) {
                    return c;
                }
            }
            throw new IllegalArgumentException("Unknown category " + code);
        }
    }

    public enum PassType {
        NONE(0), SINGLE_ROUTE(1), INTERLINE(2);

        public final int code;

        PassType(int code) {
            this.code = code;
        }

        public static PassType of(int code) {
            for (PassType p : values()) {
                if (p.code == code) {
                    return p;
                }
            }
            throw new IllegalArgumentException("Unknown pass type " + code);
        }
    }

    public enum RouteMode {
        /** Fare charged at tap-in. */
        FLAT(0),
        /** Fare charged at tap-out by number of stations travelled. */
        DISTANCE(1);

        public final int code;

        RouteMode(int code) {
            this.code = code;
        }
    }

    public enum TxType {
        TOP_UP(1), TAP_IN(2), TAP_OUT(3), BUY_PASS(4);

        public final int code;

        TxType(int code) {
            this.code = code;
        }

        public static TxType of(int code) {
            for (TxType t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown tx type " + code);
        }
    }

    public static final int FLAG_PASS_USED = 0x01;
    public static final int FLAG_PENALTY = 0x02;
}
