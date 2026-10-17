package com.clacjz.medical.rag.exception;

import com.clacjz.medical.rag.constant.ErrorCode;
import lombok.Getter;

@Getter
public class AiServiceException extends RuntimeException {

    private final int code;
    private final String message;
    private final String serviceName;
    private final ErrorCode errorCode;

    public AiServiceException(ErrorCode errorCode, String serviceName) {
        super(String.format("[%s] %s", serviceName, errorCode.getMessage()));
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.serviceName = serviceName;
        this.errorCode = errorCode;
    }

    public AiServiceException(ErrorCode errorCode, String serviceName, String customMessage) {
        super(String.format("[%s] %s: %s", serviceName, errorCode.getMessage(), customMessage));
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.serviceName = serviceName;
        this.errorCode = errorCode;
    }

    public AiServiceException(ErrorCode errorCode, String serviceName, Throwable cause) {
        super(String.format("[%s] %s", serviceName, errorCode.getMessage()), cause);
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.serviceName = serviceName;
        this.errorCode = errorCode;
    }

    public AiServiceException(ErrorCode errorCode, String serviceName, String customMessage, Throwable cause) {
        super(String.format("[%s] %s: %s", serviceName, errorCode.getMessage(), customMessage), cause);
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.serviceName = serviceName;
        this.errorCode = errorCode;
    }

    public AiServiceException(String serviceName, String message) {
        super(String.format("[%s] %s", serviceName, message));
        this.code = ErrorCode.AI_SERVICE_UNAVAILABLE.getCode();
        this.message = message;
        this.serviceName = serviceName;
        this.errorCode = ErrorCode.AI_SERVICE_UNAVAILABLE;
    }

    public AiServiceException(String serviceName, String message, Throwable cause) {
        super(String.format("[%s] %s", serviceName, message), cause);
        this.code = ErrorCode.AI_SERVICE_UNAVAILABLE.getCode();
        this.message = message;
        this.serviceName = serviceName;
        this.errorCode = ErrorCode.AI_SERVICE_UNAVAILABLE;
    }
}
