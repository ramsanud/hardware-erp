-- =====================================================================
-- CR-053 phase 1 : Invoice PDF themes.
--
-- A shop-wide default look for the generated invoice/quotation PDF -
-- colour accents, header fill, font family - never a photographic
-- background image (no such asset exists in this codebase, and pasting
-- one in without real design input would look cheap, not "themed").
-- Four CSS-only "token recipes" (CLASSIC/MINIMAL/BOLD/ELEGANT), the same
-- pattern CR-034 already used successfully for the frontend's own design
-- styles: one shared structural stylesheet, a small set of colour/font
-- tokens swapped per theme, so the four skins cannot drift out of sync
-- with each other or with the underlying (load-bearing, GST-correctness)
-- table/pagination CSS.
--
-- CLASSIC is the default and is, deliberately, byte-for-byte what every
-- invoice already rendered as before this migration - an existing tenant
-- that never opens Settings sees no change at all.
-- =====================================================================

ALTER TABLE tenant
    ADD COLUMN invoice_theme VARCHAR(20) NOT NULL DEFAULT 'CLASSIC';

ALTER TABLE tenant
    ADD CONSTRAINT ck_tenant_invoice_theme
        CHECK (invoice_theme IN ('CLASSIC', 'MINIMAL', 'BOLD', 'ELEGANT'));
