package com.clacjz.medical.rag.exception;

import com.clacjz.medical.rag.constant.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BizException 单元测试")
class BizExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("创建 BizException - 仅错误码")
        void testCreateWithErrorCode() {
            BizException exception = new BizException(ErrorCode.USER_NOT_FOUND);

            assertNotNull(exception);
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
            assertEquals("用户不存在", exception.getMessage());
            assertEquals(3001, exception.getCode());
        }

        @Test
        @DisplayName("创建 BizException - 错误码 + 详情")
        void testCreateWithErrorCodeAndDetail() {
            BizException exception = new BizException(ErrorCode.INVALID_PARAMETER, "参数name不能为空");

            assertNotNull(exception);
            assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
            assertEquals("参数name不能为空", exception.getMessage());
            assertEquals(1003, exception.getCode());
        }

        @Test
        @DisplayName("创建 BizException - 服务不可用")
        void testCreateServiceUnavailable() {
            BizException exception = new BizException(ErrorCode.SERVICE_UNAVAILABLE);

            assertNotNull(exception);
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
            assertEquals("服务不可用", exception.getMessage());
            assertEquals(1001, exception.getCode());
        }
    }

    @Nested
    @DisplayName("方法测试")
    class MethodTests {

        @Test
        @DisplayName("getErrorCode 测试")
        void testGetErrorCode() {
            BizException exception = new BizException(ErrorCode.INVALID_TOKEN);

            assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
        }

        @Test
        @DisplayName("getMessage 测试 - 仅错误码")
        void testGetMessage_CodeOnly() {
            BizException exception = new BizException(ErrorCode.INVALID_TOKEN);

            assertEquals("无效的Token", exception.getMessage());
        }

        @Test
        @DisplayName("getMessage 测试 - 错误码 + 详情")
        void testGetMessage_CodeAndDetail() {
            BizException exception = new BizException(ErrorCode.INVALID_TOKEN, "Token已过期");

            assertTrue(exception.getMessage().contains("Token已过期"));
        }
    }

    @Nested
    @DisplayName("各类错误码测试")
    class ErrorCodeTests {

        @Test
        @DisplayName("认证相关错误码")
        void testAuthenticationErrorCodes() {
            assertEquals("认证失败", new BizException(ErrorCode.AUTHENTICATION_FAILED).getMessage());
            assertEquals("无效的Token", new BizException(ErrorCode.INVALID_TOKEN).getMessage());
            assertEquals("Token已过期", new BizException(ErrorCode.TOKEN_EXPIRED).getMessage());
            assertEquals("Token缺失", new BizException(ErrorCode.TOKEN_MISSING).getMessage());
            assertEquals("权限不足", new BizException(ErrorCode.INSUFFICIENT_PERMISSIONS).getMessage());
        }

        @Test
        @DisplayName("账户相关错误码")
        void testAccountErrorCodes() {
            assertEquals("账户已被锁定", new BizException(ErrorCode.ACCOUNT_LOCKED).getMessage());
            assertEquals("账户已被禁用", new BizException(ErrorCode.ACCOUNT_DISABLED).getMessage());
        }

        @Test
        @DisplayName("用户相关错误码")
        void testUserErrorCodes() {
            assertEquals("用户不存在", new BizException(ErrorCode.USER_NOT_FOUND).getMessage());
            assertEquals("用户已存在", new BizException(ErrorCode.USER_ALREADY_EXISTS).getMessage());
            assertEquals("密码错误", new BizException(ErrorCode.INVALID_PASSWORD).getMessage());
        }

        @Test
        @DisplayName("文档相关错误码")
        void testDocumentErrorCodes() {
            assertEquals("文档不存在", new BizException(ErrorCode.DOCUMENT_NOT_FOUND).getMessage());
            assertEquals("文档上传失败", new BizException(ErrorCode.DOCUMENT_UPLOAD_FAILED).getMessage());
        }

        @Test
        @DisplayName("AI服务相关错误码")
        void testAIServiceErrorCodes() {
            assertEquals("AI服务不可用", new BizException(ErrorCode.AI_SERVICE_UNAVAILABLE).getMessage());
            assertEquals("AI服务响应超时", new BizException(ErrorCode.AI_SERVICE_TIMEOUT).getMessage());
        }

        @Test
        @DisplayName("系统错误码")
        void testSystemErrorCodes() {
            assertEquals("系统错误", new BizException(ErrorCode.SYSTEM_ERROR).getMessage());
            assertEquals("服务不可用", new BizException(ErrorCode.SERVICE_UNAVAILABLE).getMessage());
            assertEquals("参数错误", new BizException(ErrorCode.INVALID_PARAMETER).getMessage());
        }
    }

    @Nested
    @DisplayName("边界测试")
    class BoundaryTests {

        @Test
        @DisplayName("超长详情信息")
        void testLongDetailMessage() {
            String longDetail = "a".repeat(1000);
            BizException exception = new BizException(ErrorCode.INVALID_PARAMETER, longDetail);

            assertEquals(1000, exception.getMessage().length());
        }

        @Test
        @DisplayName("特殊字符详情信息")
        void testSpecialCharacterDetail() {
            String specialDetail = "错误: <script>alert('xss')</script>";
            BizException exception = new BizException(ErrorCode.INVALID_PARAMETER, specialDetail);

            assertEquals(specialDetail, exception.getMessage());
        }

        @Test
        @DisplayName("中文详情信息")
        void testChineseDetail() {
            String chineseDetail = "用户名为空，不符合业务规则";
            BizException exception = new BizException(ErrorCode.INVALID_PARAMETER, chineseDetail);

            assertEquals(chineseDetail, exception.getMessage());
        }
    }
}
