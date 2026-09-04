package com.hardware.erp.billing.service;

import com.hardware.erp.billing.config.EffectiveRazorpayConfig;

public interface RazorpayConfigResolver {

    /** The database-configured row wins when present and enabled; RAZORPAY_* environment variables are the fallback. */
    EffectiveRazorpayConfig resolve();
}
