package com.hardware.erp.auth.service;

import com.hardware.erp.auth.dto.PermissionGroupResponse;
import com.hardware.erp.auth.dto.PermissionResponse;

import java.util.List;

public interface PermissionService {

    List<PermissionResponse> findAll();

    List<PermissionGroupResponse> findAllGroupedByModule();
}
