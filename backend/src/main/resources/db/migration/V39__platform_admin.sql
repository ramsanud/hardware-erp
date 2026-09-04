-- Platform Admin Console, Phase 1: identity & auth foundation (CR-054).
--
-- Deliberately outside the tenant_id discriminator model (CR-016): a platform
-- admin is Hardware ERP staff, not a shop employee, and must be structurally
-- incapable of being scoped into any one tenant. None of these four tables
-- carry tenant_id, and no tenant-facing repository or filter ever reads them.
--
-- Kept separate from app_user/security_audit_log on purpose - see
-- PROJECT_SKILLS.md "why not reuse the tenant user table": a shared table
-- would mean every future tenant-facing query needs an extra "and not a
-- platform admin" guard to stay safe, forever. A disjoint table makes that
-- class of mistake impossible to write instead of merely easy to avoid.

CREATE TABLE platform_admin (
    platform_admin_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name             VARCHAR(200) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(30)  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    -- MFA is mandatory, never optional, for every platform admin account
    -- (see PLATFORM_ADMIN_SECURITY.md). mfa_enabled flips true only once
    -- enrollment is confirmed with a real code; totp_secret may already be
    -- populated before that (a regenerated, not-yet-confirmed secret).
    mfa_enabled           BOOLEAN NOT NULL DEFAULT FALSE,
    totp_secret           VARCHAR(255),
    mfa_enrolled_at        TIMESTAMP(3),

    token_version         INT NOT NULL DEFAULT 0,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP(3),
    last_login_at         TIMESTAMP(3),

    created_at            TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    created_by            BIGINT REFERENCES platform_admin (platform_admin_id),
    updated_at            TIMESTAMP(3),
    updated_by            BIGINT REFERENCES platform_admin (platform_admin_id),
    version               INT NOT NULL DEFAULT 0,

    CONSTRAINT uq_platform_admin_email UNIQUE (email),
    CONSTRAINT ck_platform_admin_role CHECK (role IN (
        'SUPER_ADMIN', 'PLATFORM_ADMIN', 'SUPPORT_ADMIN', 'SECURITY_ADMIN',
        'FINANCE_ADMIN', 'DEVELOPER', 'READ_ONLY_AUDITOR')),
    CONSTRAINT ck_platform_admin_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

COMMENT ON TABLE platform_admin IS
    'Hardware ERP staff accounts for the Platform Admin Console. Never a tenant, never tenant-scoped.';

-- One-time-use MFA recovery codes, issued as a batch of 10 the moment
-- enrollment is confirmed. Same shape as a password: only the SHA-256 hash
-- is ever stored, so a database dump does not yield a usable code.
CREATE TABLE platform_admin_backup_code (
    backup_code_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform_admin_id  BIGINT NOT NULL REFERENCES platform_admin (platform_admin_id) ON DELETE CASCADE,
    code_hash           VARCHAR(64) NOT NULL,
    used_at             TIMESTAMP(3),
    created_at          TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uq_platform_admin_backup_code UNIQUE (platform_admin_id, code_hash)
);

CREATE INDEX idx_platform_admin_backup_code_admin ON platform_admin_backup_code (platform_admin_id);

-- Mirrors refresh_token (V1__auth_schema.sql) exactly, on a disjoint table so
-- a platform-admin session can never be confused with a tenant session even
-- at the storage layer.
CREATE TABLE platform_admin_refresh_token (
    refresh_token_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform_admin_id     BIGINT NOT NULL REFERENCES platform_admin (platform_admin_id),
    token_hash             VARCHAR(64) NOT NULL,
    expires_at              TIMESTAMP(3) NOT NULL,
    revoked_at              TIMESTAMP(3),
    revoked_reason          VARCHAR(40),
    replaced_by_token_id    BIGINT,
    ip_address              VARCHAR(45),
    user_agent              VARCHAR(255),
    last_used_at            TIMESTAMP(3),
    created_at              TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uq_platform_admin_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_platform_admin_refresh_token_reason CHECK (revoked_reason IS NULL OR revoked_reason IN
        ('ROTATED', 'LOGOUT', 'LOGOUT_ALL', 'REUSE_DETECTED', 'PASSWORD_CHANGED',
         'PASSWORD_RESET', 'USER_DEACTIVATED', 'ROLE_CHANGED', 'SESSION_REVOKED'))
);

CREATE INDEX idx_platform_admin_refresh_token_admin ON platform_admin_refresh_token (platform_admin_id);

-- The platform-wide equivalent of security_audit_log - deliberately its own
-- table (not a shared one with a "scope" column) so a bug in one audit path
-- can never suppress or corrupt evidence in the other. platform_admin_id is
-- nullable (a login attempt against an email that matches no account is
-- still an event worth recording, with no admin row to reference) and,
-- exactly like security_audit_log.user_id, deliberately NOT a foreign key:
-- an audit write happens in its own REQUIRES_NEW transaction (so it survives
-- the surrounding action rolling back), which for a "create" event runs
-- concurrently with the still-uncommitted insert of the row it is
-- referencing - an enforced FK there would reject the audit row outright.
CREATE TABLE platform_audit_log (
    platform_audit_log_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform_admin_id      BIGINT,
    action                  VARCHAR(60) NOT NULL,
    target_type             VARCHAR(60),
    target_id               BIGINT,
    detail                  VARCHAR(500),
    success                 BOOLEAN NOT NULL,
    ip_address              VARCHAR(45),
    user_agent              VARCHAR(255),
    created_at              TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_platform_audit_log_admin ON platform_audit_log (platform_admin_id);
CREATE INDEX idx_platform_audit_log_created_at ON platform_audit_log (created_at);
