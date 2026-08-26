-- =====================================================================
-- DEV SEED : CATEGORIES, BRANDS, AND BACKFILL ONTO EXISTING PRODUCTS
-- Never loaded in production - see the note at the top of V900.
--
-- The Products list screen was showing every category/brand column as
-- "-" because the products seeded by V902 (and the two created by hand
-- during live testing) had neither set. This adds a realistic set of
-- both and assigns every existing product to one, by product_code -
-- V902's migration itself is never edited (Flyway rule).
-- =====================================================================

INSERT INTO category (tenant_id, category_code, category_name, status, created_at, version)
SELECT 1, code, name, 'ACTIVE', CURRENT_TIMESTAMP, 0
FROM (VALUES
    ('CAT-0002', 'Locks & Hardware'),
    ('CAT-0003', 'Paints & Finishes'),
    ('CAT-0004', 'Electrical'),
    ('CAT-0005', 'Plumbing'),
    ('CAT-0006', 'Power & Hand Tools'),
    ('CAT-0007', 'Adhesives & Sealants'),
    ('CAT-0008', 'Fasteners')
) AS seed(code, name)
WHERE NOT EXISTS (
    SELECT 1 FROM category c WHERE c.tenant_id = 1 AND lower(c.category_name) = lower(seed.name)
);

INSERT INTO brand (tenant_id, brand_code, brand_name, status, created_at, version)
SELECT 1, code, name, 'ACTIVE', CURRENT_TIMESTAMP, 0
FROM (VALUES
    ('BRD-0002', 'Asian Paints'),
    ('BRD-0003', 'Anchor'),
    ('BRD-0004', 'Finolex'),
    ('BRD-0005', 'Stanley'),
    ('BRD-0006', 'Fevicol'),
    ('BRD-0007', 'Bosch'),
    ('BRD-0008', 'Cera'),
    ('BRD-0009', 'Nippon')
) AS seed(code, name)
WHERE NOT EXISTS (
    SELECT 1 FROM brand b WHERE b.tenant_id = 1 AND lower(b.brand_name) = lower(seed.name)
);

-- Backfill: every product that has no category/brand yet gets one that
-- fits, matched by product_code so this is safe to run against whatever
-- already exists (including the two products created by hand during
-- live testing this session, PRD-000001 and PRD-000002).
UPDATE product p
SET category_id = c.category_id
FROM (VALUES
    ('PRD-000001', 'Locks & Hardware'),
    ('PRD-000002', 'Power & Hand Tools'),
    ('PRD-000010', 'Locks & Hardware'),
    ('PRD-000011', 'Paints & Finishes'),
    ('PRD-000012', 'Electrical'),
    ('PRD-000013', 'Electrical'),
    ('PRD-000014', 'Power & Hand Tools'),
    ('PRD-000015', 'Fasteners'),
    ('PRD-000016', 'Plumbing'),
    ('PRD-000017', 'Adhesives & Sealants'),
    ('PRD-000018', 'Power & Hand Tools'),
    ('PRD-000019', 'Plumbing'),
    ('PRD-000020', 'Paints & Finishes'),
    ('PRD-000021', 'Fasteners')
) AS mapping(code, category_name)
JOIN category c ON c.tenant_id = 1 AND lower(c.category_name) = lower(mapping.category_name)
WHERE p.tenant_id = 1 AND p.product_code = mapping.code AND p.category_id IS NULL;

UPDATE product p
SET brand_id = b.brand_id
FROM (VALUES
    ('PRD-000001', 'Godrej'),
    ('PRD-000010', 'Godrej'),
    ('PRD-000011', 'Asian Paints'),
    ('PRD-000012', 'Anchor'),
    ('PRD-000013', 'Finolex'),
    ('PRD-000014', 'Stanley'),
    ('PRD-000017', 'Fevicol'),
    ('PRD-000018', 'Bosch'),
    ('PRD-000019', 'Cera'),
    ('PRD-000020', 'Nippon')
) AS mapping(code, brand_name)
JOIN brand b ON b.tenant_id = 1 AND lower(b.brand_name) = lower(mapping.brand_name)
WHERE p.tenant_id = 1 AND p.product_code = mapping.code AND p.brand_id IS NULL;
