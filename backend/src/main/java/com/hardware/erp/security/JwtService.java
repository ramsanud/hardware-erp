package com.hardware.erp.security;

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

@Slf4j
@Service
public class JwtService {

    /** Token version. Two characters because every request carries it. */
    public static final String CLAIM_TOKEN_VERSION = "tv";
    /** CR-058 - present only on an MFA challenge token, never on a real session token. */
    public static final String CLAIM_PURPOSE = "purpose";

    private static final int REFRESH_TOKEN_BYTES = 48;
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must decode to at least 32 bytes for HS256. "
                    + "Generate one with: openssl rand -base64 32");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Minimal claims only: issuer, subject, token version, issued-at, expiry.
     *
     * No name, role, email or permission list. A JWT is signed, not encrypted -
     * anyone holding it reads the payload. Carrying authorities would also mean
     * a permission revoked at 10:00 stays live until the token expires.
     */
    public String generateAccessToken(Long userId, Integer tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenSeconds())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * CR-058 - proves only "the password check for this user id already
     * passed". Carries no token version and no authorities, and is never
     * accepted by JwtAuthenticationFilter for a protected endpoint - only
     * the absence of {@link #CLAIM_PURPOSE} marks a token as a real session.
     */
    public String generateMfaToken(Long userId, MfaTokenPurpose purpose) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_PURPOSE, purpose.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(mfaTokenSeconds())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
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
            // Reason is logged at DEBUG only. Telling the client whether a token
            // was expired, forged or malformed is free reconnaissance.
            log.debug("Rejected JWT: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Subject is written by us, but a malformed value must not throw here. */
    public Optional<Long> userIdFrom(Claims claims) {
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

    /**
     * Refresh tokens are opaque random strings, not JWTs: they carry no claims,
     * cannot be read client-side, and revocation is a database lookup rather
     * than a blacklist.
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Only the hash is ever persisted, exactly as with passwords. */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public long accessTokenSeconds() {
        return properties.accessTokenMinutes() * 60;
    }

    public long refreshTokenDays() {
        return properties.refreshTokenDays();
    }

    public long mfaTokenSeconds() {
        return properties.mfaTokenMinutes() * 60;
    }
}
