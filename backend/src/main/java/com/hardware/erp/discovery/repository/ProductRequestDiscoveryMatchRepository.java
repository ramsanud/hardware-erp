package com.hardware.erp.discovery.repository;

import com.hardware.erp.discovery.entity.ProductRequestDiscoveryMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRequestDiscoveryMatchRepository extends JpaRepository<ProductRequestDiscoveryMatch, Long> {

    List<ProductRequestDiscoveryMatch> findByProductRequestIdOrderByDistanceKmAscIdAsc(Long productRequestId);

    @Modifying
    @Query("DELETE FROM ProductRequestDiscoveryMatch m WHERE m.productRequestId = :productRequestId")
    void deleteForRequest(Long productRequestId);
}
