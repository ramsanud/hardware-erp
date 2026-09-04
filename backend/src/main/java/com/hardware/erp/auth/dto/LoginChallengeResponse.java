package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a successful password check returns.
 *
 * CR-058 made this an MFA challenge rather than a session: a correct password
 * proves only the first factor, and `enrollmentRequired` tells the frontend
 * whether to route to the QR-enrollment screen or the "enter your 6-digit
 * code" screen.
 *
 * CR-060 added {@link #session}, which is populated ONLY when
 * app.security.mfa-required is false. In that mode there is no second factor
 * to challenge, so the password check completes sign-in on its own and this
 * carries the finished session; mfaToken is null and enrollmentRequired is
 * false.
 *
 * The two are mutually exclusive and exactly one is always present, which is
 * why this stayed a single response type rather than becoming two endpoints:
 * the frontend asks "did I get a session or a challenge?" in one place, and
 * flipping the flag changes no route and no client contract.
 */
@Schema(name = "LoginChallengeResponse")
public record LoginChallengeResponse(

        @Schema(description = "Short-lived token identifying this half-finished sign-in. Null when MFA is disabled.")
        String mfaToken,

        @Schema(description = "True when the account has no authenticator yet and must enroll before it can sign in.")
        boolean enrollmentRequired,

        @Schema(description = "Lifetime of mfaToken. 0 when MFA is disabled.")
        long expiresInSeconds,

        @Schema(description = "The completed session. Populated ONLY when MFA is disabled (CR-060); null otherwise.")
        LoginResponse session
) {

    /** The CR-058 shape: a challenge, no session. */
    public static LoginChallengeResponse challenge(String mfaToken, boolean enrollmentRequired, long expiresInSeconds) {
        return new LoginChallengeResponse(mfaToken, enrollmentRequired, expiresInSeconds, null);
    }

    /** CR-060 - MFA switched off, so the password alone completed sign-in. */
    public static LoginChallengeResponse signedIn(LoginResponse session) {
        return new LoginChallengeResponse(null, false, 0L, session);
    }

    /** True when this response carries a finished session rather than a challenge. */
    public boolean isSignedIn() {
        return session != null;
    }
}
