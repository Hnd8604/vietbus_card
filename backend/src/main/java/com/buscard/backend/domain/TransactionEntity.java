package com.buscard.backend.domain;

import com.buscard.common.CardConstants.TxType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDateTime;

/** Verified card-signed transaction persisted for audit and reconciliation. */
@Entity
@Table(name = "transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tx_card_counter", columnNames = {"card_id", "tx_counter"})
})
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false, length = 16)
    private String cardId;

    @Column(name = "tx_counter", nullable = false)
    private long txCounter;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 20)
    private TxType txType;

    @Column(nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "route_id", nullable = false)
    private int routeId;

    @Column(name = "station_id", nullable = false)
    private int stationId;

    @Column(name = "tx_time", nullable = false)
    private LocalDateTime txTime;

    @Column(nullable = false)
    private int flags;

    @Column(name = "raw_hex", nullable = false, length = 80)
    private String rawHex;

    @Column(name = "signature_base64", nullable = false, columnDefinition = "TEXT")
    private String signatureBase64;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected TransactionEntity() {
    }

    public TransactionEntity(String cardId, long txCounter, TxType txType, long amount, long balanceAfter,
                             int routeId, int stationId, LocalDateTime txTime, int flags,
                             String rawHex, String signatureBase64, boolean verified, Instant recordedAt) {
        this.cardId = cardId;
        this.txCounter = txCounter;
        this.txType = txType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.routeId = routeId;
        this.stationId = stationId;
        this.txTime = txTime;
        this.flags = flags;
        this.rawHex = rawHex;
        this.signatureBase64 = signatureBase64;
        this.verified = verified;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public String getCardId() {
        return cardId;
    }

    public long getTxCounter() {
        return txCounter;
    }

    public TxType getTxType() {
        return txType;
    }

    public long getAmount() {
        return amount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public int getRouteId() {
        return routeId;
    }

    public int getStationId() {
        return stationId;
    }

    public LocalDateTime getTxTime() {
        return txTime;
    }

    public int getFlags() {
        return flags;
    }

    public String getRawHex() {
        return rawHex;
    }

    public String getSignatureBase64() {
        return signatureBase64;
    }

    public boolean isVerified() {
        return verified;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
