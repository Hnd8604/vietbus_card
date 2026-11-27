package com.buscard.backend.service;

import com.buscard.backend.domain.CardEntity;
import com.buscard.backend.repo.CardRepository;
import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.LockReason;
import com.buscard.common.CardConstants.PassType;
import com.buscard.common.CardIds;
import com.buscard.common.RsaKeys;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages card registration and state synchronization with the smart card applet. */
@Service
public class CardService {

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    @Transactional
    public CardEntity registerCard(String cardId, String publicKeyBase64, Category category, LocalDate cardExpiry) {
        validateCardId(cardId);
        if (cardRepository.existsById(cardId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Card already registered: " + cardId);
        }
        try {
            RsaKeys.fromBase64(publicKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid RSA public key");
        }
        CardEntity card = new CardEntity(cardId, publicKeyBase64, CardState.PERSONALIZED, LockReason.NONE,
                category, cardExpiry, Instant.now());
        return cardRepository.save(card);
    }

    @Transactional(readOnly = true)
    public List<CardEntity> listCards() {
        return cardRepository.findAllByOrderByIssuedAtDesc();
    }

    @Transactional(readOnly = true)
    public CardEntity getCard(String cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Card not found: " + cardId));
    }

    @Transactional
    public CardEntity syncCard(String cardId, CardState state, LockReason lockReason, Category category,
                               LocalDate cardExpiry, long balance, PassType passType, int passRoute,
                               LocalDate passExpiry, long lastTxCounter) {
        CardEntity card = getCard(cardId);
        if (lastTxCounter < card.getLastTxCounter()) {
            throw new ApiException(HttpStatus.CONFLICT, "Card txCounter cannot roll back");
        }
        card.setState(state);
        card.setLockReason(lockReason);
        card.setCategory(category);
        card.setCardExpiry(cardExpiry);
        card.setBalance(balance);
        card.setPassType(passType);
        card.setPassRoute(passRoute);
        card.setPassExpiry(passExpiry);
        card.setLastTxCounter(lastTxCounter);
        card.setUpdatedAt(Instant.now());
        return cardRepository.save(card);
    }

    private static void validateCardId(String cardId) {
        try {
            CardIds.toBytes(cardId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
