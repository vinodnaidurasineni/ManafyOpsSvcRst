package com.manafy.ops.maidapp.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.maidapp.service.MaidAppService;
import com.manafy.ops.maidapp.service.MaidIdentityResolver;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Maid-app service start/complete for an assignment (ported from ManafySvcRst
 * MaidAssignmentController). Namespaced under /maids/me to avoid clashing with
 * the ops dispatch AssignmentController's own /assignments/{id}/start|complete
 * (which is an Ops-staff action, not a maid self-action).
 */
@RestController
@RequestMapping("/api/v1/maids/me/assignments")
public class MaidAssignmentController {

    private final MaidAppService maidAppService;
    private final MaidIdentityResolver maidIdentity;

    public MaidAssignmentController(MaidAppService maidAppService, MaidIdentityResolver maidIdentity) {
        this.maidAppService = maidAppService;
        this.maidIdentity = maidIdentity;
    }

    @PostMapping("/{assignmentId}/start")
    public ApiResponse<Map<String, String>> startService(@PathVariable UUID assignmentId) {
        maidAppService.startService(maidIdentity.currentMaidId(), assignmentId);
        return ApiResponse.ok(Map.of("status", "IN_PROGRESS"));
    }

    @PostMapping("/{assignmentId}/complete")
    public ApiResponse<Map<String, String>> completeService(@PathVariable UUID assignmentId) {
        maidAppService.completeService(maidIdentity.currentMaidId(), assignmentId);
        return ApiResponse.ok(Map.of("status", "COMPLETED"));
    }
}
