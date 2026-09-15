package com.manafy.ops.workforce.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.entity.WorkforceSkill;
import com.manafy.ops.workforce.service.HelperService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Helper endpoints (Phase 3 §8). Thin controller. */
@RestController
@RequestMapping("/api/v1/helpers")
public class HelperController {

    private final HelperService service;
    private final AuthenticationContext auth;

    public HelperController(HelperService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    private HelperDetailResponse detail(Helper h) {
        return new HelperDetailResponse(h.getId(), h.getCode(), h.getName(), h.getPhone(), h.getRelationship(),
                h.getTechnicianId(), h.getVendorId(), h.getStatus(), h.getVersion());
    }

    @GetMapping
    public PageResponse<HelperListItemResponse> list(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer pageSize) {
        return service.list(auth.currentUserId(), page, pageSize);
    }

    @PostMapping
    public ApiResponse<HelperDetailResponse> create(@Valid @RequestBody HelperCreateRequest req,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.create(auth.currentUserId(), req, idemKey)));
    }

    @GetMapping("/{id}")
    public ApiResponse<HelperDetailResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(detail(service.get(auth.currentUserId(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<HelperDetailResponse> update(@PathVariable UUID id, @Valid @RequestBody HelperUpdateRequest req) {
        return ApiResponse.ok(detail(service.update(auth.currentUserId(), id, req)));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<HelperDetailResponse> activate(@PathVariable UUID id,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.changeStatus(auth.currentUserId(), id, "HELPER_ACTIVATE", "ACTIVE", null, idemKey)));
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<HelperDetailResponse> suspend(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.changeStatus(auth.currentUserId(), id, "HELPER_SUSPEND", "SUSPENDED",
                req == null ? null : req.reason(), idemKey)));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<HelperDetailResponse> deactivate(@PathVariable UUID id,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.changeStatus(auth.currentUserId(), id, "HELPER_DEACTIVATE", "INACTIVE", null, idemKey)));
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<HelperDetailResponse> terminate(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.changeStatus(auth.currentUserId(), id, "HELPER_TERMINATE", "TERMINATED",
                req == null ? null : req.reason(), idemKey)));
    }

    @GetMapping("/{id}/skills")
    public ApiResponse<List<WorkforceSkillResponse>> listSkills(@PathVariable UUID id) {
        return ApiResponse.ok(service.listSkills(auth.currentUserId(), id).stream()
                .map(s -> new WorkforceSkillResponse(s.getId(), s.getSkillId(), s.getSkillLevel())).toList());
    }

    @PostMapping("/{id}/skills")
    public ApiResponse<WorkforceSkillResponse> addSkill(@PathVariable UUID id, @Valid @RequestBody AssignSkillRequest req) {
        WorkforceSkill s = service.addSkill(auth.currentUserId(), id, req.skillId(), req.skillLevel());
        return ApiResponse.ok(new WorkforceSkillResponse(s.getId(), s.getSkillId(), s.getSkillLevel()));
    }
}
