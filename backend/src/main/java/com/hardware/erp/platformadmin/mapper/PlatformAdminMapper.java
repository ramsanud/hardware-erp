package com.hardware.erp.platformadmin.mapper;

import com.hardware.erp.platformadmin.dto.PlatformAdminResponse;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformPermission;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PlatformAdminMapper {

    public PlatformAdminResponse toResponse(PlatformAdmin admin) {
        return new PlatformAdminResponse(
                admin.getId(),
                admin.getFullName(),
                admin.getEmail(),
                admin.getRole().name(),
                admin.permissions().stream().map(PlatformPermission::name)
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new)),
                admin.isMfaEnabled(),
                admin.getStatus().name());
    }
}
