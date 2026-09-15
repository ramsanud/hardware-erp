package com.hardware.erp.common.validation;

import com.hardware.erp.common.util.Gstin;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** CR-087. */
public class GstinValidator implements ConstraintValidator<ValidGstin, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return Gstin.isValidOrBlank(value);
    }
}
