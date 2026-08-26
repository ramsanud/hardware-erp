package com.hardware.erp.supplier;

import com.hardware.erp.common.security.FieldEncryptor;
import com.hardware.erp.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * CR-018 one-time data migration: encrypts any supplier.bank_account_no rows
 * still holding plaintext (created before this feature existed, or written
 * while APP_ENCRYPTION_KEY was unset). Idempotent - every already-encrypted
 * row is excluded by the repository query itself (no "ENC:" prefix = not
 * yet done), so this is safe to run on every startup, not just the first.
 *
 * Deliberately plain Java, not a SQL migration (V17 only widens the column):
 * encryption needs the application's key, which a Flyway SQL migration has
 * no access to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierBankAccountEncryptionRunner implements ApplicationRunner {

    private final SupplierRepository supplierRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!FieldEncryptor.isConfigured()) {
            log.warn("APP_ENCRYPTION_KEY is not set - supplier bank account numbers are "
                    + "stored in plaintext. Set it to enable encryption at rest (CR-018).");
            return;
        }

        var plaintextRows = supplierRepository.findPlaintextBankAccountNumbers();
        if (plaintextRows.isEmpty()) {
            return;
        }
        for (Object[] row : plaintextRows) {
            Long id = ((Number) row[0]).longValue();
            String plaintext = (String) row[1];
            supplierRepository.writeEncryptedBankAccountNumber(id, FieldEncryptor.encrypt(plaintext));
        }
        log.info("Encrypted {} supplier bank account number(s) at rest (CR-018 backfill).",
                plaintextRows.size());
    }
}
