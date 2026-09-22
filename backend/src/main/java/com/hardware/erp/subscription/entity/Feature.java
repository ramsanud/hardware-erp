package com.hardware.erp.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** CR-088. One row per FeatureKey - the human-readable side of the catalogue. */
@Entity
@Table(name = "feature")
@Getter
@Setter
@NoArgsConstructor
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_id")
    private Long id;

    @Column(name = "feature_key", nullable = false, length = 50)
    private String featureKey;

    @Column(name = "feature_name", nullable = false, length = 100)
    private String featureName;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "module_code", nullable = false, length = 30)
    private String moduleCode;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
