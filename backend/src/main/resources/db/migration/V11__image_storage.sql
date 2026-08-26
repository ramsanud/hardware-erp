-- =====================================================================
-- CR-023 : profile photo, shop logo, digital signature - stored as
-- Postgres bytea (see CR-023 for why: single-shop monolith, "one
-- PostgreSQL database" is already a locked decision, no cloud/volume
-- infrastructure exists or is justified for a handful of small images).
--
-- Deliberately NOT columns on app_user/tenant: both those tables are
-- reloaded in full on every authenticated request (JwtAuthenticationFilter,
-- BUG-AUTH-001's per-request reload design) - an eager bytea column there
-- would bloat every request with image bytes. Separate 1:1 tables, queried
-- only by a dedicated image-serving endpoint, keep the hot path untouched.
-- =====================================================================

CREATE TABLE user_avatar (
    user_id       BIGINT       NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    file_size     INTEGER      NOT NULL,
    image_data    BYTEA        NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_user_avatar PRIMARY KEY (user_id),
    CONSTRAINT fk_user_avatar_user FOREIGN KEY (user_id)
        REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_avatar_size CHECK (file_size > 0 AND file_size <= 2097152)
);

CREATE TABLE tenant_logo (
    tenant_id     BIGINT       NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    file_size     INTEGER      NOT NULL,
    image_data    BYTEA        NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_tenant_logo PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_logo_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    CONSTRAINT ck_tenant_logo_size CHECK (file_size > 0 AND file_size <= 2097152)
);

CREATE TABLE tenant_signature (
    tenant_id     BIGINT       NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    file_size     INTEGER      NOT NULL,
    image_data    BYTEA        NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_tenant_signature PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_signature_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    CONSTRAINT ck_tenant_signature_size CHECK (file_size > 0 AND file_size <= 2097152)
);

-- ---------------------------------------------------------------------
-- Shop name is already tenant.name (V6) but Settings never exposed it
-- for editing - only GST/address fields (CR-022). No schema change
-- needed, tenant.name already exists.
-- ---------------------------------------------------------------------
