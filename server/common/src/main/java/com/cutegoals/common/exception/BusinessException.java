package com.cutegoals.common.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Business-level exception carrying a stable error code.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String message;
    private final Map<String, Object> data;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
        this.message = errorCode.getCode();
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> data) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.message = message;
        this.data = null;
    }
}
