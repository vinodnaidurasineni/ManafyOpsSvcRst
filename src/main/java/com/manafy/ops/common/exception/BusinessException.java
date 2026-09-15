package com.manafy.ops.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A deliberately client-safe error: the {@code code} and {@code message} are
 * authored by us and safe to echo to the client. Everything else becomes a
 * generic 500 with a correlation id (see GlobalExceptionHandler).
 */
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }

    // ─── Common factories mapped to the Artifact #3 §9 error-code registry ───

    public static BusinessException unauthenticated() {
        return new BusinessException("UNAUTHENTICATED", "Authentication required", HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException forbidden(String detail) {
        return new BusinessException("FORBIDDEN", detail == null ? "Forbidden" : detail, HttpStatus.FORBIDDEN);
    }

    public static BusinessException scopeDenied() {
        return new BusinessException("SCOPE_DENIED", "Resource is outside your authorized scope", HttpStatus.FORBIDDEN);
    }

    public static BusinessException notFound(String detail) {
        return new BusinessException("NOT_FOUND", detail == null ? "Not found" : detail, HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(String code, String detail) {
        return new BusinessException(code, detail, HttpStatus.CONFLICT);
    }

    public static BusinessException validation(String detail) {
        return new BusinessException("VALIDATION_ERROR", detail, HttpStatus.BAD_REQUEST);
    }
}
