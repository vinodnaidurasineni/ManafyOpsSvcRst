package com.manafy.ops.compat.controller;

import com.manafy.ops.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ManafyOps mobile COMPATIBILITY — reference/master-data endpoints.
 *
 * The ported ManafyOps screens call a couple of reference endpoints from the old
 * home-services backend (a service catalog, and an IFSC/bank lookup on the maid
 * profile). The new ops backend has no service-catalog or bank domain, so these
 * return safe, empty/echo responses that let the screens render without errors.
 * They are intentionally minimal — no new persistence — and additive only.
 */
@RestController
@RequestMapping("/api/v1")
public class ReferenceCompatController {

    /** GET /services — old catalog. The ops backend has no service catalog; return empty. */
    @GetMapping("/services")
    public ApiResponse<List<Object>> services() {
        return ApiResponse.ok(List.of());
    }

    /** GET /bank/list — used by the maid profile bank picker. Empty until a bank master exists. */
    @GetMapping("/bank/list")
    public ApiResponse<List<Object>> bankList() {
        return ApiResponse.ok(List.of());
    }

    /**
     * GET /bank/ifsc/{ifsc} — IFSC → bank/branch lookup on the maid profile screen.
     * No bank master is wired in the ops backend; echo the code so the field is
     * populated and the screen continues (the screen guards on a null response).
     */
    @GetMapping("/bank/ifsc/{ifsc}")
    public ApiResponse<Map<String, String>> ifsc(@PathVariable String ifsc) {
        return ApiResponse.ok(Map.of("ifsc", ifsc == null ? "" : ifsc.toUpperCase()));
    }
}
