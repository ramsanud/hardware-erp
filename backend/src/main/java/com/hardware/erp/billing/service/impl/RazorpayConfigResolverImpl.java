package com.hardware.erp.billing.service.impl;

import com.hardware.erp.billing.config.EffectiveRazorpayConfig;
import com.hardware.erp.billing.config.RazorpayProperties;
import com.hardware.erp.billing.service.RazorpayConfigResolver;
import com.hardware.erp.platformadmin.entity.PlatformRazorpayConfig;
import com.hardware.erp.platformadmin.repository.PlatformRazorpayConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RazorpayConfigResolverImpl implements RazorpayConfigResolver {

    private final PlatformRazorpayConfigRepository configRepository;
    private final RazorpayProperties envProperties;

    @Override
    @Transactional(readOnly = true)
    public EffectiveRazorpayConfig resolve() {
        return configRepository.findById(1L)
                .filter(PlatformRazorpayConfig::isEnabled)
                .map(this::fromDatabase)
                .orElseGet(this::fromEnvironment);
    }

    private EffectiveRazorpayConfig fromDatabase(PlatformRazorpayConfig row) {
        return new EffectiveRazorpayConfig(
                row.active(), row.getKeyId(), row.getKeySecret(),
                row.webhookActive(), row.getWebhookSecret(),
                envProperties.apiBaseUrl(),
                row.getProPlanAmountPaise(), row.getMaxPlanAmountPaise());
    }

    private EffectiveRazorpayConfig fromEnvironment() {
        return new EffectiveRazorpayConfig(
                envProperties.active(), envProperties.keyId(), envProperties.keySecret(),
                envProperties.webhookActive(), envProperties.webhookSecret(),
                envProperties.apiBaseUrl(),
                envProperties.proPlanAmountPaise(), envProperties.maxPlanAmountPaise());
    }
}
