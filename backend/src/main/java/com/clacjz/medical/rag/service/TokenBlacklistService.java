package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    private static final String ACCESS_TOKEN_BLACKLIST_PREFIX = "blacklist:access:";
    private static final String REFRESH_TOKEN_BLACKLIST_PREFIX = "blacklist:refresh:";
    private static final String USER_TOKEN_INDEX_PREFIX = "token:user:";
    private static final String BLACKLIST_STATS_KEY = "blacklist:stats";

    private final AtomicLong totalBlacklistChecks = new AtomicLong(0);
    private final AtomicLong blacklistHits = new AtomicLong(0);
    private final AtomicLong totalCheckTimeNanos = new AtomicLong(0);
    private final AtomicBoolean maintenanceInProgress = new AtomicBoolean(false);
    private final AtomicLong maintenanceRuns = new AtomicLong(0);

    public void blacklistAccessToken(String jti, long expirationMillis) {
        if (jti == null || jti.isEmpty()) {
            return;
        }

        long startTime = System.nanoTime();
        try {
            String key = ACCESS_TOKEN_BLACKLIST_PREFIX + jti;
            long expirationSeconds = TimeUnit.MILLISECONDS.toSeconds(expirationMillis) + 60;

            String value = String.format("{\"blacklistedAt\":%d,\"type\":\"ACCESS\",\"originalExpiration\":%d}",
                    Instant.now().toEpochMilli(), expirationMillis);

            stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(expirationSeconds));

            log.info("Access token blacklisted: jti={}, expiresIn={}s", jti, expirationSeconds);
        } catch (Exception e) {
            log.error("Failed to blacklist access token: jti={}", jti, e);
            throw e; // 向上传播：让调用方知道黑名单写入失败，避免静默接受已撤销的token
        } finally {
            recordOperationTime(startTime);
        }
    }

    public void blacklistRefreshToken(String jti, long expirationMillis) {
        if (jti == null || jti.isEmpty()) {
            return;
        }

        long startTime = System.nanoTime();
        try {
            String key = REFRESH_TOKEN_BLACKLIST_PREFIX + jti;
            long expirationSeconds = TimeUnit.MILLISECONDS.toSeconds(expirationMillis) + 60;

            String value = String.format("{\"blacklistedAt\":%d,\"type\":\"REFRESH\",\"originalExpiration\":%d}",
                    Instant.now().toEpochMilli(), expirationMillis);

            stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(expirationSeconds));

            log.info("Refresh token blacklisted: jti={}, expiresIn={}s", jti, expirationSeconds);
        } catch (Exception e) {
            log.error("Failed to blacklist refresh token: jti={}", jti, e);
            throw e; // 向上传播：让调用方知道黑名单写入失败
        } finally {
            recordOperationTime(startTime);
        }
    }

    public boolean isAccessTokenBlacklisted(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }

        totalBlacklistChecks.incrementAndGet();
        long startTime = System.nanoTime();

        try {
            String key = ACCESS_TOKEN_BLACKLIST_PREFIX + jti;
            Boolean exists = stringRedisTemplate.hasKey(key);
            boolean blacklisted = Boolean.TRUE.equals(exists);

            if (blacklisted) {
                blacklistHits.incrementAndGet();
                log.debug("Access token in blacklist: jti={}", jti);
            }

            return blacklisted;
        } catch (Exception e) {
            log.error("Redis unavailable during blacklist check, treating token as blacklisted for safety: jti={}", jti, e);
            return true;
        } finally {
            recordCheckTime(startTime);
        }
    }

    public boolean isRefreshTokenBlacklisted(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }

        totalBlacklistChecks.incrementAndGet();
        long startTime = System.nanoTime();

        try {
            String key = REFRESH_TOKEN_BLACKLIST_PREFIX + jti;
            Boolean exists = stringRedisTemplate.hasKey(key);
            boolean blacklisted = Boolean.TRUE.equals(exists);

            if (blacklisted) {
                blacklistHits.incrementAndGet();
                log.debug("Refresh token in blacklist: jti={}", jti);
            }

            return blacklisted;
        } catch (Exception e) {
            log.error("Redis unavailable during blacklist check, treating token as blacklisted for safety: jti={}", jti, e);
            return true;
        } finally {
            recordCheckTime(startTime);
        }
    }

    public void removeFromBlacklist(String jti, TokenType tokenType) {
        if (jti == null || jti.isEmpty()) {
            return;
        }

        String prefix = tokenType == TokenType.ACCESS ?
                ACCESS_TOKEN_BLACKLIST_PREFIX : REFRESH_TOKEN_BLACKLIST_PREFIX;
        String key = prefix + jti;

        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("Token removed from blacklist: jti={}, type={}", jti, tokenType);
            }
        } catch (Exception e) {
            log.error("Failed to remove token from blacklist: jti={}, type={}", jti, tokenType, e);
        }
    }

    /**
     * 将token索引添加到用户token集合中，用于后续revokeAllUserTokens批量撤销。
     * 使用Redis pipeline保证SADD+EXPIRE原子性，避免SADD成功但EXPIRE失败导致无TTL的内存泄漏。
     *
     * @param userId   用户ID
     * @param jti      Token的唯一标识
     * @param tokenType token类型（ACCESS或REFRESH）
     * @param expirationMillis token的剩余有效期（毫秒），用于设置集合的TTL
     */
    public void indexTokenForUser(String userId, String jti, TokenType tokenType, long expirationMillis) {
        if (userId == null || userId.isEmpty() || jti == null || jti.isEmpty()) {
            return;
        }
        try {
            String indexKey = USER_TOKEN_INDEX_PREFIX + userId;
            String entry = tokenType.name() + ":" + jti;
            long ttlSeconds = TimeUnit.MILLISECONDS.toSeconds(expirationMillis) + 300;

            // 使用pipeline保证SADD+EXPIRE原子性，避免SADD成功但EXPIRE失败导致无TTL内存泄漏
            stringRedisTemplate.executePipelined((RedisCallback<Void>) connection -> {
                byte[] keyBytes = indexKey.getBytes(StandardCharsets.UTF_8);
                byte[] entryBytes = entry.getBytes(StandardCharsets.UTF_8);
                connection.sAdd(keyBytes, entryBytes);
                connection.expire(keyBytes, ttlSeconds);
                return null;
            });

            log.debug("Token indexed for user: userId={}, jti={}, type={}", userId, jti, tokenType);
        } catch (Exception e) {
            // 索引失败不应阻断登录流程，但需记录警告
            log.warn("Failed to index token for user: userId={}, jti={}, type={}", userId, jti, tokenType, e);
        }
    }

    public void revokeAllUserTokens(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }

        try {
            String indexKey = USER_TOKEN_INDEX_PREFIX + userId;
            Set<String> tokens = stringRedisTemplate.opsForSet().members(indexKey);

            if (tokens != null && !tokens.isEmpty()) {
                for (String token : tokens) {
                    String[] parts = token.split(":", 2);
                    if (parts.length == 2) {
                        String tokenType = parts[0];
                        String jti = parts[1];

                        if ("ACCESS".equals(tokenType)) {
                            // 使用配置的accessTokenExpiration作为TTL上限（无法获取已签发token的实际剩余时间）
                            blacklistAccessToken(jti, jwtProperties.getAccessTokenExpiration());
                        } else if ("REFRESH".equals(tokenType)) {
                            blacklistRefreshToken(jti, jwtProperties.getRefreshTokenExpiration());
                        }
                    }
                }
                stringRedisTemplate.delete(indexKey);
                log.info("All tokens revoked for user: userId={}, count={}", userId, tokens.size());
            }
        } catch (Exception e) {
            log.error("Failed to revoke all user tokens: userId={}", userId, e);
        }
    }

    private void recordOperationTime(long startTime) {
        long elapsed = System.nanoTime() - startTime;
        totalCheckTimeNanos.addAndGet(elapsed);
    }

    private void recordCheckTime(long startTime) {
        long elapsed = System.nanoTime() - startTime;
        totalCheckTimeNanos.addAndGet(elapsed);

        if (elapsed > 50_000_000) {
            log.warn("Slow blacklist check: elapsed={}ms", elapsed / 1_000_000);
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredBlacklistEntries() {
        if (!maintenanceInProgress.compareAndSet(false, true)) {
            log.debug("Blacklist maintenance already in progress, skipping");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            log.info("Starting blacklist cleanup task");

            long accessCount = cleanupBlacklistKeys(ACCESS_TOKEN_BLACKLIST_PREFIX);
            long refreshCount = cleanupBlacklistKeys(REFRESH_TOKEN_BLACKLIST_PREFIX);

            maintenanceRuns.incrementAndGet();
            long duration = System.currentTimeMillis() - startTime;

            log.info("Blacklist cleanup completed: access={}, refresh={}, duration={}ms",
                    accessCount, refreshCount, duration);
        } catch (Exception e) {
            log.error("Error during blacklist cleanup", e);
        } finally {
            maintenanceInProgress.set(false);
        }
    }

    private long cleanupBlacklistKeys(String prefix) {
        long cleaned = 0;
        try {
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(100)
                    .build();
            try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    Long ttl = stringRedisTemplate.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS);
                    // Redis TTL：-2 表示 key 不存在，-1 表示 key 无过期时间（持久化），>0 为剩余毫秒
                    if (ttl != null && ttl == -2) {
                        // key已不存在，无需操作
                        continue;
                    }
                    if (ttl != null && ttl == -1) {
                        // key没有过期时间（持久化key），这是pipeline中EXPIRE命令失败导致的
                        // 为了防止内存泄漏，对这些key设置默认TTL（与token过期时间对齐）
                        long defaultTtlSeconds = Math.max(
                                TimeUnit.MILLISECONDS.toSeconds(jwtProperties.getAccessTokenExpiration()) + 60,
                                TimeUnit.MILLISECONDS.toSeconds(jwtProperties.getRefreshTokenExpiration()) + 60
                        );
                        Boolean expireSet = stringRedisTemplate.expire(key, Duration.ofSeconds(defaultTtlSeconds));
                        if (Boolean.TRUE.equals(expireSet)) {
                            log.warn("Fixed persistent blacklist key by setting TTL: key={}, ttl={}s", key, defaultTtlSeconds);
                            cleaned++;
                        }
                        continue;
                    }
                    // ttl > 0: key有正常的过期时间，不需要清理
                }
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup blacklist keys: prefix={}", prefix, e);
        }
        return cleaned;
    }

    public Map<String, Object> getStatistics() {
        long totalChecks = totalBlacklistChecks.get();
        long hits = blacklistHits.get();
        long totalNanos = totalCheckTimeNanos.get();

        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalBlacklistChecks", totalChecks);
        stats.put("blacklistHits", hits);
        stats.put("hitRate", totalChecks > 0 ? (double) hits / totalChecks : 0.0);
        stats.put("averageCheckTimeMs", totalChecks > 0 ? (double) totalNanos / totalChecks / 1_000_000 : 0.0);
        stats.put("maintenanceRuns", maintenanceRuns.get());
        stats.put("timestamp", Instant.now().toEpochMilli());

        try {
            Map<String, Object> redisStats = getRedisStats();
            stats.putAll(redisStats);
        } catch (Exception e) {
            log.warn("Failed to get Redis statistics", e);
        }

        return stats;
    }

    private Map<String, Object> getRedisStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        var connection = (org.springframework.data.redis.connection.RedisConnection) null;
        try {
            connection = stringRedisTemplate.getConnectionFactory().getConnection();
            String ping = connection.ping();
            stats.put("redisStatus", "PONG".equals(ping) ? "connected" : "disconnected");

            Properties info = connection.serverCommands().info("memory");
            if (info != null) {
                stats.put("usedMemory", info.getProperty("used_memory_human"));
                stats.put("usedMemoryPeak", info.getProperty("used_memory_peak_human"));
            }

            Properties statsInfo = connection.serverCommands().info("stats");
            if (statsInfo != null) {
                stats.put("totalConnections", statsInfo.getProperty("total_connections_received"));
                stats.put("keyspaceHits", statsInfo.getProperty("keyspace_hits"));
                stats.put("keyspaceMisses", statsInfo.getProperty("keyspace_misses"));
            }
        } catch (Exception e) {
            stats.put("redisStatus", "error: " + e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception closeEx) {
                    log.warn("Failed to close Redis connection: {}", closeEx.getMessage());
                }
            }
        }
        return stats;
    }

    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new java.util.HashMap<>();
        long startTime = System.currentTimeMillis();

        var connection = (org.springframework.data.redis.connection.RedisConnection) null;
        try {
            connection = stringRedisTemplate.getConnectionFactory().getConnection();
            String ping = connection.ping();
            boolean healthy = "PONG".equals(ping);

            health.put("status", healthy ? "UP" : "DOWN");
            health.put("responseTimeMs", System.currentTimeMillis() - startTime);
            health.put("redisPing", ping);

            if (healthy) {
                Properties info = connection.serverCommands().info();
                if (info != null) {
                    health.put("connectedClients", info.getProperty("connected_clients"));
                    health.put("usedMemory", info.getProperty("used_memory_human"));
                    health.put("uptimeDays", info.getProperty("uptime_in_days"));
                }
            }
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("responseTimeMs", System.currentTimeMillis() - startTime);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception closeEx) {
                    log.warn("Failed to close Redis connection during health check: {}", closeEx.getMessage());
                }
            }
        }

        return health;
    }

    public void resetStatistics() {
        totalBlacklistChecks.set(0);
        blacklistHits.set(0);
        totalCheckTimeNanos.set(0);
        log.info("Token blacklist statistics reset");
    }

    public enum TokenType {
        ACCESS,
        REFRESH
    }
}
