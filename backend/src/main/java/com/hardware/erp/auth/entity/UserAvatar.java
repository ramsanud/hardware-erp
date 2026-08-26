package com.hardware.erp.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Deliberately its own table, not a column on app_user - app_user is
 * reloaded in full on every authenticated request (JwtAuthenticationFilter,
 * BUG-AUTH-001) and an eager image column there would bloat every request.
 * Queried only by AvatarController, never joined into the auth hot path.
 */
@Entity
@Table(name = "user_avatar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Integer fileSize;

    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
