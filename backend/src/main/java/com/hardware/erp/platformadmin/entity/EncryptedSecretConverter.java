package com.hardware.erp.platformadmin.entity;

import com.hardware.erp.common.security.FieldEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparent AES-256-GCM encryption at the entity boundary (CR-018's
 * FieldEncryptor, same scheme WhatsAppAccessTokenConverter already reuses
 * for tenant.access_token) - PlatformRazorpayConfig's key_secret/
 * webhook_secret are always plaintext in Java, always encrypted in the
 * database column, and never sent back to the browser once saved.
 */
@Converter
public class EncryptedSecretConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldEncryptor.decrypt(dbData);
    }
}
