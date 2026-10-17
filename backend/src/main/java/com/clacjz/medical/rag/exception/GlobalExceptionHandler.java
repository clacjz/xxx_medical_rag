package com.clacjz.medical.rag.exception;

import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.model.vo.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBizException(BizException e) {
        log.error("Business exception occurred: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(AiServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleAiServiceException(AiServiceException e) {
        log.error("AI service exception occurred: service={}, code={}, message={}",
                e.getServiceName(), e.getCode(), e.getMessage());
        return Result.error(e.getCode(), String.format("AI服务[%s]异常: %s", e.getServiceName(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.error("Validation exception occurred: errors={}", errors);
        return Result.<Map<String, String>>builder()
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message("参数验证失败")
                .data(errors)
                .build();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String violations = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        log.error("Constraint violation exception occurred: violations={}", violations);
        return Result.error(ErrorCode.INVALID_PARAMETER.getCode(), violations);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleBindException(BindException e) {
        Map<String, String> errors = new HashMap<>();
        e.getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        log.error("Bind exception occurred: errors={}", errors);
        return Result.<Map<String, String>>builder()
                .code(ErrorCode.INVALID_PARAMETER.getCode())
                .message("参数绑定失败")
                .data(errors)
                .build();
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        log.error("Missing request parameter: parameter={}", e.getParameterName());
        return Result.error(ErrorCode.INVALID_PARAMETER.getCode(),
                String.format("缺少必需参数: %s", e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        log.error("Type mismatch for parameter: name={}, type={}",
                e.getName(), e.getRequiredType().getSimpleName());
        return Result.error(ErrorCode.INVALID_PARAMETER.getCode(),
                String.format("参数%s类型错误", e.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("HTTP message not readable: {}", e.getMessage());
        return Result.error(ErrorCode.INVALID_PARAMETER.getCode(), "请求体格式错误或为空");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("File upload size exceeded: {}", e.getMessage());
        return Result.error(ErrorCode.DOCUMENT_SIZE_EXCEEDED);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        log.error("HTTP method not supported: method={}", e.getMethod());
        return Result.error(ErrorCode.INVALID_PARAMETER.getCode(),
                String.format("不支持的请求方法: %s", e.getMethod()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<Void> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e) {
        log.error("HTTP media type not supported: mediaType={}", e.getContentType());
        return Result.error(ErrorCode.INVALID_PARAMETER.getCode(), "不支持的媒体类型");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.error("No handler found: method={}, url={}", e.getHttpMethod(), e.getRequestURL());
        return Result.error(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.error("Access denied: {}", e.getMessage());
        return Result.error(ErrorCode.INSUFFICIENT_PERMISSIONS);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationException(AuthenticationException e) {
        log.error("Authentication failed: {}", e.getMessage());
        return Result.error(ErrorCode.AUTHENTICATION_FAILED);
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentialsException(BadCredentialsException e) {
        log.error("Bad credentials: {}", e.getMessage());
        return Result.error(ErrorCode.AUTHENTICATION_FAILED, "用户名或密码错误");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleGlobalException(Exception e) {
        log.error("Unexpected exception occurred: type={}, message={}", e.getClass().getName(), e.getMessage());
        return Result.error(ErrorCode.SYSTEM_ERROR, "系统发生未知错误，请稍后重试");
    }

    @ExceptionHandler(org.mybatis.spring.MyBatisSystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleMyBatisSystemException(org.mybatis.spring.MyBatisSystemException e) {
        StringBuilder detailBuilder = new StringBuilder();
        detailBuilder.append("数据库操作异常");

        Throwable rootCause = findRootCause(e);
        if (rootCause != null) {
            String rootMessage = rootCause.getMessage() != null ? rootCause.getMessage() : "null";

            if (rootCause instanceof org.apache.ibatis.type.TypeException) {
                detailBuilder.append(" - 类型映射错误");
                log.error("MyBatis TypeException detected - possible enum mapping issue. Cause: {}", rootMessage);
                log.warn("TROUBLESHOOTING: Check if enum types are properly mapped. " +
                        "Ensure MyBatisPlus can convert DB values to Java enums. " +
                        "Common issues: 1) Enum value not found in DB, 2) NULL value for non-nullable enum, " +
                        "3) Type handler mismatch for {}", rootCause.getClass().getSimpleName());
            } else if (rootCause instanceof org.apache.ibatis.binding.BindingException) {
                detailBuilder.append(" - 绑定错误: ").append(rootMessage);
                log.error("MyBatis BindingException: mapper binding error, cause={}", rootMessage);
            } else if (rootCause instanceof org.apache.ibatis.executor.ExecutorException) {
                detailBuilder.append(" - 执行器错误: ").append(rootMessage);
                log.error("MyBatis ExecutorException: execution error, cause={}", rootMessage);
            } else if (rootCause instanceof org.springframework.dao.DataIntegrityViolationException) {
                detailBuilder.append(" - 数据完整性违反: ").append(rootMessage);
                log.error("DataIntegrityViolation: constraint violation, cause={}", rootMessage);
            } else if (rootCause instanceof org.springframework.dao.DataAccessException) {
                detailBuilder.append(" - 数据访问错误: ").append(rootMessage);
                log.error("DataAccessException: database access error, cause={}", rootMessage);
            } else if (rootMessage.contains("null") || "null".equals(rootMessage)) {
                detailBuilder.append(" - 可能是空值导致的数据库操作失败");
                log.error("MyBatisSystemException with null message: type={}, cause={}",
                        rootCause.getClass().getName(), rootMessage);
            } else {
                detailBuilder.append(" - ").append(rootMessage);
                log.error("MyBatisSystemException: root cause type={}, message={}",
                        rootCause.getClass().getName(), rootMessage);
            }
        } else {
            log.error("MyBatisSystemException with no root cause: message={}", e.getMessage(), e);
            detailBuilder.append(": ").append(e.getMessage() != null ? e.getMessage() : "未知错误");
        }

        return Result.error(ErrorCode.SYSTEM_ERROR, detailBuilder.toString());
    }

    private Throwable findRootCause(Throwable throwable) {
        Throwable cause = throwable;
        int maxDepth = 10;
        int depth = 0;
        while (cause.getCause() != null && depth < maxDepth) {
            cause = cause.getCause();
            depth++;
        }
        return cause;
    }

    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleThrowable(Throwable e) {
        log.error("Unexpected throwable occurred: type={}, message={}", e.getClass().getName(), e.getMessage());
        return Result.error(ErrorCode.SYSTEM_ERROR, "系统发生未知错误，请稍后重试");
    }
}
