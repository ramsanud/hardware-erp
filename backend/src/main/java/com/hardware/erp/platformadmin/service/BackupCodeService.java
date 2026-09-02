package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminBackupCode;
import com.hardware.erp.platformadmin.repository.PlatformAdminBackupCodeRepository;
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
 * One-time MFA recovery codes - the standard companion to TOTP enrollment
 * (every major provider issues these), and a real gap without them: losing
 * the enrolled phone would otherwise lock a platform admin out permanently,
 * with no owner-reset path the way a tenant user has via forgot-password.
 *
 * Same shape as a password: only the SHA-256 hash is ever stored.
 */
@Service
@RequiredArgsConstructor
public class BackupCodeService {

    private static final int CODE_COUNT = 10;
    private static final int CODE_DIGITS = 10;

    private final PlatformAdminBackupCodeRepository backupCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Regenerates the full set, replacing any that existed before - called once, right after enrollment confirm. */
    @Transactional
    public List<String> issueNewSet(PlatformAdmin admin) {
        backupCodeRepository.deleteAllByAdminId(admin.getId());

        List<String> plainCodes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = randomDigits();
            plainCodes.add(code);
            backupCodeRepository.save(PlatformAdminBackupCode.builder()
                    .admin(admin)
                    .codeHash(hash(code))
                    .build());
        }
        return plainCodes;
    }

    /** Consumes the code if valid and unused. Returns whether it was accepted. */
    @Transactional
    public boolean consume(PlatformAdmin admin, String rawCode) {
        if (rawCode == null || !rawCode.matches("\\d{" + CODE_DIGITS + "}")) {
            return false;
        }
        return backupCodeRepository.findByAdminIdAndCodeHash(admin.getId(), hash(rawCode))
                .filter(PlatformAdminBackupCode::isUsable)
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
