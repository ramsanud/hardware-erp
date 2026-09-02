package com.hardware.erp.supportticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per message. authorName is a deliberate snapshot, not a join -
 * see the V47 migration comment for why (tenant users and platform admins
 * live in two disjoint tables with no common key).
 */
@Entity
@Table(name = "support_ticket_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "support_ticket_message_id")
    private Long id;

    @Column(name = "support_ticket_id", nullable = false)
    private Long supportTicketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 20)
    private MessageAuthorType authorType;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_name", nullable = false, length = 200)
    private String authorName;

    @Column(name = "message", nullable = false, length = 4000)
    private String message;

    /** Never visible to the tenant - enforced by the two separate read paths in SupportTicketService, not by this column alone. */
    @Column(name = "internal", nullable = false)
    @Builder.Default
    private boolean internal = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
