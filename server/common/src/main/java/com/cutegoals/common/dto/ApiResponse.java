package com.cutegoals.common.dto;

import com.cutegoals.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified API response following the design spec §5.1.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "ok", data, null);
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("SUCCESS", "ok", data, requestId);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getCode(), null, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, String requestId) {
        return new ApiResponse<>(errorCode.getCode(), message, null, requestId);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, T data, String requestId) {
        return new ApiResponse<>(errorCode.getCode(), message, data, requestId);
    }
}
