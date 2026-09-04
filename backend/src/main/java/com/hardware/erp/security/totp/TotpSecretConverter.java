package com.hardware.erp.security.totp;

import com.hardware.erp.common.security.FieldEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparent AES-256-GCM encryption for a totp_secret column, the same
 * shape as supplier.bank_account_no (CR-018) - a TOTP seed is a standing
 * credential, not a hash, so it must be genuinely retrievable to verify a
 * code, but never sit in the database as plaintext.
 *
 * Shared between the Platform Admin Console (CR-054) and tenant user login
 * (CR-058) - moved here from platformadmin.entity alongside TotpService so
 * both mandatory-MFA implementations encrypt their secret the same way.
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
