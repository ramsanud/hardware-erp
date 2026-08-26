package com.hardware.erp.auth.repository;

import com.hardware.erp.auth.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleCodeAscDisplayOrderAsc();

    Set<Permission> findByCodeIn(Set<String> codes);

    boolean existsByCode(String code);
}
