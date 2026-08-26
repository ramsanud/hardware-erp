-- =====================================================================
-- CR-036 : per-product photo upload. Same "own table, never joined into
-- the hot list-read path" pattern already used for user_avatar/
-- tenant_logo/tenant_signature/tenant_upi_qr - a product list page reads
-- many rows per request and must never eagerly carry image bytes along
-- with it.
-- =====================================================================

CREATE TABLE product_image (
    product_id    BIGINT        NOT NULL,
    content_type  VARCHAR(50)   NOT NULL,
    file_size     INTEGER       NOT NULL,
    image_data    BYTEA         NOT NULL,
    updated_at    TIMESTAMP(3)  NOT NULL,

    CONSTRAINT pk_product_image PRIMARY KEY (product_id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id)
        REFERENCES product (product_id) ON DELETE CASCADE
);
