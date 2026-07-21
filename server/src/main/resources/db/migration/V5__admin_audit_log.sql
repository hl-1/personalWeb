CREATE TABLE admin_audit_log (
    id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    resource_id UUID NOT NULL,
    resource_version BIGINT,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_admin_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES identity_user_account(id),
    CONSTRAINT ck_admin_audit_action CHECK (
        action IN ('CREATE', 'UPDATE', 'DELETE', 'PUBLISH', 'ARCHIVE')),
    CONSTRAINT ck_admin_audit_resource_type CHECK (
        resource_type IN ('ARTICLE', 'CATEGORY', 'TAG', 'PROJECT', 'PROFILE', 'SKILL', 'EXPERIENCE')),
    CONSTRAINT ck_admin_audit_resource_version CHECK (
        resource_version IS NULL OR resource_version >= 0)
);

CREATE INDEX ix_admin_audit_occurred_at
    ON admin_audit_log (occurred_at DESC, id DESC);

CREATE INDEX ix_admin_audit_resource
    ON admin_audit_log (resource_type, resource_id, occurred_at DESC);

CREATE FUNCTION prevent_admin_audit_log_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'admin audit log is append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_admin_audit_log_append_only
    BEFORE UPDATE OR DELETE ON admin_audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_admin_audit_log_mutation();
