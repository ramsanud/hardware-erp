package com.hardware.erp.auth.service;

import com.hardware.erp.auth.dto.*;

import java.util.List;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    void logoutAllDevices(Long userId);

    void changePassword(Long userId, ChangePasswordRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    UserResponse currentUser(Long userId);

    List<SessionResponse> activeSessions(Long userId, String currentRawRefreshToken);

    void revokeSession(Long userId, Long sessionId);
}
