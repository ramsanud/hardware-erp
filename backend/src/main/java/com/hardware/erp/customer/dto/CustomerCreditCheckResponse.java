package com.hardware.erp.customer.dto;

/**
 * CR-030 - looked up by exact mobile number while a new invoice is being
 * built (the wizard's customer entry is free text, not a picker), so the
 * Review step can warn before the sale if this invoice would push the
 * customer's balance past their credit limit. creditLimitPaise is never
 * null (the column defaults to 0) - 0 is the "no limit configured"
 * sentinel used everywhere else in this module, and the frontend must
 * never warn when it's 0.
 */
public record CustomerCreditCheckResponse(
        Long customerId,
        String customerName,
        long creditLimitPaise,
        long outstandingBalancePaise
) {}
