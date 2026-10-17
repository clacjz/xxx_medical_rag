package com.clacjz.medical.rag.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.DepartmentMapper;
import com.clacjz.medical.rag.mapper.UserDepartmentMapper;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.LoginRequest;
import com.clacjz.medical.rag.model.dto.LoginResponse;
import com.clacjz.medical.rag.model.dto.RegisterRequest;
import com.clacjz.medical.rag.model.dto.UserDTO;
import com.clacjz.medical.rag.model.entity.Department;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.entity.UserDepartment;
import com.clacjz.medical.rag.model.enums.UserRoleEnum;
import com.clacjz.medical.rag.model.enums.UserStatusEnums;
import com.clacjz.medical.rag.security.JwtTokenProvider;
import com.clacjz.medical.rag.util.RequestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final UserDepartmentMapper userDepartmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditService auditService;
    private final RequestUtils requestUtils;

    private static final int IP_MAX_ATTEMPTS = 5;
    private static final long IP_LOCKOUT_MILLIS = 30 * 60 * 1000;
    private static final long IP_WINDOW_MILLIS = 30 * 60 * 1000;
    private final ConcurrentHashMap<String, IpAttemptTracker> ipAttemptMap = new ConcurrentHashMap<>();

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String ipAddress = requestUtils.getClientIp();
        checkIpRateLimit(ipAddress);

        String userAgent = requestUtils.getUserAgent();
        log.info("User login attempt: {} from IP: {}", request.getUsername(), ipAddress);

        User user = userMapper.selectByUsernameWithRoles(request.getUsername());
        if (user == null) {
            recordIpFailedAttempt(ipAddress);
            auditService.logLogin(null, request.getUsername(), null,
                    ipAddress, userAgent, false, "用户不存在");
            throw new BizException(ErrorCode.AUTHENTICATION_FAILED, "用户名或密码错误");
        }

        if (user.getStatus() == UserStatusEnums.DISABLED) {
            log.warn("Login rejected: user {} is disabled", request.getUsername());
            auditService.logLogin(user.getId(), user.getUsername(), user.getDisplayName(),
                    ipAddress, userAgent, false, "账户已被禁用");
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "账户已被禁用");
        }

        if (user.getStatus() == UserStatusEnums.LOCKED) {
            log.warn("Login rejected: user {} is locked (status=LOCKED)", request.getUsername());
            auditService.logLogin(user.getId(), user.getUsername(), user.getDisplayName(),
                    ipAddress, userAgent, false, "账户已被锁定");
            throw new BizException(ErrorCode.ACCOUNT_LOCKED, "账户已被锁定，请联系管理员");
        }

        if (user.isLocked()) {
            long remainingSeconds = user.getRemainingLockSeconds();
            long remainingMinutes = (remainingSeconds + 59) / 60;
            log.warn("Login rejected: user {} is locked for {} seconds", request.getUsername(), remainingSeconds);
            auditService.logLogin(user.getId(), user.getUsername(), user.getDisplayName(),
                    ipAddress, userAgent, false, "账户已被锁定");
            if (remainingMinutes > 0) {
                throw new BizException(ErrorCode.ACCOUNT_LOCKED,
                        String.format("账户已被锁定，请在%d分钟后重试", remainingMinutes));
            } else {
                // 锁定期已过，使用选择性更新重置
                userMapper.update(null, new UpdateWrapper<User>()
                        .eq("id", user.getId())
                        .set("login_fail_count", 0)
                        .set("lock_until", null));
            }
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordIpFailedAttempt(ipAddress);
            user.incrementLoginFailCount();
            userMapper.updateById(user);

            int remainingAttempts = 5 - user.getLoginFailCount();
            if (user.getLoginFailCount() >= 5) {
                log.warn("User {} account locked due to {} failed login attempts",
                        request.getUsername(), user.getLoginFailCount());
                auditService.logLogin(user.getId(), user.getUsername(), user.getDisplayName(),
                        ipAddress, userAgent, false, "连续5次登录失败，账户已被锁定");
                throw new BizException(ErrorCode.ACCOUNT_LOCKED, "连续5次登录失败，账户已被锁定30分钟");
            } else {
                log.warn("Login failed: invalid password for user {}, {} attempts remaining",
                        request.getUsername(), remainingAttempts);
                auditService.logLogin(user.getId(), user.getUsername(), user.getDisplayName(),
                        ipAddress, userAgent, false, String.format("密码错误，剩余%d次尝试机会", remainingAttempts));
                throw new BizException(ErrorCode.AUTHENTICATION_FAILED,
                        String.format("用户名或密码错误，剩余%d次尝试机会", remainingAttempts));
            }
        }

        clearIpFailedAttempts(ipAddress);
        // 使用选择性更新，避免覆盖并发事务中其他线程对用户状态的修改
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", user.getId())
                .set("login_fail_count", 0)
                .set("lock_until", null));

        // 吊销旧令牌：放在事务提交后执行，避免DB回滚时Redis已写入导致不一致
        registerPostCommitAction(() -> tokenBlacklistService.revokeAllUserTokens(user.getId()));

        // 使用User.getSafeRoleName()统一null-safe处理，避免role为null时NPE
        String roleName = user.getSafeRoleName();
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                roleName
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 索引token到用户token集合：放在事务提交后执行，避免DB回滚时Redis已写入导致不一致
        String accessJti = jwtTokenProvider.getJtiFromToken(accessToken);
        String refreshJti = jwtTokenProvider.getJtiFromToken(refreshToken);
        registerPostCommitAction(() -> {
            tokenBlacklistService.indexTokenForUser(user.getId(), accessJti,
                    TokenBlacklistService.TokenType.ACCESS, jwtTokenProvider.getExpiration());
            tokenBlacklistService.indexTokenForUser(user.getId(), refreshJti,
                    TokenBlacklistService.TokenType.REFRESH, jwtTokenProvider.getRefreshExpiration());
        });

        log.info("User {} logged in successfully from IP: {}", request.getUsername(), ipAddress);
        auditService.logLogin(user.getId(), user.getUsername(), user.getDisplayName(),
                ipAddress, userAgent, true, "登录成功");

        return LoginResponse.fromUser(
                user,
                accessToken,
                refreshToken,
                jwtTokenProvider.getExpiration() / 1000
        );
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String ipAddress = requestUtils.getClientIp();
        String userAgent = requestUtils.getUserAgent();
        log.info("User registration attempt: {} from IP: {}", request.getUsername(), ipAddress);

        if (userMapper.existsByUsername(request.getUsername()) > 0) {
            log.warn("Registration failed: username {} already exists", request.getUsername());
            auditService.logRegister(null, request.getUsername(), null,
                    ipAddress, userAgent, false);
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "用户名已存在");
        }

        if (userMapper.existsByEmail(request.getEmail()) > 0) {
            log.warn("Registration failed: email {} already exists", request.getUsername());
            auditService.logRegister(null, request.getUsername(), null,
                    ipAddress, userAgent, false);
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "邮箱已被注册");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getUsername())
                .role(UserRoleEnum.DEPT_USER)
                .status(UserStatusEnums.ACTIVE)
                .loginFailCount(0)
                .build();

        userMapper.insert(user);

        Department defaultDept = departmentMapper.selectByIsDefaultTrue();
        if (defaultDept != null) {
            UserDepartment userDept = UserDepartment.builder()
                    .userId(user.getId())
                    .departmentId(defaultDept.getId())
                    .isPrimary(true)
                    .build();
            userDepartmentMapper.insert(userDept);
            log.info("User {} added to default department: {}", user.getUsername(), defaultDept.getName());
        }
        auditService.logRegister(user.getId(), user.getUsername(), user.getDisplayName(),
                ipAddress, userAgent, true);

        // 使用User.getSafeRoleName()统一null-safe处理
        String roleName = user.getSafeRoleName();
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                roleName
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 索引token到用户token集合：放在事务提交后执行，避免DB回滚时Redis已写入导致不一致
        String accessJti = jwtTokenProvider.getJtiFromToken(accessToken);
        String refreshJti = jwtTokenProvider.getJtiFromToken(refreshToken);
        registerPostCommitAction(() -> {
            tokenBlacklistService.indexTokenForUser(user.getId(), accessJti,
                    TokenBlacklistService.TokenType.ACCESS, jwtTokenProvider.getExpiration());
            tokenBlacklistService.indexTokenForUser(user.getId(), refreshJti,
                    TokenBlacklistService.TokenType.REFRESH, jwtTokenProvider.getRefreshExpiration());
        });

        return LoginResponse.fromUser(
                user,
                accessToken,
                refreshToken,
                jwtTokenProvider.getExpiration() / 1000
        );
    }

    @Transactional
    public LoginResponse refreshToken(String refreshToken) {
        try {
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                throw new BizException(ErrorCode.INVALID_TOKEN, "刷新Token无效或已过期");
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED, "刷新Token已过期，请重新登录");
        }

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BizException(ErrorCode.INVALID_TOKEN, "无效的刷新Token");
        }

        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
        if (tokenBlacklistService.isRefreshTokenBlacklisted(jti)) {
            log.warn("Reused refresh token detected: jti={}", jti);
            throw new BizException(ErrorCode.INVALID_TOKEN, "Token已被使用或失效，请重新登录");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userMapper.selectByIdWithRoles(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getStatus() == UserStatusEnums.LOCKED) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED, "账户已被锁定，请联系管理员");
        }

        if (user.getStatus() == UserStatusEnums.DISABLED) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "账户已被禁用");
        }

        if (user.isLocked()) {
            long remainingSeconds = user.getRemainingLockSeconds();
            long remainingMinutes = (remainingSeconds + 59) / 60;
            log.warn("Token refresh rejected: user {} is locked", user.getUsername());
            if (remainingMinutes > 0) {
                throw new BizException(ErrorCode.ACCOUNT_LOCKED,
                        String.format("账户已被锁定，请在%d分钟后重试", remainingMinutes));
            } else {
                // 锁定期已过，使用选择性更新重置
                userMapper.update(null, new UpdateWrapper<User>()
                        .eq("id", user.getId())
                        .set("login_fail_count", 0)
                        .set("lock_until", null));
            }
        }

        long expirationMillis = jwtTokenProvider.getExpirationMillisFromToken(refreshToken);
        // 吊销旧refreshToken：放在事务提交后执行，避免DB回滚时Redis已写入导致不一致
        registerPostCommitAction(() -> tokenBlacklistService.blacklistRefreshToken(jti, expirationMillis));

        // 使用User.getSafeRoleName()统一null-safe处理
        String roleName = user.getSafeRoleName();
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                roleName
        );
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 索引新token到用户token集合：放在事务提交后执行，避免DB回滚时Redis已写入导致不一致
        String newAccessJti = jwtTokenProvider.getJtiFromToken(newAccessToken);
        String newRefreshJti = jwtTokenProvider.getJtiFromToken(newRefreshToken);
        registerPostCommitAction(() -> {
            tokenBlacklistService.indexTokenForUser(user.getId(), newAccessJti,
                    TokenBlacklistService.TokenType.ACCESS, jwtTokenProvider.getExpiration());
            tokenBlacklistService.indexTokenForUser(user.getId(), newRefreshJti,
                    TokenBlacklistService.TokenType.REFRESH, jwtTokenProvider.getRefreshExpiration());
        });

        log.info("Token refreshed for user {}", user.getUsername());

        return LoginResponse.fromUser(
                user,
                newAccessToken,
                newRefreshToken,
                jwtTokenProvider.getExpiration() / 1000
        );
    }

    @Transactional(readOnly = true)
    public UserDTO getCurrentUser(String userId) {
        User user = userMapper.selectByIdWithRoles(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        return UserDTO.fromUser(user);
    }

    @Transactional
    public void logout(String userId, String accessToken, String refreshToken) {
        String ipAddress = requestUtils.getClientIp();
        String userAgent = requestUtils.getUserAgent();

        User user = userMapper.selectById(userId);
        String username = user != null ? user.getUsername() : userId;
        String displayName = user != null ? user.getDisplayName() : null;

        // 处理 accessToken：即使过期也尝试拉黑，确保所有已知token失效
        if (accessToken != null) {
            try {
                if (jwtTokenProvider.validateToken(accessToken)) {
                    if (!jwtTokenProvider.isAccessToken(accessToken)) {
                        // 非access类型的token也拉黑，防止token复用
                        log.warn("Logout received non-access token in access token position, blacklisting anyway: userId={}", userId);
                    }
                    String accessJti = jwtTokenProvider.getJtiFromToken(accessToken);
                    long accessExpiration = jwtTokenProvider.getExpirationMillisFromToken(accessToken);
                    registerPostCommitAction(() -> tokenBlacklistService.blacklistAccessToken(accessJti, accessExpiration));
                    log.debug("Access token scheduled for blacklisting during logout: userId={}, jti={}", userId, accessJti);
                }
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                // token已过期但仍然拉黑jti，防止在自然过期前被复用
                // 注意：jti存储为自定义claim，不在标准JWT ID字段中，需要从claims map中读取
                String accessJti = null;
                if (e.getClaims() != null) {
                    accessJti = e.getClaims().get("jti", String.class);
                    if (accessJti == null) {
                        accessJti = e.getClaims().getId();
                    }
                }
                if (accessJti != null) {
                    long accessExpiration = e.getClaims() != null && e.getClaims().getExpiration() != null
                            ? e.getClaims().getExpiration().getTime() - System.currentTimeMillis() : 0;
                    // accessExpiration可能为负值（已过期），此时使用0让黑名单立即过期
                    long effectiveExpiration = Math.max(0, accessExpiration);
                    final String finalJti = accessJti;
                    registerPostCommitAction(() -> tokenBlacklistService.blacklistAccessToken(finalJti, effectiveExpiration));
                    log.debug("Expired access token scheduled for blacklisting during logout: userId={}, jti={}", userId, finalJti);
                }
            }
        }

        // 处理 refreshToken：即使accessToken异常也要继续处理refreshToken
        if (refreshToken != null) {
            try {
                if (jwtTokenProvider.validateToken(refreshToken)) {
                    if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
                        log.warn("Logout received non-refresh token in refresh token position, blacklisting anyway: userId={}", userId);
                    }
                    String refreshJti = jwtTokenProvider.getJtiFromToken(refreshToken);
                    long refreshExpiration = jwtTokenProvider.getExpirationMillisFromToken(refreshToken);
                    registerPostCommitAction(() -> tokenBlacklistService.blacklistRefreshToken(refreshJti, refreshExpiration));
                    log.debug("Refresh token scheduled for blacklisting during logout: userId={}, jti={}", userId, refreshJti);
                }
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                // 注意：jti存储为自定义claim，不在标准JWT ID字段中，需要从claims map中读取
                String refreshJti = null;
                if (e.getClaims() != null) {
                    refreshJti = e.getClaims().get("jti", String.class);
                    if (refreshJti == null) {
                        refreshJti = e.getClaims().getId();
                    }
                }
                if (refreshJti != null) {
                    long refreshExpiration = e.getClaims() != null && e.getClaims().getExpiration() != null
                            ? e.getClaims().getExpiration().getTime() - System.currentTimeMillis() : 0;
                    long effectiveExpiration = Math.max(0, refreshExpiration);
                    final String finalJti = refreshJti;
                    registerPostCommitAction(() -> tokenBlacklistService.blacklistRefreshToken(finalJti, effectiveExpiration));
                    log.debug("Expired refresh token scheduled for blacklisting during logout: userId={}, jti={}", userId, finalJti);
                }
            }
        }

        log.info("User {} logged out from IP: {}", username, ipAddress);
        auditService.logLogout(userId, username, displayName, ipAddress, userAgent);
    }

    /**
     * 密码重置：更新密码并吊销所有已签发的token。
     * 使用registerPostCommitAction确保Redis写入在DB事务成功提交后执行，
     * 避免DB回滚时Redis已写入导致数据不一致。
     */
    @Transactional
    public void resetPassword(String userId, String newPassword) {
        // 密码复杂度校验：与注册/修改密码接口保持一致
        validatePasswordComplexity(newPassword);

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        // 密码重置后吊销所有token：放在事务提交后执行，避免DB回滚时Redis已写入导致不一致
        registerPostCommitAction(() -> tokenBlacklistService.revokeAllUserTokens(userId));

        log.info("Password reset completed for user: {}", user.getUsername());
    }

    private static final String PASSWORD_COMPLEXITY_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$";
    private static final int PASSWORD_MIN_LENGTH = 8;

    /**
     * 密码复杂度校验，与RegisterRequest的@Size+@Pattern约束保持一致：
     * 至少8位，必须包含大小写字母和数字。
     */
    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "密码长度不能少于" + PASSWORD_MIN_LENGTH + "位");
        }
        if (!password.matches(PASSWORD_COMPLEXITY_REGEX)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "密码必须包含大写字母、小写字母和数字");
        }
    }

    private void checkIpRateLimit(String ipAddress) {
        IpAttemptTracker tracker = ipAttemptMap.get(ipAddress);
        long now = System.currentTimeMillis();

        if (tracker == null) {
            return;
        }

        if (tracker.isLockedOut(now)) {
            long remainingSeconds = tracker.getRemainingLockoutSeconds(now);
            log.warn("IP {} is rate limited, {} seconds remaining", ipAddress, remainingSeconds);
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    String.format("请求过于频繁，请%d秒后重试", remainingSeconds));
        }

        if (tracker.isExpired(now, IP_WINDOW_MILLIS)) {
            ipAttemptMap.remove(ipAddress);
        }
    }

    private void recordIpFailedAttempt(String ipAddress) {
        IpAttemptTracker tracker = ipAttemptMap.compute(ipAddress, (key, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || existing.isExpired(now, IP_WINDOW_MILLIS)) {
                return new IpAttemptTracker(now);
            }
            existing.incrementAttempts();
            return existing;
        });

        if (tracker != null && tracker.getAttempts().get() >= IP_MAX_ATTEMPTS) {
            log.error("IP {} locked out due to {} failed login attempts within {} minutes",
                    ipAddress, tracker.getAttempts().get(), IP_WINDOW_MILLIS / 60000);
        }
    }

    private void clearIpFailedAttempts(String ipAddress) {
        ipAttemptMap.remove(ipAddress);
    }

    private static class IpAttemptTracker {
        private final AtomicInteger attempts;
        private final AtomicLong firstAttemptTime;
        private final AtomicLong lockoutStartTime;

        IpAttemptTracker(long timestamp) {
            this.attempts = new AtomicInteger(1);
            this.firstAttemptTime = new AtomicLong(timestamp);
            this.lockoutStartTime = new AtomicLong(0);
        }

        AtomicInteger getAttempts() {
            return attempts;
        }

        void incrementAttempts() {
            attempts.incrementAndGet();
        }

        boolean isLockedOut(long now) {
            if (attempts.get() >= IP_MAX_ATTEMPTS) {
                lockoutStartTime.compareAndSet(0, now);
                long startTime = lockoutStartTime.get();
                return (now - startTime) < IP_LOCKOUT_MILLIS;
            }
            return false;
        }

        boolean isExpired(long now, long windowMillis) {
            return (now - firstAttemptTime.get()) > windowMillis;
        }

        long getRemainingLockoutSeconds(long now) {
            long startTime = lockoutStartTime.get();
            if (startTime == 0) {
                return 0;
            }
            long elapsed = now - startTime;
            return Math.max(0, IP_LOCKOUT_MILLIS - elapsed) / 1000;
        }
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredIpAttempts() {
        long now = System.currentTimeMillis();
        ipAttemptMap.entrySet().removeIf(entry -> entry.getValue().isExpired(now, IP_WINDOW_MILLIS));
    }

    /**
     * 注册事务提交后执行的动作，确保Redis写入在DB事务成功提交后进行，
     * 避免DB回滚时Redis已写入导致数据不一致。
     */
    private void registerPostCommitAction(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.warn("Post-commit action failed", e);
                    }
                }
            });
        } else {
            // 无事务上下文时直接执行
            action.run();
        }
    }
}