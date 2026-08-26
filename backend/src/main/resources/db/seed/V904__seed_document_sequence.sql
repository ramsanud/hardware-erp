-- =====================================================================
-- CR-041 : Re-sync document_sequence after the dev/test seed data.
--
-- V29 backfills document_sequence from whatever is already in the tables.
-- In production that is the real data and the backfill is correct. In the
-- dev and test profiles, though, Flyway runs V29 BEFORE V900-V903, so the
-- backfill sees empty tables and seeds every counter at 1 - and then the
-- seed inserts SUP-0001..SUP-0013, CUS-nnnn, PRD-nnnn and the rest on top
-- of it. The first generated code afterwards would collide with a seeded
-- row and fail on the unique constraint.
--
-- Caught by DocumentSequenceServiceIT.backfillContinuesExistingRun.
--
-- This file lives in db/seed, not db/migration, because it only makes
-- sense where the seed itself was applied - the dev and test profiles.
-- =====================================================================

INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
SELECT tenant_id, 'CUSTOMER',
       COALESCE(MAX(CAST(SUBSTRING(customer_code FROM 5) AS INTEGER)), 0) + 1, NOW()
FROM customer WHERE customer_code ~ '^CUS-[0-9]+$' GROUP BY tenant_id
ON CONFLICT (tenant_id, doc_type) DO UPDATE
    SET next_value = GREATEST(document_sequence.next_value, EXCLUDED.next_value),
        updated_at = NOW();

INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
SELECT tenant_id, 'SUPPLIER',
       COALESCE(MAX(CAST(SUBSTRING(supplier_code FROM 5) AS INTEGER)), 0) + 1, NOW()
FROM supplier WHERE supplier_code ~ '^SUP-[0-9]+$' GROUP BY tenant_id
ON CONFLICT (tenant_id, doc_type) DO UPDATE
    SET next_value = GREATEST(document_sequence.next_value, EXCLUDED.next_value),
        updated_at = NOW();

INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
SELECT tenant_id, 'PRODUCT',
       COALESCE(MAX(CAST(SUBSTRING(product_code FROM 5) AS INTEGER)), 0) + 1, NOW()
FROM product WHERE product_code ~ '^PRD-[0-9]+$' GROUP BY tenant_id
ON CONFLICT (tenant_id, doc_type) DO UPDATE
    SET next_value = GREATEST(document_sequence.next_value, EXCLUDED.next_value),
        updated_at = NOW();

INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
SELECT tenant_id, 'CATEGORY',
       COALESCE(MAX(CAST(SUBSTRING(category_code FROM 5) AS INTEGER)), 0) + 1, NOW()
FROM category WHERE category_code ~ '^CAT-[0-9]+$' GROUP BY tenant_id
ON CONFLICT (tenant_id, doc_type) DO UPDATE
    SET next_value = GREATEST(document_sequence.next_value, EXCLUDED.next_value),
        updated_at = NOW();

INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
SELECT tenant_id, 'BRAND',
       COALESCE(MAX(CAST(SUBSTRING(brand_code FROM 5) AS INTEGER)), 0) + 1, NOW()
FROM brand WHERE brand_code ~ '^BRD-[0-9]+$' GROUP BY tenant_id
ON CONFLICT (tenant_id, doc_type) DO UPDATE
    SET next_value = GREATEST(document_sequence.next_value, EXCLUDED.next_value),
        updated_at = NOW();

INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
SELECT tenant_id, 'INVOICE',
       COALESCE(MAX(CAST(SUBSTRING(invoice_number FROM 5) AS INTEGER)), 0) + 1, NOW()
FROM invoice WHERE invoice_number ~ '^INV-[0-9]+$' GROUP BY tenant_id
ON CONFLICT (tenant_id, doc_type) DO UPDATE
    SET next_value = GREATEST(document_sequence.next_value, EXCLUDED.next_value),
        updated_at = NOW();

INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
SELECT tenant_id, 'QUOTATION',
       COALESCE(MAX(CAST(SUBSTRING(quotation_number FROM 5) AS INTEGER)), 0) + 1, NOW()
FROM quotation WHERE quotation_number ~ '^QUO-[0-9]+$' GROUP BY tenant_id
ON CONFLICT (tenant_id, doc_type) DO UPDATE
    SET next_value = GREATEST(document_sequence.next_value, EXCLUDED.next_value),
        updated_at = NOW();

INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
SELECT tenant_id, 'PURCHASE',
       COALESCE(MAX(CAST(SUBSTRING(purchase_number FROM 5) AS INTEGER)), 0) + 1, NOW()
FROM purchase WHERE purchase_number ~ '^PUR-[0-9]+$' GROUP BY tenant_id
ON CONFLICT (tenant_id, doc_type) DO UPDATE
    SET next_value = GREATEST(document_sequence.next_value, EXCLUDED.next_value),
        updated_at = NOW();

INSERT INTO document_sequence (tenant_id, doc_type, next_value, created_at)
SELECT tenant_id, 'PROJECT',
       COALESCE(MAX(CAST(SUBSTRING(project_number FROM 5) AS INTEGER)), 0) + 1, NOW()
FROM project WHERE project_number ~ '^PRJ-[0-9]+$' GROUP BY tenant_id
ON CONFLICT (tenant_id, doc_type) DO UPDATE
    SET next_value = GREATEST(document_sequence.next_value, EXCLUDED.next_value),
        updated_at = NOW();
