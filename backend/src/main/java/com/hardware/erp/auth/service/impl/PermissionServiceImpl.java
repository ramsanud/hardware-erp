package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.dto.PermissionGroupResponse;
import com.hardware.erp.auth.dto.PermissionResponse;
import com.hardware.erp.auth.mapper.UserMapper;
import com.hardware.erp.auth.repository.PermissionRepository;
import com.hardware.erp.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAllByOrderByModuleCodeAscDisplayOrderAsc()
                .stream()
                .map(userMapper::toPermissionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionGroupResponse> findAllGroupedByModule() {
        return userMapper.toPermissionGroups(
                permissionRepository.findAllByOrderByModuleCodeAscDisplayOrderAsc());
    }
}
