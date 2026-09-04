-- Platform Admin Console, CR-057 phase 4 (Support Center). A real two-sided
-- feature: tenant users raise a ticket from inside their own shop's app,
-- platform admins triage/reply/resolve it from the console. Supersedes
-- nothing - NotificationService.contactAdmin() (CR-028) stays as the
-- lighter-weight "email support about a general problem" path; this is
-- for a trackable, threaded ticket with a real lifecycle.
CREATE TABLE support_ticket (
    support_ticket_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant (tenant_id),
    user_id             BIGINT NOT NULL REFERENCES app_user (user_id),
    subject             VARCHAR(200) NOT NULL,
    description         VARCHAR(4000) NOT NULL,
    category            VARCHAR(30) NOT NULL,
    priority            VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status              VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    -- No FK, deliberately - mirrors platform_audit_log.admin_id (V39):
    -- a ticket must remain readable even if the assigned admin account is
    -- ever removed, and REQUIRES_NEW audit writes must never race a FK.
    assigned_admin_id   BIGINT,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    updated_at          TIMESTAMP(3),
    resolved_at         TIMESTAMP(3),
    CONSTRAINT ck_support_ticket_category CHECK (category IN (
        'LOGIN', 'INVOICE', 'PAYMENT', 'PURCHASE', 'INVENTORY',
        'WHATSAPP', 'SUBSCRIPTION', 'TECHNICAL', 'OTHER')),
    CONSTRAINT ck_support_ticket_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_support_ticket_status CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'WAITING_FOR_USER', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_support_ticket_tenant ON support_ticket (tenant_id, created_at DESC);
CREATE INDEX idx_support_ticket_status ON support_ticket (status);
CREATE INDEX idx_support_ticket_assigned ON support_ticket (assigned_admin_id);

-- One row per message in the thread. author_name is a deliberate snapshot
-- (not a join) - a tenant user and a platform admin live in two completely
-- separate tables with no common key, so this is the one clean way to
-- render a mixed thread without two different join paths in every query.
CREATE TABLE support_ticket_message (
    support_ticket_message_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    support_ticket_id          BIGINT NOT NULL REFERENCES support_ticket (support_ticket_id) ON DELETE CASCADE,
    author_type                 VARCHAR(20) NOT NULL,
    author_id                   BIGINT NOT NULL,
    author_name                 VARCHAR(200) NOT NULL,
    message                     VARCHAR(4000) NOT NULL,
    -- An internal note must never be visible to the tenant - enforced in
    -- the service layer's own two separate read paths (tenant-facing vs
    -- admin-facing), not by this column alone.
    internal                    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP(3) NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_support_ticket_message_author_type CHECK (author_type IN ('TENANT_USER', 'PLATFORM_ADMIN'))
);

CREATE INDEX idx_support_ticket_message_ticket ON support_ticket_message (support_ticket_id, created_at ASC);
