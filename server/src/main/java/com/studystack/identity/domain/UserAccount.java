package com.studystack.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "identity_user_account")
public class UserAccount {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "login", nullable = false, length = 255)
    private String login;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_login_at", nullable = false)
    private OffsetDateTime lastLoginAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserAccount() {
    }

    public UserAccount(
            UUID id,
            String login,
            String displayName,
            String avatarUrl,
            AccountStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime lastLoginAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.login = requireText(login, "login");
        this.displayName = requireText(displayName, "displayName");
        this.avatarUrl = avatarUrl;
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.lastLoginAt = Objects.requireNonNull(lastLoginAt, "lastLoginAt is required");
    }

    public void updateProfile(
            String login,
            String displayName,
            String avatarUrl,
            OffsetDateTime loginTime) {
        this.login = requireText(login, "login");
        this.displayName = requireText(displayName, "displayName");
        this.avatarUrl = avatarUrl;
        this.updatedAt = Objects.requireNonNull(loginTime, "loginTime is required");
        this.lastLoginAt = loginTime;
    }

    public UUID id() {
        return id;
    }

    public String login() {
        return login;
    }

    public String displayName() {
        return displayName;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public AccountStatus status() {
        return status;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public OffsetDateTime lastLoginAt() {
        return lastLoginAt;
    }

    public long version() {
        return version;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
