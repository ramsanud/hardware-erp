package com.hardware.erp.auth.service;

import com.hardware.erp.auth.dto.*;

import java.util.List;

public interface AuthService {

    /** CR-058 - a successful password check never returns a session directly; it returns an MFA challenge. */
    LoginChallengeResponse login(LoginRequest request);

    MfaEnrollResponse enrollMfa(MfaTokenRequest request);

    MfaConfirmResponse confirmMfaEnroll(MfaVerifyRequest request);

    LoginResponse verifyMfa(MfaVerifyRequest request);

    /** CR-078 - another sign-in code to the same address; the challenge token is unchanged. */
    OtpSentResponse resendEmailCode(MfaTokenRequest request);

    /** CR-078 - an authenticator app added from the profile by a user who is already signed in. */
    MfaEnrollResponse beginMfaSetup(Long userId);

    /** CR-078 - confirms {@link #beginMfaSetup} and returns the one-time backup codes, shown exactly once. */
    List<String> confirmMfaSetup(Long userId, String code);

    LoginResponse refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    void logoutAllDevices(Long userId);

    void changePassword(Long userId, ChangePasswordRequest request);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    /** CR-078 - the code path of forgot-password. */
    void resetPasswordWithCode(ResetPasswordWithCodeRequest request);

    /** CR-078 - sends a step-up code to the signed-in user's verified email. */
    OtpSentResponse sendStepUpCode(Long userId);

    /** CR-078 - exchanges a correct step-up code for a short-lived step-up token. */
    StepUpResponse verifyStepUp(Long userId, String code);

    /**
     * CR-078 - checks a step-up token presented to a sensitive endpoint
     * belongs to this user and is live. Throws otherwise; the endpoint
     * never sees a half-valid token.
     */
    void requireStepUp(Long userId, String stepUpToken);

    UserResponse currentUser(Long userId);

    List<SessionResponse> activeSessions(Long userId, String currentRawRefreshToken);

    void revokeSession(Long userId, Long sessionId);
}
