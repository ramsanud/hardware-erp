package com.hardware.erp.subscription.repository;

import com.hardware.erp.subscription.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {

    /** Plan code -> feature key pairs for the whole matrix, one query - FeatureAccessServiceImpl caches the result. */
    @Query("SELECT pf.plan.planCode, pf.feature.featureKey FROM PlanFeature pf")
    List<Object[]> matrix();
}
