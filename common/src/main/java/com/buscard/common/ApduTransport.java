package com.buscard.common;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/**
 * One APDU exchange with a card. Implemented by the jcardsim adapter in the desktop app; a PC/SC reader
 * ({@code javax.smartcardio.CardChannel}) can be plugged in the same way.
 */
@FunctionalInterface
public interface ApduTransport {

    ResponseAPDU transmit(CommandAPDU command);
}
