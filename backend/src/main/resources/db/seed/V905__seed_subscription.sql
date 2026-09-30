-- CR-088. The seeded shop (tenant 1) runs on PREMIUM in dev and test so
-- every existing integration test keeps reaching the modules it exercises
-- (quotations, credit notes, imports, AI) now that those are plan-gated.
-- Plan-gating itself is tested against freshly registered shops on each
-- tier (SubscriptionControllerIT), never against this row.
UPDATE tenant SET subscription_tier = 'MAX', subscription_trial_expires_at = NULL WHERE tenant_id = 1;
UPDATE tenant_subscription ts
   SET subscription_plan_id = (SELECT subscription_plan_id FROM subscription_plan WHERE plan_code = 'PREMIUM'),
       status = 'ACTIVE', trial_ends_at = NULL
 WHERE ts.tenant_id = 1;
