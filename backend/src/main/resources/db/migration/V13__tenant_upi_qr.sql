-- =====================================================================
-- CR-026 : lets a shop upload its existing UPI/GPay QR code image
-- directly, as an alternative to the QR InvoicePdfService already
-- generates from tenant.upi_id text. Same 1:1 bytea pattern as
-- tenant_logo/tenant_signature (V11) - see that migration's header for
-- why this is its own table rather than a column on tenant.
-- =====================================================================

CREATE TABLE tenant_upi_qr (
    tenant_id     BIGINT       NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    file_size     INTEGER      NOT NULL,
    image_data    BYTEA        NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_tenant_upi_qr PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_upi_qr_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant (tenant_id) ON DELETE CASCADE,
    CONSTRAINT ck_tenant_upi_qr_size CHECK (file_size > 0 AND file_size <= 2097152)
);
