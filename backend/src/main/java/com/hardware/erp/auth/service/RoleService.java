package com.hardware.erp.auth.service;

import com.hardware.erp.auth.dto.RoleRequest;
import com.hardware.erp.auth.dto.RoleResponse;

import java.util.List;

public interface RoleService {

    List<RoleResponse> findAll();

    RoleResponse get(Long id);

    RoleResponse create(RoleRequest request);

    RoleResponse update(Long id, RoleRequest request);

    void delete(Long id);
}
