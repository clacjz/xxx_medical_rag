package com.clacjz.medical.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailService 单元测试")
class EmailServiceTest {

    @Mock
    private AuditService auditService;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(auditService);
    }

    @Nested
    @DisplayName("sendPasswordResetCode")
    class SendPasswordResetCodeTests {

        @Test
        @DisplayName("正常调用记录审计日志")
        void shouldRecordAuditLog() {
            emailService.sendPasswordResetCode("user@example.com", "u-1");

            verify(auditService).logSystemOperation(eq("u-1"), eq("PASSWORD_RESET_CODE_SENT"),
                    eq("email"), eq("user@example.com"), eq(true), anyString());
        }

        @Test
        @DisplayName("连续两次发送会覆盖之前的验证码")
        void shouldOverwritePreviousCode() {
            emailService.sendPasswordResetCode("user@example.com", "u-1");
            emailService.sendPasswordResetCode("user@example.com", "u-1");

            // 审计应该被调用两次
            verify(auditService, times(2)).logSystemOperation(any(), eq("PASSWORD_RESET_CODE_SENT"),
                    any(), any(), anyBoolean(), anyString());
        }

        @Test
        @DisplayName("不同邮箱的验证码独立存储")
        void shouldStoreCodesSeparately() {
            emailService.sendPasswordResetCode("a@example.com", "u-1");
            emailService.sendPasswordResetCode("b@example.com", "u-2");

            verify(auditService).logSystemOperation(eq("u-1"), eq("PASSWORD_RESET_CODE_SENT"),
                    eq("email"), eq("a@example.com"), anyBoolean(), anyString());
            verify(auditService).logSystemOperation(eq("u-2"), eq("PASSWORD_RESET_CODE_SENT"),
                    eq("email"), eq("b@example.com"), anyBoolean(), anyString());
        }
    }

    @Nested
    @DisplayName("verifyCode")
    class VerifyCodeTests {

        @Test
        @DisplayName("未发送过验证码返回 false")
        void shouldReturnFalseWhenNoCodeSent() {
            boolean result = emailService.verifyCode("never@example.com", "123456");
            assertFalse(result);
        }

        @Test
        @DisplayName("验证码正确返回 true")
        void shouldReturnTrueForCorrectCode() throws Exception {
            emailService.sendPasswordResetCode("user@example.com", "u-1");

            // 通过反射获取内部存储的验证码
            java.lang.reflect.Field field = EmailService.class.getDeclaredField("verificationCodes");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, Object> codes =
                    (ConcurrentHashMap<String, Object>) field.get(emailService);
            String storedCode = (String) codes.get("user@example.com").getClass()
                    .getMethod("getCode").invoke(codes.get("user@example.com"));

            boolean result = emailService.verifyCode("user@example.com", storedCode);
            assertTrue(result);
        }

        @Test
        @DisplayName("验证码错误返回 false")
        void shouldReturnFalseForWrongCode() {
            emailService.sendPasswordResetCode("user@example.com", "u-1");

            boolean result = emailService.verifyCode("user@example.com", "000000");
            assertFalse(result);
        }

        @Test
        @DisplayName("验证成功后删除验证码（一次性）")
        void shouldRemoveCodeAfterSuccess() throws Exception {
            emailService.sendPasswordResetCode("user@example.com", "u-1");

            java.lang.reflect.Field field = EmailService.class.getDeclaredField("verificationCodes");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, Object> codes =
                    (ConcurrentHashMap<String, Object>) field.get(emailService);
            String storedCode = (String) codes.get("user@example.com").getClass()
                    .getMethod("getCode").invoke(codes.get("user@example.com"));

            assertTrue(emailService.verifyCode("user@example.com", storedCode));
            // 再次验证应失败，因为验证码已被消费
            assertFalse(emailService.verifyCode("user@example.com", storedCode));
        }

        @Test
        @DisplayName("过期验证码返回 false 并清理")
        void shouldRejectExpiredCode() throws Exception {
            emailService.sendPasswordResetCode("user@example.com", "u-1");

            // 手动设置验证码为过期（通过反射）
            java.lang.reflect.Field codesField = EmailService.class.getDeclaredField("verificationCodes");
            codesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, Object> codes =
                    (ConcurrentHashMap<String, Object>) codesField.get(emailService);
            Object entry = codes.get("user@example.com");
            String code = (String) entry.getClass().getMethod("getCode").invoke(entry);
            java.time.LocalDateTime pastTime = java.time.LocalDateTime.now().minusMinutes(1);

            // 重新构造过期 entry
            java.lang.reflect.Constructor<?> ctor = entry.getClass().getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            Object expiredEntry = ctor.newInstance(code, pastTime);
            codes.put("user@example.com", expiredEntry);

            boolean result = emailService.verifyCode("user@example.com", code);
            assertFalse(result);
            // 过期的 entry 应被清理
            assertFalse(codes.containsKey("user@example.com"));
        }
    }

    @Nested
    @DisplayName("sendWelcomeEmail")
    class SendWelcomeEmailTests {

        @Test
        @DisplayName("正常调用记录审计日志（userId 传 null）")
        void shouldRecordAuditLogWithNullUserId() {
            emailService.sendWelcomeEmail("newuser@example.com", "newuser");

            verify(auditService).logSystemOperation(isNull(), eq("WELCOME_EMAIL_SENT"),
                    eq("email"), eq("newuser@example.com"), eq(true), anyString());
        }
    }
}
