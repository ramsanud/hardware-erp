package com.hardware.erp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLTMyYnl0ZXMh";
    private static final String OTHER_SECRET = "b3RoZXItc2VjcmV0LWtleS1mb3ItdW5pdC10ZXN0cy0zMmJ5dGVzIQ==";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, "hardware-erp", 15, 7));
    }

    @Nested
    @DisplayName("access token")
    class AccessToken {

        @Test
        @DisplayName("round-trips the subject and token version")
        void validToken() {
            String token = jwtService.generateAccessToken(42L, 3);
            Claims claims = jwtService.parse(token).orElseThrow();

            assertThat(jwtService.userIdFrom(claims)).contains(42L);
            assertThat(jwtService.tokenVersionFrom(claims)).contains(3);
            assertThat(claims.getIssuer()).isEqualTo("hardware-erp");
        }

        @Test
        @DisplayName("carries no name, role, email or permission data")
        void carriesNoPersonalData() {
            String token = jwtService.generateAccessToken(42L, 3);

            String payload = new String(Base64.getUrlDecoder()
                    .decode(token.split("\\.")[1]));

            // A JWT is signed, not encrypted. Anyone holding it reads this.
            assertThat(payload)
                    .doesNotContain("name")
                    .doesNotContain("role")
                    .doesNotContain("perm")
                    .doesNotContain("email")
                    .doesNotContain("mobile");
            assertThat(payload).contains("\"sub\":\"42\"").contains("\"tv\":3");
        }

        @Test
        @DisplayName("an expired token is rejected")
        void expiredToken() {
            var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
            String expired = Jwts.builder()
                    .issuer("hardware-erp")
                    .subject("42")
                    .claim(JwtService.CLAIM_TOKEN_VERSION, 0)
                    .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                    .expiration(Date.from(Instant.now().minusSeconds(3600)))
                    .signWith(key, Jwts.SIG.HS256)
                    .compact();

            assertThat(jwtService.parse(expired)).isEmpty();
        }

        @Test
        @DisplayName("a token signed with another key is rejected")
        void invalidSignature() {
            JwtService other = new JwtService(
                    new JwtProperties(OTHER_SECRET, "hardware-erp", 15, 7));
            assertThat(jwtService.parse(other.generateAccessToken(42L, 0))).isEmpty();
        }

        @Test
        @DisplayName("a token whose payload was edited is rejected")
        void tamperedPayload() {
            String token = jwtService.generateAccessToken(42L, 0);
            String[] parts = token.split("\\.");
            String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    "{\"iss\":\"hardware-erp\",\"sub\":\"1\",\"tv\":0}".getBytes());

            assertThat(jwtService.parse(parts[0] + "." + forged + "." + parts[2])).isEmpty();
        }

        @Test
        @DisplayName("a token from a different issuer is rejected")
        void wrongIssuer() {
            JwtService other = new JwtService(
                    new JwtProperties(SECRET, "someone-else", 15, 7));
            assertThat(jwtService.parse(other.generateAccessToken(42L, 0))).isEmpty();
        }

        @Test
        @DisplayName("a missing subject yields empty rather than throwing")
        void missingSubject() {
            var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
            String noSubject = Jwts.builder()
                    .issuer("hardware-erp")
                    .claim(JwtService.CLAIM_TOKEN_VERSION, 0)
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(900)))
                    .signWith(key, Jwts.SIG.HS256)
                    .compact();

            Claims claims = jwtService.parse(noSubject).orElseThrow();
            assertThat(jwtService.userIdFrom(claims)).isEmpty();
        }

        @Test
        @DisplayName("a missing token version yields empty rather than defaulting to 0")
        void missingTokenVersion() {
            var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
            String noVersion = Jwts.builder()
                    .issuer("hardware-erp")
                    .subject("42")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(900)))
                    .signWith(key, Jwts.SIG.HS256)
                    .compact();

            Claims claims = jwtService.parse(noVersion).orElseThrow();
            // Defaulting to 0 here would let a version-less token authenticate a
            // freshly created user.
            assertThat(jwtService.tokenVersionFrom(claims)).isEmpty();
        }

        @Test
        @DisplayName("malformed input is rejected without throwing")
        void malformedToken() {
            assertThat(jwtService.parse("not.a.jwt")).isEmpty();
            assertThat(jwtService.parse("")).isEmpty();
            assertThat(jwtService.parse(null)).isEmpty();
            assertThat(jwtService.parse("....")).isEmpty();
        }
    }

    @Nested
    @DisplayName("refresh token")
    class RefreshToken {

        @Test
        @DisplayName("is opaque, not a JWT")
        void isOpaque() {
            String token = jwtService.generateRefreshToken();
            assertThat(token).doesNotContain(".");
            assertThat(jwtService.parse(token)).isEmpty();
        }

        @Test
        @DisplayName("is unique across generations")
        void isUnique() {
            var seen = new java.util.HashSet<String>();
            for (int i = 0; i < 500; i++) {
                seen.add(jwtService.generateRefreshToken());
            }
            assertThat(seen).hasSize(500);
        }

        @Test
        @DisplayName("hashes deterministically to 64 hex characters")
        void hashing() {
            String token = jwtService.generateRefreshToken();
            String hash = jwtService.hashToken(token);

            assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
            assertThat(jwtService.hashToken(token)).isEqualTo(hash);
            assertThat(hash).isNotEqualTo(jwtService.hashToken(
                    jwtService.generateRefreshToken()));
        }
    }

    @Test
    @DisplayName("a secret shorter than 32 bytes fails at construction, not at first use")
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(
                new JwtProperties("c2hvcnQ=", "hardware-erp", 15, 7)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("token lifetimes come from configuration")
    void lifetimesFromConfig() {
        assertThat(jwtService.accessTokenSeconds()).isEqualTo(900);
        assertThat(jwtService.refreshTokenDays()).isEqualTo(7);
    }
}
