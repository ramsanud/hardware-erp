package com.hardware.erp.product.substitute.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * CR-089 §7/§9. The part of the scoring policy the OWNER controls, one row
 * per shop. The scoring weights themselves (same-category +30 etc.) are
 * platform configuration (SubstituteScoringProperties), not this - an
 * owner tuning "same material is worth 5 or 7 points" is a support call
 * waiting to happen, while "do not show me anything under 60" and "hide
 * things over the customer's budget" are genuinely their decision.
 *
 * Absent row = the defaults below, which match the migration's own column
 * defaults. Nothing creates the row until an owner changes something.
 */
@Entity
@Table(name = "substitute_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubstituteSetting {

    public static final int DEFAULT_MIN_SCORE = 40;
    public static final boolean DEFAULT_SHOW_ABOVE_BUDGET = true;
    public static final int DEFAULT_MAX_RESULTS = 3;

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "min_score_threshold", nullable = false)
    @Builder.Default
    private Integer minScoreThreshold = DEFAULT_MIN_SCORE;

    @Column(name = "show_above_budget", nullable = false)
    @Builder.Default
    private boolean showAboveBudget = DEFAULT_SHOW_ABOVE_BUDGET;

    @Column(name = "max_results", nullable = false)
    @Builder.Default
    private Integer maxResults = DEFAULT_MAX_RESULTS;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    /** The defaults a shop that has never opened the settings screen gets. */
    public static SubstituteSetting defaults(Long tenantId) {
        return SubstituteSetting.builder().tenantId(tenantId).build();
    }
}
