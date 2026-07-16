package com.studystack.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "identity_external_identity")
public class ExternalIdentity {

    private static final String GITHUB = "github";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, updatable = false, length = 32)
    private String provider;

    @Column(name = "provider_subject", nullable = false, updatable = false, length = 64)
    private String providerSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ExternalIdentity() {
    }

    private ExternalIdentity(
            UUID id,
            UUID userId,
            String provider,
            String providerSubject,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.userId = Objects.requireNonNull(userId, "userId is required");
        this.provider = Objects.requireNonNull(provider, "provider is required");
        this.providerSubject = requireText(providerSubject, "providerSubject");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static ExternalIdentity github(
            UUID id,
            UUID userId,
            String providerSubject,
            OffsetDateTime timestamp) {
        return new ExternalIdentity(id, userId, GITHUB, providerSubject, timestamp, timestamp);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String provider() {
        return provider;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
