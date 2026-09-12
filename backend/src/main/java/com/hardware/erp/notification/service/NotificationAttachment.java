package com.hardware.erp.notification.service;

/**
 * One file riding along with an outbound notification (CR-073).
 *
 * Deliberately a detached copy of the bytes rather than the MultipartFile
 * itself: the request's temp file is gone once the controller returns, and
 * a provider is free to send asynchronously later. Nothing here is persisted
 * - a support screenshot reaches the admin inbox and lives in that mailbox,
 * not in this database. Storing it would mean a table, a retention policy and
 * a tenant-scoped download endpoint for a file only support ever reads.
 */
public record NotificationAttachment(String filename, String contentType, byte[] content) {

    /** Rendered into the message body so the log says an image was sent even though the bytes are not kept. */
    public String describe() {
        return "%s (%s, %d KB)".formatted(filename, contentType, Math.max(1, content.length / 1024));
    }
}
