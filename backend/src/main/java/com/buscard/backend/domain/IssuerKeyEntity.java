package com.buscard.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Persisted issuer RSA-1024 key pair used to authorize sensitive card commands. */
@Entity
@Table(name = "issuer_keys")
public class IssuerKeyEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "public_key_x509", nullable = false, columnDefinition = "TEXT")
    private String publicKeyX509;

    @Column(name = "private_key_pkcs8", nullable = false, columnDefinition = "TEXT")
    private String privateKeyPkcs8;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IssuerKeyEntity() {
    }

    public IssuerKeyEntity(String id, String publicKeyX509, String privateKeyPkcs8, Instant createdAt) {
        this.id = id;
        this.publicKeyX509 = publicKeyX509;
        this.privateKeyPkcs8 = privateKeyPkcs8;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getPublicKeyX509() {
        return publicKeyX509;
    }

    public String getPrivateKeyPkcs8() {
        return privateKeyPkcs8;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
