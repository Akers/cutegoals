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
    public ResponseEntity<?> handleBusinessException(BusinessException e) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        HttpStatus status = mapHttpStatus(e.getErrorCode());

        // Log without sensitive details
        log.warn("Business exception: code={}, message={}, requestId={}",
                e.getErrorCode(), e.getMessage(), requestId);

        // If exception carries data (e.g. BLIND_BOX_POOL_CHANGED with candidates), include it
        if (e.getData() != null && !e.getData().isEmpty()) {
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(e.getErrorCode(), e.getMessage(), e.getData(), requestId));
        }

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
            case SINGLE_FAMILY_ONLY -> HttpStatus.CONFLICT;
            case INVITATION_NOT_AVAILABLE -> HttpStatus.GONE;
            case PIN_CONFLICT -> HttpStatus.CONFLICT;
            case DEVICE_BINDING_NOT_AVAILABLE -> HttpStatus.GONE;
            case DEVICE_NOT_AUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case CHILD_AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
            case AUDIT_UNAVAILABLE -> HttpStatus.INTERNAL_SERVER_ERROR;
            // Task Template errors
            case TASK_TEMPLATE_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case TASK_TEMPLATE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TASK_TEMPLATE_VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case TASK_TEMPLATE_INVALID_RECURRENCE -> HttpStatus.BAD_REQUEST;
            case TASK_TEMPLATE_REQUIRES_ACTIVE_DIFFICULTY -> HttpStatus.CONFLICT;
            case TASK_TEMPLATE_VERSION_CONFLICT -> HttpStatus.CONFLICT;
            case TASK_TEMPLATE_INACTIVE -> HttpStatus.GONE;
            case TASK_TEMPLATE_INVALID_QUERY -> HttpStatus.BAD_REQUEST;
            // Task Assignment errors
            case TASK_ASSIGNMENT_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case TASK_ASSIGNMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TASK_ASSIGNMENT_TEMPLATE_INACTIVE -> HttpStatus.GONE;
            case TASK_ASSIGNMENT_DIFFICULTY_INACTIVE -> HttpStatus.GONE;
            case TASK_ASSIGNMENT_CHILD_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TASK_ASSIGNMENT_INVALID_DEADLINE -> HttpStatus.BAD_REQUEST;
            case TASK_ASSIGNMENT_IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case TASK_ASSIGNMENT_INVALID_DATE_RANGE -> HttpStatus.BAD_REQUEST;
            case TASK_ASSIGNMENT_RECURRENCE_NOT_CONFIGURED -> HttpStatus.BAD_REQUEST;
            case TASK_ASSIGNMENT_NOT_EDITABLE -> HttpStatus.CONFLICT;
            case TASK_ASSIGNMENT_VERSION_CONFLICT -> HttpStatus.CONFLICT;
            case TASK_ASSIGNMENT_ALREADY_APPROVED -> HttpStatus.CONFLICT;
            case TASK_ASSIGNMENT_CANCELLED -> HttpStatus.CONFLICT;
            case TASK_ASSIGNMENT_INVALID_QUERY -> HttpStatus.BAD_REQUEST;
            // Task Review errors
            case TASK_REVIEW_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case TASK_REVIEW_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TASK_REVIEW_INVALID_STATE -> HttpStatus.CONFLICT;
            case TASK_REVIEW_REASON_REQUIRED -> HttpStatus.BAD_REQUEST;
            case TASK_REVIEW_VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case TASK_REVIEW_ALREADY_DECIDED -> HttpStatus.CONFLICT;
            case TASK_REVIEW_STALE_ATTEMPT -> HttpStatus.CONFLICT;
            case TASK_REVIEW_IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case TASK_REVIEW_INVALID_QUERY -> HttpStatus.BAD_REQUEST;
            case TASK_REVIEW_HISTORY_IMMUTABLE -> HttpStatus.METHOD_NOT_ALLOWED;
            // Task Submission errors
            case TASK_SUBMISSION_VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case TASK_SUBMISSION_LATE_NOT_ALLOWED -> HttpStatus.CONFLICT;
            case TASK_SUBMISSION_IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            // Points errors
            case POINTS_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case POINTS_ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case POINTS_INVALID_TRANSACTION -> HttpStatus.BAD_REQUEST;
            case POINTS_INSUFFICIENT_BALANCE -> HttpStatus.CONFLICT;
            case POINTS_ACCOUNT_CONFLICT -> HttpStatus.CONFLICT;
            case POINTS_ADJUST_REASON_REQUIRED -> HttpStatus.BAD_REQUEST;
            case POINTS_REFERENCE_CONFLICT -> HttpStatus.CONFLICT;
            case POINTS_LEDGER_IMMUTABLE -> HttpStatus.METHOD_NOT_ALLOWED;
            case POINTS_REFUND_SOURCE_INVALID -> HttpStatus.BAD_REQUEST;
            case POINTS_ALREADY_REFUNDED -> HttpStatus.CONFLICT;
            case POINTS_SPEND_SOURCE_INVALID -> HttpStatus.BAD_REQUEST;
            case POINTS_INVALID_QUERY -> HttpStatus.BAD_REQUEST;
            // Prize errors
            case PRIZE_INVALID_POINTS_COST -> HttpStatus.BAD_REQUEST;
            case PRIZE_INVALID_STOCK -> HttpStatus.BAD_REQUEST;
            case PRIZE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PRIZE_OUT_OF_STOCK -> HttpStatus.CONFLICT;
            case PRIZE_DELETED -> HttpStatus.GONE;
            // Blind Box errors
            case BLIND_BOX_INVALID_COST -> HttpStatus.BAD_REQUEST;
            case BLIND_BOX_EMPTY_POOL -> HttpStatus.BAD_REQUEST;
            case BLIND_BOX_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BLIND_BOX_INVALID_WEIGHT -> HttpStatus.BAD_REQUEST;
            case BLIND_BOX_DUPLICATE_PRIZE -> HttpStatus.CONFLICT;
            case BLIND_BOX_POOL_CHANGED -> HttpStatus.CONFLICT;
            case BLIND_BOX_UNAVAILABLE -> HttpStatus.CONFLICT;
            // Exchange errors
            case EXCHANGE_IDEMPOTENCY_KEY_REQUIRED -> HttpStatus.BAD_REQUEST;
            case EXCHANGE_IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case EXCHANGE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case EXCHANGE_INVALID_STATE -> HttpStatus.CONFLICT;
            case EXCHANGE_INVALID_QUERY -> HttpStatus.BAD_REQUEST;
            case EXCHANGE_TRANSACTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            case EXCHANGE_CANCELLATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
