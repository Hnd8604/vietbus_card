package com.buscard.common;

/** A non-9000 status word from the card, with a passenger-facing Vietnamese message. */
public class CardStatusException extends RuntimeException {

    private final int sw;

    public CardStatusException(int sw, String command) {
        super(String.format("%s: SW=%04X - %s", command, sw, describe(sw)));
        this.sw = sw;
    }

    public int sw() {
        return sw;
    }

    /** Remaining PIN tries for SW 63Cx, otherwise -1. */
    public int pinTriesRemaining() {
        return (sw & 0xFFF0) == 0x63C0 ? sw & 0x0F : -1;
    }

    public String userMessage() {
        return describe(sw);
    }

    public static String describe(int sw) {
        if ((sw & 0xFFF0) == 0x63C0) {
            return "Sai mã PIN, còn " + (sw & 0x0F) + " lần thử";
        }
        return switch (sw) {
            case CardConstants.SW_INSUFFICIENT_FUNDS -> "Số dư không đủ";
            case CardConstants.SW_CARD_EXPIRED -> "Thẻ đã hết hạn";
            case CardConstants.SW_CARD_BLOCKED -> "Thẻ đã bị khóa hoặc thu hồi";
            case CardConstants.SW_NOT_ON_BOARD -> "Chưa quẹt thẻ lên xe trên tuyến này";
            case CardConstants.SW_PASS_WRONG_ROUTE -> "Vé tháng không áp dụng cho tuyến này";
            case CardConstants.SW_BALANCE_LIMIT -> "Vượt hạn mức số dư của thẻ";
            case CardConstants.SW_ISSUER_AUTH_FAILED -> "Xác thực nhà phát hành thất bại";
            case CardConstants.SW_PIN_BLOCKED -> "Mã PIN đã bị khóa (sai quá 5 lần)";
            case CardConstants.SW_SECURITY_STATUS -> "Cần xác thực mã PIN";
            case CardConstants.SW_CONDITIONS -> "Thao tác không hợp lệ ở trạng thái thẻ hiện tại";
            case 0x6700 -> "Sai độ dài dữ liệu";
            case 0x6A80 -> "Dữ liệu không hợp lệ";
            case 0x6A83 -> "Không có dữ liệu";
            case 0x6A86 -> "Tham số không hợp lệ";
            case 0x6D00 -> "Lệnh không được hỗ trợ";
            case 0x6999 -> "Không chọn được ứng dụng thẻ";
            default -> "Lỗi thẻ";
        };
    }
}
