package com.hardware.erp.security.captcha;

public interface CaptchaService {

    /** True when a real challenge is configured and callers must supply a token. */
    boolean active();

    /** The public site key for the browser widget, or null when inactive. */
    String siteKey();

    /**
     * Verifies a widget token.
     *
     * @throws com.hardware.erp.common.exception.BusinessException when a challenge
     *         is active and the token is missing, malformed or rejected.
     */
    void verify(String token, String remoteIp);
}
