package com.hardware.erp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CR-087. Structure plus Modulo-36 checksum (see {@link com.hardware.erp.common.util.Gstin}).
 * Null and blank pass - every GSTIN field in this app is optional, and
 * "required" stays the job of {@code @NotBlank} where a screen needs it.
 */
@Documented
@Constraint(validatedBy = GstinValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGstin {

    String message() default "Enter a valid 15-character GSTIN";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
