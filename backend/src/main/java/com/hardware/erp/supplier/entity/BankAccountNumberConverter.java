package com.hardware.erp.supplier.entity;

import com.hardware.erp.common.security.FieldEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparent AES-256-GCM encryption at the entity boundary (CR-018) -
 * Supplier.bankAccountNo is always plaintext in Java, always encrypted in
 * the database column. Not autoApply: this is deliberately scoped to the
 * one field it was built for, not silently applied to every String column
 * that happens to share a type.
 */
@Converter
public class BankAccountNumberConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldEncryptor.decrypt(dbData);
    }
}
