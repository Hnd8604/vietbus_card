package com.buscard.backend.service;

import com.buscard.backend.domain.CardEntity;
import com.buscard.backend.domain.TransactionEntity;
import com.buscard.backend.repo.CardRepository;
import com.buscard.backend.repo.TransactionRepository;
import com.buscard.common.CardCodec;
import com.buscard.common.CardConstants;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardConstants.TxType;
import com.buscard.common.RsaKeys;
import com.buscard.common.TxRecord;
import java.security.PublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies card RSA signatures on transaction records and stores them in PostgreSQL. */
@Service
public class TransactionService {

    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(CardRepository cardRepository, TransactionRepository transactionRepository) {
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionEntity recordTransaction(String rawBase64, String signatureBase64,
                                               PassType passType, Integer passRoute, LocalDate passExpiry) {
        byte[] raw = decodeBase64(rawBase64, "rawBase64", TxRecord.LENGTH);
        byte[] sig = decodeBase64(signatureBase64, "signatureBase64", CardConstants.RSA_LEN);
        TxRecord record = TxRecord.fromRaw(raw, sig);

        CardEntity card = cardRepository.findById(record.cardId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Card not found: " + record.cardId()));

        PublicKey cardKey = RsaKeys.fromBase64(card.getPublicKeyX509());
        if (!record.verify(cardKey)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Invalid card RSA signature for tx " + record.counter());
        }
        if (record.counter() <= card.getLastTxCounter()
                || transactionRepository.existsByCardIdAndTxCounter(record.cardId(), record.counter())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Duplicate or stale txCounter " + record.counter() + " for card " + record.cardId());
        }

        Instant now = Instant.now();
        TransactionEntity entity = new TransactionEntity(
                record.cardId(),
                record.counter(),
                record.type(),
                record.amount(),
                record.balanceAfter(),
                record.route(),
                record.station(),
                record.time(),
                record.flags(),
                CardCodec.hex(raw),
                signatureBase64,
                true,
                now
        );
        TransactionEntity saved = transactionRepository.save(entity);

        card.setBalance(record.balanceAfter());
        card.setLastTxCounter(record.counter());
        if (record.type() == TxType.BUY_PASS && passType != null && passExpiry != null) {
            card.setPassType(passType);
            card.setPassRoute(passRoute == null ? 0 : passRoute);
            card.setPassExpiry(passExpiry);
        }
        card.setUpdatedAt(now);
        cardRepository.save(card);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<TransactionEntity> listByCard(String cardId) {
        return transactionRepository.findByCardIdOrderByTxCounterDesc(cardId);
    }

    @Transactional(readOnly = true)
    public List<TransactionEntity> listRecent() {
        return transactionRepository.findTop50ByOrderByRecordedAtDesc();
    }

    private static byte[] decodeBase64(String value, String field, int expectedLength) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length != expectedLength) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        field + " must decode to " + expectedLength + " bytes, got " + bytes.length);
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Base64 in " + field);
        }
    }
}
