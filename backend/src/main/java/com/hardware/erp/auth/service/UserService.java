package com.hardware.erp.auth.service;

import com.hardware.erp.auth.dto.*;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    UserResponse update(Long id, UpdateUserRequest request);

    UserResponse updateOwnProfile(Long userId, UpdateProfileRequest request);

    UserResponse get(Long id);

    PageResponse<UserResponse> search(String search, UserStatus status,
                                      Long roleId, Pageable pageable);

    void resetPassword(Long id, ResetUserPasswordRequest request);

    void softDelete(Long id, Long actingUserId);

    /** CR-058: soft-deleted accounts for this tenant, newest deletion first. Invisible to every other query - see UserRepository.findDeletedByTenantId. */
    java.util.List<UserDeletedResponse> listDeleted();

    /** CR-058: undoes softDelete. 404 unless the account is this tenant's AND is genuinely deleted. Never restores token_version - old tokens stay dead. */
    void restore(Long id);

    /** CR-053 backlog item 6. Verifies id belongs to the caller's own tenant before reading any activity - see ActivityLogRepository's own javadoc for why that matters. */
    PageResponse<UserActivityResponse> activity(Long id, Pageable pageable);
}
