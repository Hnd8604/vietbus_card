package com.buscard.backend.domain;

import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.LockReason;
import com.buscard.common.CardConstants.PassType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** Server-side registry record for an issued VietBus smart card. */
@Entity
@Table(name = "cards")
public class CardEntity {

    @Id
    @Column(name = "card_id", length = 16)
    private String cardId;

    @Column(name = "public_key_x509", nullable = false, columnDefinition = "TEXT")
    private String publicKeyX509;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_reason", nullable = false, length = 20)
    private LockReason lockReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(name = "card_expiry", nullable = false)
    private LocalDate cardExpiry;

    @Column(nullable = false)
    private long balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_type", nullable = false, length = 20)
    private PassType passType;

    @Column(name = "pass_route", nullable = false)
    private int passRoute;

    @Column(name = "pass_expiry")
    private LocalDate passExpiry;

    @Column(name = "last_tx_counter", nullable = false)
    private long lastTxCounter;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardEntity() {
    }

    public CardEntity(String cardId, String publicKeyX509, CardState state, LockReason lockReason,
                      Category category, LocalDate cardExpiry, Instant now) {
        this.cardId = cardId;
        this.publicKeyX509 = publicKeyX509;
        this.state = state;
        this.lockReason = lockReason;
        this.category = category;
        this.cardExpiry = cardExpiry;
        this.balance = 0;
        this.passType = PassType.NONE;
        this.passRoute = 0;
        this.lastTxCounter = 0;
        this.issuedAt = now;
        this.updatedAt = now;
    }

    public String getCardId() {
        return cardId;
    }

    public String getPublicKeyX509() {
        return publicKeyX509;
    }

    public CardState getState() {
        return state;
    }

    public void setState(CardState state) {
        this.state = state;
    }

    public LockReason getLockReason() {
        return lockReason;
    }

    public void setLockReason(LockReason lockReason) {
        this.lockReason = lockReason;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public LocalDate getCardExpiry() {
        return cardExpiry;
    }

    public void setCardExpiry(LocalDate cardExpiry) {
        this.cardExpiry = cardExpiry;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public PassType getPassType() {
        return passType;
    }

    public void setPassType(PassType passType) {
        this.passType = passType;
    }

    public int getPassRoute() {
        return passRoute;
    }

    public void setPassRoute(int passRoute) {
        this.passRoute = passRoute;
    }

    public LocalDate getPassExpiry() {
        return passExpiry;
    }

    public void setPassExpiry(LocalDate passExpiry) {
        this.passExpiry = passExpiry;
    }

    public long getLastTxCounter() {
        return lastTxCounter;
    }

    public void setLastTxCounter(long lastTxCounter) {
        this.lastTxCounter = lastTxCounter;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
