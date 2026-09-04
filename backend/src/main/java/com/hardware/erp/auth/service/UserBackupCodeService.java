package com.hardware.erp.auth.service;

import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserBackupCode;
import com.hardware.erp.auth.repository.UserBackupCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * One-time MFA recovery codes for tenant users (CR-058) - the tenant-side
 * mirror of platformadmin.service.BackupCodeService, same shape: only the
 * SHA-256 hash is ever stored, 10 ten-digit codes issued once right after
 * enrollment confirm.
 */
@Service
@RequiredArgsConstructor
public class UserBackupCodeService {

    private static final int CODE_COUNT = 10;
    private static final int CODE_DIGITS = 10;

    private final UserBackupCodeRepository backupCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Regenerates the full set, replacing any that existed before - called once, right after enrollment confirm. */
    @Transactional
    public List<String> issueNewSet(User user) {
        backupCodeRepository.deleteAllByUserId(user.getId());

        List<String> plainCodes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = randomDigits();
            plainCodes.add(code);
            backupCodeRepository.save(UserBackupCode.builder()
                    .user(user)
                    .codeHash(hash(code))
                    .build());
        }
        return plainCodes;
    }

    /** Consumes the code if valid and unused. Returns whether it was accepted. */
    @Transactional
    public boolean consume(User user, String rawCode) {
        if (rawCode == null || !rawCode.matches("\\d{" + CODE_DIGITS + "}")) {
            return false;
        }
        return backupCodeRepository.findByUserIdAndCodeHash(user.getId(), hash(rawCode))
                .filter(UserBackupCode::isUsable)
                .map(c -> {
                    c.markUsed();
                    backupCodeRepository.save(c);
                    return true;
                })
                .orElse(false);
    }

    private String randomDigits() {
        StringBuilder sb = new StringBuilder(CODE_DIGITS);
        for (int i = 0; i < CODE_DIGITS; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    private String hash(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawCode.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
