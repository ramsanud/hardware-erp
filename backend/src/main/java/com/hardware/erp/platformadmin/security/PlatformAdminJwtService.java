package com.hardware.erp.platformadmin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Deliberately not a reuse of security.JwtService, even though several
 * methods below are near-identical - see PlatformAdminJwtProperties. Its own
 * SecretKey instance means a tenant JWT can never parse successfully here,
 * and vice versa, independent of any coincidence in the two configured
 * secrets.
 */
@Slf4j
@Service
public class PlatformAdminJwtService {

    public static final String CLAIM_TOKEN_VERSION = "tv";
    public static final String CLAIM_PURPOSE = "purpose";

    private static final int REFRESH_TOKEN_BYTES = 48;
    private static final int MIN_SECRET_BYTES = 32;

    private final PlatformAdminJwtProperties properties;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PlatformAdminJwtService(PlatformAdminJwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.platform-admin.jwt.secret must decode to at least 32 bytes for HS256. "
                    + "Generate one with: openssl rand -base64 32");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** A full session token. No purpose claim - its absence is what distinguishes it from an MFA challenge token. */
    public String generateAccessToken(Long adminId, Integer tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(adminId))
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenSeconds())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Proves only "the password check for this admin id already passed" -
     * carries no authorities and is never accepted by
     * PlatformAdminAuthenticationFilter for a protected endpoint.
     */
    public String generateMfaToken(Long adminId, MfaTokenPurpose purpose) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(adminId))
                .claim(CLAIM_PURPOSE, purpose.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.mfaTokenMinutes() * 60)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<Claims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected platform-admin JWT: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Optional<Long> adminIdFrom(Claims claims) {
        try {
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (NumberFormatException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    public Optional<Integer> tokenVersionFrom(Claims claims) {
        try {
            return Optional.ofNullable(claims.get(CLAIM_TOKEN_VERSION, Integer.class));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** Empty for a real session token, which carries no purpose claim at all. */
    public Optional<MfaTokenPurpose> purposeFrom(Claims claims) {
        try {
            String raw = claims.get(CLAIM_PURPOSE, String.class);
            return raw == null ? Optional.empty() : Optional.of(MfaTokenPurpose.valueOf(raw));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public long accessTokenSeconds() {
        return properties.accessTokenMinutes() * 60;
    }

    public long mfaTokenSeconds() {
        return properties.mfaTokenMinutes() * 60;
    }

    public long refreshTokenDays() {
        return properties.refreshTokenDays();
    }
}
