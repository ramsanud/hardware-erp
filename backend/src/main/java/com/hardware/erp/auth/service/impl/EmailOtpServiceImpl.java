package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.entity.EmailOtp;
import com.hardware.erp.auth.entity.EmailOtpPurpose;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.repository.EmailOtpRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.auth.service.EmailOtpService;
import com.hardware.erp.auth.service.OtpMailService;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.security.JwtService;
import com.hardware.erp.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * CR-078. The rules, in one place:
 *
 * <ul>
 *   <li>six digits from {@link SecureRandom}, zero-padded, so "004217" is as likely as any other;</li>
 *   <li>valid ten minutes; five wrong guesses kill it; used once;</li>
 *   <li>at most one live code per (address, purpose) - issuing a new one consumes the old;</li>
 *   <li>a resend inside sixty seconds is refused, and the caller decides whether to say so;</li>
 *   <li>the hash is compared in constant time, though with a million-value space that is hygiene, not the defence.</li>
 * </ul>
 *
 * A successful verification also stamps {@code app_user.email_verified_at}
 * when the code went to the user's own address: a person who can read a
 * code sent there owns the address, whatever they were proving it for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpServiceImpl implements EmailOtpService {

    private static final int CODE_DIGITS = 6;
    private static final int CODE_BOUND = 1_000_000;

    private final EmailOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final OtpMailService otpMailService;
    private final JwtService jwtService;
    private final SecurityAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public IssueResult issue(String email, User user, EmailOtpPurpose purpose, String recipientName) {
        String address = normalise(email);
        String code = store(address, user, purpose);
        if (code == null) {
            return IssueResult.COOLDOWN;
        }
        otpMailService.sendCode(address, recipientName, purpose, code, VALID_MINUTES);
        return IssueResult.SENT;
    }

    @Override
    @Transactional
    public String issueRaw(String email, User user, EmailOtpPurpose purpose) {
        return store(normalise(email), user, purpose);
    }

    /** Generates and persists a code, killing any live one first. Null when inside the resend cooldown. */
    private String store(String address, User user, EmailOtpPurpose purpose) {
        Optional<EmailOtp> latest = otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(address, purpose);
        if (latest.isPresent() && latest.get().isUsable()
                && latest.get().getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            return null;
        }

        otpRepository.consumeAllFor(address, purpose, LocalDateTime.now());

        String code = String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", secureRandom.nextInt(CODE_BOUND));
        HttpServletRequest request = currentRequest();
        otpRepository.save(EmailOtp.builder()
                .user(user)
                .email(address)
                .purpose(purpose)
                .codeHash(jwtService.hashToken(code))
                .expiresAt(LocalDateTime.now().plusMinutes(VALID_MINUTES))
                .ipAddress(request != null ? SecurityUtils.clientIp(request) : null)
                .build());
        return code;
    }

    @Override
    @Transactional
    public boolean verify(String email, EmailOtpPurpose purpose, String code) {
        if (email == null || code == null) {
            return false;
        }
        String address = normalise(email);
        String submitted = code.trim();

        Optional<EmailOtp> maybe = otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(address, purpose);
        if (maybe.isEmpty() || !maybe.get().isUsable()) {
            audit(maybe.orElse(null), purpose, "No live code");
            return false;
        }
        EmailOtp otp = maybe.get();

        boolean matches = MessageDigest.isEqual(
                otp.getCodeHash().getBytes(StandardCharsets.UTF_8),
                jwtService.hashToken(submitted).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            boolean exhausted = otp.registerFailedAttempt();
            otpRepository.save(otp);
            audit(otp, purpose, exhausted ? "Wrong code - attempts exhausted" : "Wrong code, attempt " + otp.getAttempts());
            return false;
        }

        otp.consume();
        otpRepository.save(otp);

        User user = otp.getUser();
        if (user != null && user.getEmail() != null && address.equals(normalise(user.getEmail()))
                && user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(LocalDateTime.now());
            userRepository.save(user);
            auditService.success(AuditAction.EMAIL_VERIFIED, user.getId(), user.getFullName(), "USER", user.getId());
        }
        return true;
    }

    private void audit(EmailOtp otp, EmailOtpPurpose purpose, String reason) {
        User user = otp == null ? null : otp.getUser();
        auditService.failure(AuditAction.EMAIL_OTP_FAILED,
                user == null ? null : user.getId(),
                user == null ? null : user.getFullName(),
                purpose + ": " + reason);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
