-- CR-097 - typo-tolerant product search inside PostgreSQL.
--
-- Counter staff type what they hear: "towr bolt", "hammr", "fevicol sr98".
-- ProductRepository.search is a case-insensitive substring match, so any of
-- those returns an empty page and the sale stalls while someone walks over
-- to the shelf. pg_trgm answers the same question by trigram overlap, which
-- survives a dropped or swapped letter, and it ships inside every PostgreSQL
-- this product runs on - Supabase (CLOUD) and postgres:16-alpine (self-hosted
-- and the Testcontainers tier) - so there is no search cluster to deploy,
-- back up or keep in sync with the tenant's catalogue.
--
-- Version 64: V57-V63 were claimed by the two feature branches in flight on
-- 2026-09-16 (email OTP, and the SaaS platform pack), both now merged ahead
-- of this one. Renumbered from CR-096 to CR-097 at consolidation: CR-096 is
-- the Render keep-alive.
--
-- V60 (CR-089 Smart Substitute) already created the extension and a
-- non-partial idx_product_name_trgm for its own name-similarity fallback.
-- The partial index below is strictly better for both callers (soft-deleted
-- rows are excluded by every query), so V60's is replaced rather than
-- duplicated under a second name.
--
-- On Supabase the extension is normally pre-installed in the "extensions"
-- schema, which the postgres role's search_path already includes; the
-- IF NOT EXISTS makes this a no-op there. On a self-hosted container it
-- installs into public. Neither case needs a schema qualifier in the
-- queries, and nothing here is qualified for that reason.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN, not GiST: the catalogue is read a thousand times for every write, and
-- GIN trigram indexes are the faster of the two for lookups at the cost of a
-- slower insert, which a product create can afford.
--
-- Partial on deleted_at IS NULL to match Product's @SQLRestriction, so a
-- soft-deleted row never costs index space and never surfaces as a "close
-- match" for a live sale. tenant_id is not a leading column - a GIN index
-- cannot lead with a btree-ordered scalar without btree_gin - so the planner
-- combines this with idx_product_tenant; at the sizes a single shop reaches
-- (tens of thousands of rows) the recheck is cheap, and every query still
-- carries tenant_id = ? as its first predicate (CR-016).
DROP INDEX IF EXISTS idx_product_name_trgm;
CREATE INDEX idx_product_name_trgm
    ON product USING gin (product_name gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_product_code_trgm
    ON product USING gin (product_code gin_trgm_ops)
    WHERE deleted_at IS NULL;
