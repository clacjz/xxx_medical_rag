package com.clacjz.medical.rag.security;

import com.clacjz.medical.rag.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    private static final List<String> WEAK_KEY_PATTERNS = List.of(
            "secret", "password", "123456", "qwerty", "admin", "welcome",
            "your-256-bit", "development-only", "changeme", "default"
    );

    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            log.error("JWT secret key is too short! Minimum 32 bytes (256 bits) required for HMAC-SHA256.");
            throw new IllegalStateException("JWT secret key must be at least 256 bits (32 bytes)");
        }

        // 检测弱密钥模式：包含常见弱词或重复字符
        String secretLower = jwtProperties.getSecret().toLowerCase();
        boolean hasWeakPattern = WEAK_KEY_PATTERNS.stream().anyMatch(secretLower::contains);
        boolean hasRepeatingChars = secretLower.matches("(.)\\1{5,}"); // 5+ 重复字符
        boolean isSequential = isSequential(secretLower);

        if (hasWeakPattern || hasRepeatingChars || isSequential) {
            log.error("JWT secret key appears weak (detected common pattern or low entropy). "
                    + "Please use a cryptographically random key in production!");
            throw new IllegalStateException(
                    "JWT secret key is too weak. Use a cryptographically random key (e.g., from a key management service).");
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT secret key validated successfully ({} bytes)", keyBytes.length);
    }

    private boolean isSequential(String s) {
        if (s.length() < 4) return false;
        for (int i = 0; i < s.length() - 2; i++) {
            if (s.charAt(i + 1) == s.charAt(i) + 1 && s.charAt(i + 2) == s.charAt(i) + 2) {
                return true;
            }
        }
        return false;
    }

    public String generateAccessToken(String userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("type", "access");
        claims.put("jti", UUID.randomUUID().toString());

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");
        claims.put("jti", UUID.randomUUID().toString());

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());

        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("type", String.class);
    }

    public String getJtiFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("jti", String.class);
    }

    public Date getExpirationFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    public long getExpirationMillisFromToken(String token) {
        return getExpirationFromToken(token).getTime() - System.currentTimeMillis();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            // 让调用方区分过期Token和其他无效Token，不吞掉ExpiredJwtException
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token", e);
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        return "access".equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }

    /**
     * 解析并验证Token，返回Claims对象。
     * 供JwtAuthenticationFilter使用，避免多次调用parseToken导致重复的HMAC-SHA256签名验证。
     *
     * @throws ExpiredJwtException token已过期
     * @throws JwtException token无效
     */
    public Claims parseAndValidateToken(String token) {
        return parseToken(token);
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpiration() {
        return jwtProperties.getAccessTokenExpiration();
    }

    public long getRefreshExpiration() {
        return jwtProperties.getRefreshTokenExpiration();
    }

    public void updateExpiration(long accessExpiration, long refreshExpiration) {
        if (accessExpiration < 60000) {
            throw new IllegalArgumentException("Access token expiration must be at least 60000ms (1 minute)");
        }
        if (refreshExpiration < 60000) {
            throw new IllegalArgumentException("Refresh token expiration must be at least 60000ms (1 minute)");
        }
        if (refreshExpiration <= accessExpiration) {
            throw new IllegalArgumentException("Refresh token expiration must be greater than access token expiration");
        }
        jwtProperties.setAccessTokenExpiration(accessExpiration);
        jwtProperties.setRefreshTokenExpiration(refreshExpiration);
        log.info("Token expiration updated: access={}ms, refresh={}ms", accessExpiration, refreshExpiration);
    }
}
