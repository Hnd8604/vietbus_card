package com.buscard.backend.service;

import com.buscard.backend.domain.IssuerKeyEntity;
import com.buscard.backend.repo.CardRepository;
import com.buscard.backend.repo.IssuerKeyRepository;
import com.buscard.common.CardConstants;
import com.buscard.common.CardIds;
import com.buscard.common.IssuerAuthority;
import com.buscard.common.RsaKeys;
import jakarta.annotation.PostConstruct;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the issuer RSA-1024 key pair and signs issuer-authorized APDU challenges. */
@Service
public class IssuerService {

    private static final String DEFAULT_KEY_ID = "DEFAULT";
    private static final Set<Integer> ALLOWED_ISSUER_INS = Set.of(
            CardConstants.INS_UNBLOCK_PIN,
            CardConstants.INS_TOP_UP,
            CardConstants.INS_SET_STATUS,
            CardConstants.INS_UPDATE_PARAMS,
            CardConstants.INS_ACTIVATE
    );

    private final IssuerKeyRepository keyRepository;
    private final CardRepository cardRepository;

    private RSAPublicKey publicKey;
    private PrivateKey privateKey;

    public IssuerService(IssuerKeyRepository keyRepository, CardRepository cardRepository) {
        this.keyRepository = keyRepository;
        this.cardRepository = cardRepository;
    }

    @PostConstruct
    @Transactional
    public void initKeys() {
        IssuerKeyEntity entity = keyRepository.findById(DEFAULT_KEY_ID).orElseGet(() -> {
            KeyPair kp = RsaKeys.generate1024();
            String pub = RsaKeys.toBase64(kp.getPublic());
            String priv = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
            return keyRepository.save(new IssuerKeyEntity(DEFAULT_KEY_ID, pub, priv, Instant.now()));
        });
        this.publicKey = (RSAPublicKey) RsaKeys.fromBase64(entity.getPublicKeyX509());
        this.privateKey = decodePrivateKey(entity.getPrivateKeyPkcs8());
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public String getPublicKeyBase64() {
        return RsaKeys.toBase64(publicKey);
    }

    /**
     * Signs {@code challenge(16) || cardId(16) || INS(1) || payload} for a registered card.
     */
    public byte[] signCommand(String cardId, int ins, byte[] challenge, byte[] payload) {
        if (!ALLOWED_ISSUER_INS.contains(ins)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported issuer INS: 0x" + Integer.toHexString(ins));
        }
        if (challenge == null || challenge.length != CardConstants.CHALLENGE_LEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Challenge must be 16 bytes");
        }
        if (!cardRepository.existsById(cardId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Card is not registered: " + cardId);
        }
        byte[] cardIdBytes = CardIds.toBytes(cardId);
        byte[] safePayload = payload == null ? new byte[0] : payload;
        return IssuerAuthority.withKey(privateKey).sign(challenge, cardIdBytes, ins, safePayload);
    }

    private static PrivateKey decodePrivateKey(String pkcs8Base64) {
        try {
            byte[] der = Base64.getDecoder().decode(pkcs8Base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Corrupt issuer private key", e);
        }
    }
}
