package com.hardware.erp.auth.service;

import com.hardware.erp.auth.entity.EmailOtpPurpose;
import com.hardware.erp.auth.entity.User;

/**
 * CR-078 - one-time codes by email: issue, verify, consume.
 *
 * Every flow that uses a code (registration, sign-in fallback, password
 * reset, step-up) goes through these two methods; the purpose is what
 * keeps them apart. Nothing here decides whether an account exists or
 * whether a caller may ask - that is the calling flow's job, because the
 * answer differs (forgot-password must stay silent, registration may not).
 */
public interface EmailOtpService {

    /** How long a code stays valid. Short because the space is only a million values. */
    int VALID_MINUTES = 10;

    /** A second code for the same address and purpose is refused inside this window, to stop mailbox flooding. */
    int RESEND_COOLDOWN_SECONDS = 60;

    enum IssueResult {
        SENT,
        /** A code was sent less than {@link #RESEND_COOLDOWN_SECONDS} ago and is still live. Nothing was sent. */
        COOLDOWN
    }

    /**
     * Generates a fresh code, kills any live one for the same address and
     * purpose, stores the hash and emails the code.
     *
     * @param user null before the account exists (registration)
     * @param recipientName for the greeting; null is fine
     */
    IssueResult issue(String email, User user, EmailOtpPurpose purpose, String recipientName);

    /**
     * Checks a code against the newest live one for the address and purpose,
     * consuming it on success and counting the attempt on failure.
     *
     * Returns false - never throws - so each caller reports failure in its
     * own vocabulary (a sign-in says INVALID_MFA_CODE, a reset says
     * INVALID_OTP) and nothing about the code's existence leaks through a
     * different exception type.
     */
    boolean verify(String email, EmailOtpPurpose purpose, String code);

    /**
     * Like {@link #issue} but returns the raw code instead of emailing it, for
     * the one caller that puts the code inside an email it composes itself
     * (the password-reset mail, which also carries the link). Null on cooldown.
     */
    String issueRaw(String email, User user, EmailOtpPurpose purpose);

}
