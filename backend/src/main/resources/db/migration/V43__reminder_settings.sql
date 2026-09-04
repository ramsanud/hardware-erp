-- CR-053 backlog item 5: reminder settings. Two of the five reminder types
-- from the original request (payment-due digest, low-stock alert) - the
-- other three (SMS-on-transaction, daily sales-summary digest, WhatsApp
-- alerts) are deferred, same "bounded, one real thing at a time" reasoning
-- as every other item in this backlog. See ReminderSchedulerService's own
-- javadoc for what the daily job actually does with these.

ALTER TABLE tenant
    ADD COLUMN payment_due_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN low_stock_alert_enabled      BOOLEAN NOT NULL DEFAULT FALSE;
