package com.cutegoals.web.handler;

import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Global exception handler for REST API.
 * Consistent error responses across all endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        HttpStatus status = mapHttpStatus(e.getErrorCode());

        // Log without sensitive details
        log.warn("Business exception: code={}, message={}, requestId={}",
                e.getErrorCode(), e.getMessage(), requestId);

        return ResponseEntity.status(status)
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage(), requestId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        log.error("Unhandled exception: requestId={}", requestId, e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "Internal server error", requestId));
    }

    /**
     * Map error codes to HTTP status codes.
     */
    private HttpStatus mapHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case INITIALIZATION_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
            case SMS_LOGIN_NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case REFRESH_TOKEN_INVALID, REFRESH_TOKEN_REUSED, SESSION_REVOKED -> HttpStatus.UNAUTHORIZED;
            case RECOVERY_NOT_AVAILABLE -> HttpStatus.GONE;
            case PASSWORD_POLICY_VIOLATED -> HttpStatus.BAD_REQUEST;
            case PIN_LOCKED -> HttpStatus.LOCKED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
