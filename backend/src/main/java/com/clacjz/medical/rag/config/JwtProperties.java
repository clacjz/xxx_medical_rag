package com.clacjz.medical.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret = "your-256-bit-secret-key-for-development-only";

    private long expiration = 1800000; // 30 minutes

    private long refreshExpiration = 604800000; // 7 days

    private final AtomicLong accessTokenExpirationMs = new AtomicLong(1800000);

    private final AtomicLong refreshTokenExpirationMs = new AtomicLong(604800000);

    @jakarta.annotation.PostConstruct
    public void init() {
        accessTokenExpirationMs.set(expiration);
        refreshTokenExpirationMs.set(refreshExpiration);
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpirationMs.get();
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpirationMs.get();
    }

    public void setAccessTokenExpiration(long expiration) {
        this.expiration = expiration;
        this.accessTokenExpirationMs.set(expiration);
    }

    public void setRefreshTokenExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
        this.refreshTokenExpirationMs.set(refreshExpiration);
    }

    /**
     * 覆盖@Data生成的setExpiration，同步AtomicLong字段。
     * 当Spring通过@ConfigurationProperties绑定jwt.expiration时使用此方法。
     */
    public void setExpiration(long expiration) {
        this.expiration = expiration;
        this.accessTokenExpirationMs.set(expiration);
    }

    /**
     * 覆盖@Data生成的setRefreshExpiration，同步AtomicLong字段。
     * 当Spring通过@ConfigurationProperties绑定jwt.refresh-expiration时使用此方法。
     */
    public void setRefreshExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
        this.refreshTokenExpirationMs.set(refreshExpiration);
    }
}
