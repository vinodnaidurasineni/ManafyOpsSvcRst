package com.manafy.ops;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API conventions: pagination defaults/max, and the global exception handler's
 * no-leak behavior for unexpected internal errors.
 */
class ApiConventionsTest {

    @Test
    void paginationDefaultsAndMax() {
        assertThat(PageResponse.normalizePage(null)).isEqualTo(1);
        assertThat(PageResponse.normalizePage(0)).isEqualTo(1);
        assertThat(PageResponse.normalizePage(3)).isEqualTo(3);

        assertThat(PageResponse.clampPageSize(null)).isEqualTo(25);   // default
        assertThat(PageResponse.clampPageSize(0)).isEqualTo(25);      // invalid → default
        assertThat(PageResponse.clampPageSize(50)).isEqualTo(50);     // within range
        assertThat(PageResponse.clampPageSize(1000)).isEqualTo(100);  // clamped to max
    }

    @Test
    void internalErrorDoesNotLeakExceptionMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        // An unexpected exception with sensitive text must NOT reach the client.
        Exception boom = new RuntimeException("SQL near 'SECRET_TABLE': syntax error; jdbc:postgres://user:pw@host");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleGeneral(boom);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiResponse<Void> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred.");
        assertThat(body.getMessage()).doesNotContain("SECRET_TABLE", "syntax error", "jdbc");
        // A correlation id is returned so support can trace it server-side.
        assertThat(body.getErrorId()).isNotBlank();
    }

    @Test
    void businessExceptionIsEchoedSafely() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleBusiness(BusinessException.scopeDenied());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getErrorCode()).isEqualTo("SCOPE_DENIED");
    }
}
