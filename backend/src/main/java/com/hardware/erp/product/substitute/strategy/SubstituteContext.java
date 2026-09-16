package com.hardware.erp.product.substitute.strategy;

import com.hardware.erp.product.entity.Product;

import java.math.BigDecimal;

/**
 * CR-089. Everything a strategy is allowed to know about one request:
 * the product the customer asked for, how many they wanted, and what they
 * were willing to pay. Deliberately does NOT carry the customer's identity
 * - no recommendation rule may depend on who is asking.
 */
public record SubstituteContext(
        Long tenantId,
        Product requestedProduct,
        BigDecimal requestedQuantity,
        /** Null when the customer gave no budget. */
        Long requestedBudgetPaise
) {}
