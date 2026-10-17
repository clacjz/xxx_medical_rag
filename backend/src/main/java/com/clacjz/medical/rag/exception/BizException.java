package com.clacjz.medical.rag.exception;

import com.clacjz.medical.rag.constant.ErrorCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final String message;
    private final Object[] args;
    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.args = null;
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, Object... args) {
        super(errorCode.getFormattedMessage(args));
        this.code = errorCode.getCode();
        this.message = errorCode.getFormattedMessage(args);
        this.args = args;
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
        this.message = customMessage;
        this.args = null;
        this.errorCode = errorCode;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.args = null;
        this.errorCode = null;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
        this.args = null;
        this.errorCode = null;
    }

    public BizException(String message) {
        super(message);
        this.code = ErrorCode.OPERATION_FAILED.getCode();
        this.message = message;
        this.args = null;
        this.errorCode = ErrorCode.OPERATION_FAILED;
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.OPERATION_FAILED.getCode();
        this.message = message;
        this.args = null;
        this.errorCode = ErrorCode.OPERATION_FAILED;
    }
}
