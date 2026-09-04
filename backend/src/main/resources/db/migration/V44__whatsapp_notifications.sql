-- Task 05 (WhatsApp reminders, MUST-HAVE). NotificationChannel.WHATSAPP
-- already existed as an enum value (notification_log.channel) but nothing
-- in the codebase ever actually sent through it - sendToCustomer() only
-- ever attempted SMS and EMAIL. This migration only adds the one column
-- genuinely new work needs: the provider's own message id, kept for
-- future delivery-status reconciliation once a webhook endpoint exists.
-- No new table - notification_log already has the right shape (CR-027).

ALTER TABLE notification_log
    ADD COLUMN provider_message_id VARCHAR(100);

COMMENT ON COLUMN notification_log.provider_message_id IS
    'The id a real provider (WhatsApp Cloud API, SMS gateway) returns on accepting a message - null for LOGGED_ONLY/FAILED rows. Not yet reconciled against delivery-status webhooks - see WhatsAppBusinessProvider''s own comment for that limitation.';
