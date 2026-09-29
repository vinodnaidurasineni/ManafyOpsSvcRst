package com.manafy.ops.dashboard.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.dashboard.dto.DashboardDtos.DashboardSummary;
import com.manafy.ops.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ops dashboard endpoints. Thin controller — the aggregate + authorization live in
 * the service. Gated by REPORT_VIEW.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final AuthenticationContext auth;

    public DashboardController(DashboardService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> summary() {
        return ApiResponse.ok(service.summary(auth.currentUserId()));
    }
}
