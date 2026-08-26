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
}
