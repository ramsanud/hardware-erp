package com.hardware.erp.product.substitute.entity;

import com.hardware.erp.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * CR-089 §14. One suggestion the engine made for a request, with the score
 * and the human-readable reason that produced it - persisted so the shop
 * can later ask which alternatives get accepted (§25's analytics), and so
 * an owner can see why something was offered months afterwards.
 */
@Entity
@Table(name = "product_request_suggestion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequestSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_request_suggestion_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_request_id", nullable = false)
    private ProductRequestRecord productRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggested_product_id", nullable = false)
    private Product suggestedProduct;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_level", nullable = false, length = 20)
    private MatchLevel matchLevel;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SuggestionSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
