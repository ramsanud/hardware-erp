package com.hardware.erp.notification.entity;

import com.hardware.erp.common.security.FieldEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparent AES-256-GCM encryption at the entity boundary (CR-018's
 * FieldEncryptor, reused as-is) - TenantWhatsAppConnection.accessToken is
 * always plaintext in Java, always encrypted in the database column. This
 * is the one credential in the whole WhatsApp feature that must never
 * reach the frontend or a log line; see TenantWhatsAppConnectionService.
 */
@Converter
public class WhatsAppAccessTokenConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldEncryptor.decrypt(dbData);
    }
}
