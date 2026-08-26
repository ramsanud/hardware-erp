package com.hardware.erp.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A product's photo (CR-036) - own table, never joined into the product list/search read path. See UserAvatar for why. */
@Entity
@Table(name = "product_image")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Integer fileSize;

    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
