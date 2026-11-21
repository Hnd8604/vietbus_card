package com.buscard.common;

import java.security.PrivateKey;

/**
 * Signs issuer-authorized card commands. In production this is the backend ({@code /api/issuer/sign});
 * tests and offline demos can use {@link #withKey(PrivateKey)}.
 *
 * <p>Signed message: challenge(16) || cardId(16) || INS(1) || payload, SHA256withRSA.
 */
@FunctionalInterface
public interface IssuerAuthority {

    byte[] sign(byte[] challenge, byte[] cardId, int ins, byte[] payload);

    static IssuerAuthority withKey(PrivateKey issuerKey) {
        return (challenge, cardId, ins, payload) ->
                RsaKeys.sign(issuerKey, challenge, cardId, new byte[] {(byte) ins}, payload);
    }
}
