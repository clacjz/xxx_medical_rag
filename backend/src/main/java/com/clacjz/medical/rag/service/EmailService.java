package com.clacjz.medical.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件服务：负责密码重置验证码的生成、存储与校验。
 *
 * 当前仅记录日志，尚未接入 SMTP 邮件服务器，实际发送逻辑需在后续迭代补齐。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final int VERIFICATION_CODE_LENGTH = 6;
    private static final long VERIFICATION_CODE_EXPIRE_MINUTES = 30;

    // 验证码存储 (email -> {code, expireTime})
    private final Map<String, VerificationCode> verificationCodes = new ConcurrentHashMap<>();

    private final AuditService auditService;

    /**
     * 发送密码重置验证码
     *
     * @param email 用户邮箱
     * @param userId 用户ID（用于审计日志）
     */
    public void sendPasswordResetCode(String email, String userId) {
        String code = generateVerificationCode();
        verificationCodes.put(email, new VerificationCode(code, LocalDateTime.now().plusMinutes(VERIFICATION_CODE_EXPIRE_MINUTES)));

        // 验证码明文仅写入 debug 日志，避免泄漏
        log.info("[EmailService] Password reset code sent for {} (expire in {} minutes)",
                email, VERIFICATION_CODE_EXPIRE_MINUTES);
        log.debug("[EmailService] Password reset code for {}: {} (expire in {} minutes)",
                email, code, VERIFICATION_CODE_EXPIRE_MINUTES);

        auditService.logSystemOperation(userId, "PASSWORD_RESET_CODE_SENT",
                "email", email, true, "密码重置验证码已发送");
    }

    /**
     * 验证密码重置验证码
     *
     * @param email 用户邮箱
     * @param code 验证码
     * @return 是否验证通过
     */
    public boolean verifyCode(String email, String code) {
        // 使用compute保证原子性：检查和删除在同一个原子操作中完成
        java.util.concurrent.atomic.AtomicBoolean verified = new java.util.concurrent.atomic.AtomicBoolean(false);
        verificationCodes.compute(email, (key, storedCode) -> {
            if (storedCode == null) {
                log.warn("[EmailService] No verification code found for email: {}", email);
                return null; // remove entry
            }

            if (LocalDateTime.now().isAfter(storedCode.getExpireTime())) {
                log.warn("[EmailService] Verification code expired for email: {}", email);
                return null; // remove expired entry
            }

            if (!storedCode.getCode().equals(code)) {
                log.warn("[EmailService] Invalid verification code for email: {}", email);
                return storedCode; // keep entry for retry
            }

            // 验证成功，原子性地删除验证码（一次性使用）
            verified.set(true);
            log.info("[EmailService] Verification code verified successfully for email: {}", email);
            return null; // remove entry
        });
        return verified.get();
    }

    /**
     * 发送欢迎邮件（新用户注册）
     */
    public void sendWelcomeEmail(String email, String username) {
        log.info("[EmailService] Would send welcome email to: {}", email);

        auditService.logSystemOperation(null, "WELCOME_EMAIL_SENT",
                "email", email, true, "欢迎邮件已发送");
    }

    /**
     * 发送密码重置成功通知
     */
    public void sendPasswordChangedNotification(String email, String username) {
        log.info("[EmailService] Would send password changed notification to: {}", email);

        auditService.logSystemOperation(null, "PASSWORD_CHANGED_NOTIFICATION_SENT",
                "email", email, true, "密码变更通知已发送");
    }

    /**
     * 生成6位数字验证码，使用SecureRandom确保密码学安全
     */
    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < VERIFICATION_CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 验证码数据结构
     */
    private static class VerificationCode {
        private final String code;
        private final LocalDateTime expireTime;

        public VerificationCode(String code, LocalDateTime expireTime) {
            this.code = code;
            this.expireTime = expireTime;
        }

        public String getCode() {
            return code;
        }

        public LocalDateTime getExpireTime() {
            return expireTime;
        }
    }
}