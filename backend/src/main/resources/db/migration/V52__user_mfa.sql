-- CR-058: mandatory TOTP two-factor authentication for every tenant user.
-- Same shape as platform_admin's own MFA columns (V39__platform_admin.sql) -
-- mfa_enabled flips true only once enrollment is confirmed with a real code;
-- totp_secret may already be populated before that (a regenerated,
-- not-yet-confirmed secret from an abandoned /mfa/enroll call).
ALTER TABLE app_user
    ADD COLUMN mfa_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_secret     VARCHAR(255),
    ADD COLUMN mfa_enrolled_at TIMESTAMP(3);

-- One-time MFA recovery codes, mirroring platform_admin_backup_code exactly.
CREATE TABLE user_backup_code (
    backup_code_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES app_user (user_id) ON DELETE CASCADE,
    code_hash      VARCHAR(64) NOT NULL,
    used_at        TIMESTAMP(3),
    created_at     TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uq_user_backup_code UNIQUE (user_id, code_hash)
);

CREATE INDEX idx_user_backup_code_user ON user_backup_code (user_id);
