CREATE TABLE identity_user_account (
    id UUID NOT NULL,
    login VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(2048),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_identity_user_account PRIMARY KEY (id),
    CONSTRAINT ck_identity_user_login_not_blank CHECK (btrim(login) <> ''),
    CONSTRAINT ck_identity_user_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_identity_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_identity_user_version CHECK (version >= 0)
);

CREATE TABLE identity_external_identity (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_identity_external_identity PRIMARY KEY (id),
    CONSTRAINT fk_identity_external_user
        FOREIGN KEY (user_id) REFERENCES identity_user_account(id) ON DELETE CASCADE,
    CONSTRAINT uk_identity_external_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_identity_external_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_identity_external_provider CHECK (provider = 'github'),
    CONSTRAINT ck_identity_external_subject_not_blank CHECK (btrim(provider_subject) <> '')
);

CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk
        FOREIGN KEY (session_primary_id) REFERENCES spring_session(primary_id) ON DELETE CASCADE
);
