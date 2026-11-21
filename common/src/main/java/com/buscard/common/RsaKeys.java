package com.buscard.common;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/** RSA helpers shared by terminals and backend. */
public final class RsaKeys {

    private RsaKeys() {
    }

    public static KeyPair generate1024() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(CardConstants.RSA_LEN * 8);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Parses the card's public key format: modLen(2) modulus expLen(2) exponent. */
    public static PublicKey fromCardFormat(byte[] b) {
        int modLen = CardCodec.readU16(b, 0);
        byte[] mod = Arrays.copyOfRange(b, 2, 2 + modLen);
        int expLen = CardCodec.readU16(b, 2 + modLen);
        byte[] exp = Arrays.copyOfRange(b, 4 + modLen, 4 + modLen + expLen);
        return fromModulusExponent(mod, exp);
    }

    public static PublicKey fromModulusExponent(byte[] mod, byte[] exp) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(new BigInteger(1, mod), new BigInteger(1, exp)));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA public key", e);
        }
    }

    /** Fixed-length (128-byte) unsigned modulus, as the card expects. */
    public static byte[] modulus(RSAPublicKey key) {
        return unsigned(key.getModulus(), CardConstants.RSA_LEN);
    }

    public static byte[] exponent(RSAPublicKey key) {
        byte[] e = key.getPublicExponent().toByteArray();
        return e[0] == 0 ? Arrays.copyOfRange(e, 1, e.length) : e;
    }

    public static String toBase64(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PublicKey fromBase64(String x509) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(x509)));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA public key", e);
        }
    }

    public static byte[] sign(PrivateKey key, byte[]... parts) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(key);
            for (byte[] p : parts) {
                s.update(p);
            }
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verify(PublicKey key, byte[] signature, byte[]... parts) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(key);
            for (byte[] p : parts) {
                s.update(p);
            }
            return s.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] unsigned(BigInteger v, int len) {
        byte[] b = v.toByteArray();
        if (b.length == len) {
            return b;
        }
        if (b.length == len + 1 && b[0] == 0) {
            return Arrays.copyOfRange(b, 1, b.length);
        }
        byte[] out = new byte[len];
        System.arraycopy(b, 0, out, len - b.length, b.length);
        return out;
    }
}
