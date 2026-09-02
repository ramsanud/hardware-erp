package com.hardware.erp.platformadmin.entity;

import com.hardware.erp.common.security.FieldEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparent AES-256-GCM encryption for platform_admin.totp_secret, the
 * same shape as supplier.bank_account_no (CR-018) - a TOTP seed is a
 * standing credential, not a hash, so it must be genuinely retrievable to
 * verify a code, but never sit in the database as plaintext.
 */
@Converter
public class TotpSecretConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldEncryptor.decrypt(dbData);
    }
}
