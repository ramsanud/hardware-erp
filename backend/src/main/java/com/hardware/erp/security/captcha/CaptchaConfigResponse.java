package com.hardware.erp.security.captcha;

/**
 * Public, unauthenticated: the sign-in page has to know whether to render a
 * challenge before anyone has signed in. Carries the *site* key only - the
 * secret never leaves the server.
 */
public record CaptchaConfigResponse(boolean enabled, String siteKey) {}
