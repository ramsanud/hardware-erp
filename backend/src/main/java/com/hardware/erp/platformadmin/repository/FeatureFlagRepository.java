package com.hardware.erp.platformadmin.repository;

import com.hardware.erp.platformadmin.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {

    Optional<FeatureFlag> findByFlagKey(String flagKey);

    boolean existsByFlagKey(String flagKey);

    List<FeatureFlag> findAllByOrderByFlagKeyAsc();
}
