package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.exception.BizException;
import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.LoginRequest;
import com.clacjz.medical.rag.model.dto.LoginResponse;
import com.clacjz.medical.rag.model.dto.PasswordResetVerifyRequest;
import com.clacjz.medical.rag.model.dto.RegisterRequest;
import com.clacjz.medical.rag.model.dto.TokenConfigDTO;
import com.clacjz.medical.rag.model.dto.UserDTO;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.security.JwtTokenProvider;
import com.clacjz.medical.rag.service.AuthService;
import com.clacjz.medical.rag.service.EmailService;
import com.clacjz.medical.rag.service.TokenBlacklistService;
import com.clacjz.medical.rag.util.RsaCryptoUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication API", description = "用户认证相关接口")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RsaCryptoUtil rsaCryptoUtil;
    private final EmailService emailService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;

    // 密码重置验证码尝试次数限制：每邮箱最多5次验证失败
    private static final int VERIFY_MAX_ATTEMPTS = 5;
    private static final long VERIFY_WINDOW_MILLIS = 30 * 60 * 1000L;
    private final java.util.concurrent.ConcurrentHashMap<String, VerifyAttemptTracker> verifyAttemptMap = new java.util.concurrent.ConcurrentHashMap<>();

    @GetMapping("/public-key")
    @Operation(summary = "获取RSA公钥", description = "获取用于密码加密的RSA公钥")
    public ResponseEntity<Result<Map<String, String>>> getPublicKey() {
        Map<String, String> result = new HashMap<>();
        result.put("publicKey", rsaCryptoUtil.getPublicKeyBase64());
        log.debug("Public key requested");
        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录，返回JWT Token。密码支持明文或RSA加密传输")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        String username = request.getUsername();
        String password = decryptPasswordSafely(request.getPassword(), username);

        LoginRequest decryptedRequest = LoginRequest.builder()
                .username(username)
                .password(password)
                .build();

        log.info("Login request received for user: {}", username);
        LoginResponse response = authService.login(decryptedRequest);
        return ResponseEntity.ok(Result.success("登录成功", response));
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户，返回JWT Token。密码支持明文或RSA加密传输")
    public ResponseEntity<Result<LoginResponse>> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.getUsername();
        String password = decryptPasswordSafely(request.getPassword(), username);

        // RSA解密后的密码需重新校验复杂度，因为@Valid只校验了原始密文
        validatePasswordComplexity(password);

        RegisterRequest decryptedRequest = RegisterRequest.builder()
                .username(username)
                .password(password)
                .email(request.getEmail())
                .build();

        log.info("Register request received for user: {}", username);
        LoginResponse response = authService.register(decryptedRequest);
        return ResponseEntity.ok(Result.success("注册成功", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "使用Refresh Token获取新的Access Token")
    public ResponseEntity<Result<LoginResponse>> refresh(
            @Parameter(description = "刷新Token") @RequestHeader("X-Refresh-Token") String refreshToken) {
        log.info("Token refresh request received");
        LoginResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(Result.success("刷新成功", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "清除用户会话，并将Token加入黑名单")
    public ResponseEntity<Result<Void>> logout(
            @Parameter(description = "用户ID") @RequestAttribute String userId,
            @Parameter(description = "访问令牌") @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(description = "刷新令牌") @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        // 从Authorization: Bearer <token>中提取access token
        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7);
        }
        log.info("Logout request received for user: {}", userId);
        authService.logout(userId, accessToken, refreshToken);
        return ResponseEntity.ok(Result.success("登出成功", null));
    }

    @GetMapping("/current")
    @Operation(summary = "获取当前用户", description = "获取当前登录用户的信息")
    public ResponseEntity<Result<UserDTO>> getCurrentUser(
            @Parameter(description = "Authorization Header") @RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.INVALID_TOKEN, "无效的Authorization Header");
        }

        String token = authHeader.substring(7);
        try {
            if (!jwtTokenProvider.validateToken(token)) {
                throw new BizException(ErrorCode.INVALID_TOKEN, "Token无效或已过期");
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED, "Token已过期，请重新登录");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(token);
        UserDTO user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(Result.success(user));
    }
    @GetMapping("/token-config")
    @Operation(summary = "获取Token配置", description = "获取当前Token过期时间配置")
    public ResponseEntity<Result<TokenConfigDTO>> getTokenConfig() {
        TokenConfigDTO config = TokenConfigDTO.of(
                jwtTokenProvider.getExpiration(),
                jwtTokenProvider.getRefreshExpiration()
        );
        return ResponseEntity.ok(Result.success(config));
    }

    @PutMapping("/token-config")
    @Operation(summary = "更新Token配置", description = "更新Token过期时间配置（需要管理员权限）")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<TokenConfigDTO>> updateTokenConfig(
            @Parameter(description = "Access Token过期时间(毫秒)") @RequestParam Long accessTokenExpiration,
            @Parameter(description = "Refresh Token过期时间(毫秒)") @RequestParam Long refreshTokenExpiration) {
        log.info("Updating token config: accessExpiration={}, refreshExpiration={}",
                accessTokenExpiration, refreshTokenExpiration);
        jwtTokenProvider.updateExpiration(accessTokenExpiration, refreshTokenExpiration);
        TokenConfigDTO config = TokenConfigDTO.of(
                jwtTokenProvider.getExpiration(),
                jwtTokenProvider.getRefreshExpiration()
        );
        return ResponseEntity.ok(Result.success("配置更新成功", config));
    }

    @PostMapping("/password/reset/send-code")
    @Operation(summary = "发送密码重置验证码", description = "发送密码重置验证码到用户邮箱")
    public ResponseEntity<Result<Void>> sendPasswordResetCode(
            @Parameter(description = "用户邮箱") @RequestParam String email) {
        log.info("Password reset code requested for email: {}", email);

        User user = userMapper.selectByEmail(email);
        if (user == null) {
            // 为防止邮箱枚举攻击，不返回具体错误
            log.warn("Password reset requested for non-existent email: {}", email);
            return ResponseEntity.ok(Result.success("如果邮箱存在，验证码已发送", null));
        }

        emailService.sendPasswordResetCode(email, user.getId());
        return ResponseEntity.ok(Result.success("如果邮箱存在，验证码已发送", null));
    }

    @PostMapping("/password/reset/verify")
    @Operation(summary = "验证密码重置验证码", description = "验证验证码并设置新密码。密码通过请求体传输，避免URL暴露。")
    public ResponseEntity<Result<Void>> verifyPasswordResetCode(
            @Valid @RequestBody PasswordResetVerifyRequest request) {
        String email = request.getEmail();
        String code = request.getCode();
        String newPassword = request.getNewPassword();

        log.info("Password reset verification for email: {}", email);

        // 检查验证尝试次数限制
        checkVerifyRateLimit(email);

        if (!emailService.verifyCode(email, code)) {
            recordVerifyFailedAttempt(email);
            throw new BizException(ErrorCode.INVALID_PARAMETER, "验证码无效或已过期");
        }

        // 验证成功，清除尝试记录
        clearVerifyAttempts(email);

        User user = userMapper.selectByEmail(email);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证新密码复杂度：复用集中校验逻辑，避免多份校验代码不一致
        validatePasswordComplexity(newPassword);

        // 更新密码：委托给AuthService确保事务边界和token吊销的一致性
        authService.resetPassword(user.getId(), newPassword);

        // 发送密码变更通知
        emailService.sendPasswordChangedNotification(email, user.getUsername());

        log.info("Password reset completed for user: {}", user.getUsername());
        return ResponseEntity.ok(Result.success("密码重置成功，请使用新密码登录", null));
    }

    private void checkVerifyRateLimit(String email) {
        VerifyAttemptTracker tracker = verifyAttemptMap.get(email);
        if (tracker == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (tracker.isExpired(now, VERIFY_WINDOW_MILLIS)) {
            verifyAttemptMap.remove(email);
            return;
        }
        if (tracker.getAttempts() >= VERIFY_MAX_ATTEMPTS) {
            long remainingSeconds = (VERIFY_WINDOW_MILLIS - (now - tracker.getFirstAttemptTime())) / 1000;
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    String.format("验证码验证次数过多，请%d秒后重试", remainingSeconds));
        }
    }

    private void recordVerifyFailedAttempt(String email) {
        verifyAttemptMap.compute(email, (key, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || existing.isExpired(now, VERIFY_WINDOW_MILLIS)) {
                return new VerifyAttemptTracker(now);
            }
            existing.incrementAttempts();
            return existing;
        });
    }

    private void clearVerifyAttempts(String email) {
        verifyAttemptMap.remove(email);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000)
    public void cleanupExpiredVerifyAttempts() {
        long now = System.currentTimeMillis();
        verifyAttemptMap.entrySet().removeIf(entry -> entry.getValue().isExpired(now, VERIFY_WINDOW_MILLIS));
    }

    // 私有辅助方法

    /**
     * 安全解密RSA加密密码。解密失败时抛出异常，不静默降级为明文。
     * 防止RSA密钥变更或密文格式错误时将密文当作明文存储，导致用户永远无法登录。
     */
    private String decryptPasswordSafely(String password, String username) {
        if (!rsaCryptoUtil.isEncrypted(password)) {
            return password;
        }
        try {
            String decrypted = rsaCryptoUtil.decrypt(password);
            log.debug("Password decrypted successfully for user: {}", username);
            return decrypted;
        } catch (Exception e) {
            log.error("RSA decryption failed for user: {}. Rejecting request.", username);
            throw new BizException(ErrorCode.INVALID_PARAMETER, "密码解密失败，请重新获取加密公钥");
        }
    }

    /**
     * 校验密码复杂度，与RegisterRequest的@Size+@Pattern约束保持一致。
     * RSA解密后的密码绕过了@Valid校验，需在此重新验证。
     */
    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 8) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "密码长度不能少于8位");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "密码必须包含大写字母");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "密码必须包含小写字母");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "密码必须包含数字");
        }
    }

    private static class VerifyAttemptTracker {
        private final java.util.concurrent.atomic.AtomicInteger attempts;
        private final long firstAttemptTime;

        VerifyAttemptTracker(long timestamp) {
            this.attempts = new java.util.concurrent.atomic.AtomicInteger(1);
            this.firstAttemptTime = timestamp;
        }

        int getAttempts() { return attempts.get(); }
        long getFirstAttemptTime() { return firstAttemptTime; }
        void incrementAttempts() { attempts.incrementAndGet(); }
        boolean isExpired(long now, long windowMillis) {
            return (now - firstAttemptTime) > windowMillis;
        }
    }
}
