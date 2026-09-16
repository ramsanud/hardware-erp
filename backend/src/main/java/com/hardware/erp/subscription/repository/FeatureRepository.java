package com.hardware.erp.subscription.repository;

import com.hardware.erp.subscription.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, Long> {

    Optional<Feature> findByFeatureKey(String featureKey);

    List<Feature> findAllByOrderByDisplayOrderAsc();
}
