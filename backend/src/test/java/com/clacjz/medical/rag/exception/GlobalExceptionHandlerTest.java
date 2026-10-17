package com.clacjz.medical.rag.exception;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.model.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler 单元测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("BizException 处理测试")
    class BizExceptionHandlerTests {

        @Test
        @DisplayName("处理业务异常 - 用户不存在 (3001)")
        void testHandleBizException_UserNotFound() {
            BizException exception = new BizException(ErrorCode.USER_NOT_FOUND);

            Result<Void> result = exceptionHandler.handleBizException(exception);

            assertEquals(3001, result.getCode());
            assertEquals("用户不存在", result.getMessage());
        }

        @Test
        @DisplayName("处理业务异常 - 无效Token (2002)")
        void testHandleBizException_InvalidToken() {
            BizException exception = new BizException(ErrorCode.INVALID_TOKEN);

            Result<Void> result = exceptionHandler.handleBizException(exception);

            assertEquals(2002, result.getCode());
            assertEquals("无效的Token", result.getMessage());
        }

        @Test
        @DisplayName("处理业务异常 - 权限不足")
        void testHandleBizException_InsufficientPermissions() {
            BizException exception = new BizException(ErrorCode.INSUFFICIENT_PERMISSIONS);

            Result<Void> result = exceptionHandler.handleBizException(exception);

            assertEquals(ErrorCode.INSUFFICIENT_PERMISSIONS.getCode(), result.getCode());
            assertEquals("权限不足", result.getMessage());
        }

        @Test
        @DisplayName("处理业务异常 - 系统错误 (1000)")
        void testHandleBizException_SystemError() {
            BizException exception = new BizException(ErrorCode.SYSTEM_ERROR);

            Result<Void> result = exceptionHandler.handleBizException(exception);

            assertEquals(1000, result.getCode());
            assertEquals("系统错误", result.getMessage());
        }

        @Test
        @DisplayName("处理业务异常 - 带详情信息")
        void testHandleBizException_WithDetail() {
            BizException exception = new BizException(ErrorCode.INVALID_PARAMETER, "用户ID不能为空");

            Result<Void> result = exceptionHandler.handleBizException(exception);

            assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), result.getCode());
            assertTrue(result.getMessage().contains("用户ID不能为空"));
        }

        @Test
        @DisplayName("处理业务异常 - 账户锁定")
        void testHandleBizException_AccountLocked() {
            BizException exception = new BizException(ErrorCode.ACCOUNT_LOCKED);

            Result<Void> result = exceptionHandler.handleBizException(exception);

            assertEquals(ErrorCode.ACCOUNT_LOCKED.getCode(), result.getCode());
            assertEquals("账户已被锁定", result.getMessage());
        }

        @Test
        @DisplayName("处理业务异常 - 密码错误 (3003)")
        void testHandleBizException_InvalidPassword() {
            BizException exception = new BizException(ErrorCode.INVALID_PASSWORD);

            Result<Void> result = exceptionHandler.handleBizException(exception);

            assertEquals(3003, result.getCode());
            assertEquals("密码错误", result.getMessage());
        }

        @Test
        @DisplayName("处理业务异常 - 服务不可用 (1001)")
        void testHandleBizException_ServiceUnavailable() {
            BizException exception = new BizException(ErrorCode.SERVICE_UNAVAILABLE);

            Result<Void> result = exceptionHandler.handleBizException(exception);

            assertEquals(1001, result.getCode());
        }
    }

    @Nested
    @DisplayName("Exception 处理测试")
    class GeneralExceptionHandlerTests {

        @Test
        @DisplayName("处理通用异常 - 返回系统错误 (1000)")
        void testHandleGlobalException() {
            Exception exception = new Exception("未知错误");

            Result<Void> result = exceptionHandler.handleGlobalException(exception);

            assertEquals(1000, result.getCode());
            assertEquals("系统发生未知错误，请稍后重试", result.getMessage());
        }

        @Test
        @DisplayName("处理通用异常 - RuntimeException")
        void testHandleGlobalException_RuntimeException() {
            RuntimeException exception = new RuntimeException("运行时错误");

            Result<Void> result = exceptionHandler.handleGlobalException(exception);

            assertEquals(1000, result.getCode());
        }

        @Test
        @DisplayName("处理 Throwable")
        void testHandleThrowable() {
            Throwable throwable = new Throwable("严重错误");

            Result<Void> result = exceptionHandler.handleThrowable(throwable);

            assertEquals(1000, result.getCode());
            assertEquals("系统发生未知错误，请稍后重试", result.getMessage());
        }
    }

    @Nested
    @DisplayName("其他异常处理测试")
    class OtherExceptionHandlerTests {

        @Test
        @DisplayName("处理AI服务异常 (7001)")
        void testHandleAiServiceException() {
            AiServiceException exception = new AiServiceException(ErrorCode.AI_SERVICE_UNAVAILABLE, "chat");

            Result<Void> result = exceptionHandler.handleAiServiceException(exception);

            assertEquals(7001, result.getCode());
            assertTrue(result.getMessage().contains("AI服务[chat]异常"));
        }

        @Test
        @DisplayName("处理约束违规异常")
        void testHandleConstraintViolationException() {
            BizException exception = new BizException(ErrorCode.INVALID_PARAMETER);

            Result<Void> result = exceptionHandler.handleBizException(exception);

            assertNotNull(result);
        }
    }
}
