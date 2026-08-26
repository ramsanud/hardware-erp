-- V1 declared token_hash as CHAR(64) in both refresh_token and
-- password_reset_token, but RefreshToken.tokenHash and
-- PasswordResetToken.tokenHash are mapped with @Column(length = 64) and no
-- columnDefinition override, which Hibernate resolves to VARCHAR(64). Fixed
-- forward rather than editing V1 (BUG-AUTH-011, see BUG_REGISTRY.md).
--
-- CHAR pads short values with trailing spaces; a SHA-256 hex digest is always
-- exactly 64 characters, so no stored value actually changes shape here - only
-- the declared type moves to match what the entity has always expected.
ALTER TABLE refresh_token ALTER COLUMN token_hash TYPE VARCHAR(64);
ALTER TABLE password_reset_token ALTER COLUMN token_hash TYPE VARCHAR(64);
