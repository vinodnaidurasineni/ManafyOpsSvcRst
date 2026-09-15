package com.manafy.ops.common.dto;

/**
 * Standard single-object API envelope (Artifact #3 §56).
 *
 * Success: {@code success=true, data=...}. Error: {@code success=false,
 * errorCode + message}. {@code errorId} carries a correlation id for internal
 * (500) errors so support can trace a client-facing failure without leaking
 * internal detail.
 */
public class ApiResponse<T> {
    private boolean success;
    private String errorCode;
    private String message;
    private String errorId;
    private T data;

    private ApiResponse() {}

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.errorCode = code;
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String code, String message, String errorId) {
        ApiResponse<T> r = error(code, message);
        r.errorId = errorId;
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getErrorId() { return errorId; }
    public T getData() { return data; }
}
