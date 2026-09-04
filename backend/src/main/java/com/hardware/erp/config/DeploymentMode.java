package com.hardware.erp.config;

/**
 * CR-059 - which of the two supported installations this process is.
 *
 * The application code is identical in both; what differs is who runs the
 * database, whether the browser reaches the API over TLS, and whether
 * subscription billing means anything at all. Making that an explicit,
 * validated property rather than an unwritten assumption is the whole point:
 * before this existed, a self-hosted install pointed at the SaaS database was
 * a configuration nobody would notice until data appeared in the wrong place.
 *
 * Deliberately NOT named after a vendor. Supabase is a managed PostgreSQL
 * endpoint and nothing more - no Supabase SDK, no Supabase Auth, no Supabase
 * Storage exists anywhere in this codebase (uploads are bytea in PostgreSQL,
 * auth is this application's own JWT + MFA). Swapping Supabase for Neon, RDS
 * or a managed instance elsewhere is a change of DB_HOST, not a change of
 * mode.
 */
public enum DeploymentMode {

    /**
     * The hosted, multi-tenant SaaS: managed PostgreSQL (Supabase), the API on
     * a platform host, the frontend on a CDN, HTTPS everywhere, subscription
     * billing live. Shops that buy the hosted product land here.
     */
    CLOUD,

    /**
     * The client's own machine or server, brought up by
     * {@code docker compose -f docker-compose.selfhosted.yml up -d}: PostgreSQL,
     * API and frontend all as containers, typically reached over a LAN address
     * with no public DNS and no certificate. Nothing here calls out to the
     * internet to work, and the client has already paid for the software - so
     * subscription billing and plan caps are off (CR-059).
     */
    SELF_HOSTED;

    public boolean isSelfHosted() {
        return this == SELF_HOSTED;
    }

    public boolean isCloud() {
        return this == CLOUD;
    }
}
