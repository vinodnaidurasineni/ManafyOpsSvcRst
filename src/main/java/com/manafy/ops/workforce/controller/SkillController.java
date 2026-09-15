package com.manafy.ops.workforce.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.Skill;
import com.manafy.ops.workforce.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Skill master-data endpoints (Phase 3 §11). Thin controller. */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService service;
    private final AuthenticationContext auth;

    public SkillController(SkillService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    private SkillResponse map(Skill s) {
        return new SkillResponse(s.getId(), s.getCode(), s.getName(), s.getCategory(), s.getStatus(), s.getVersion());
    }

    @GetMapping
    public ApiResponse<List<SkillResponse>> list() {
        return ApiResponse.ok(service.list(auth.currentUserId()).stream().map(this::map).toList());
    }

    @PostMapping
    public ApiResponse<SkillResponse> create(@Valid @RequestBody SkillCreateRequest req) {
        return ApiResponse.ok(map(service.create(auth.currentUserId(), req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<SkillResponse> update(@PathVariable UUID id, @Valid @RequestBody SkillUpdateRequest req) {
        return ApiResponse.ok(map(service.update(auth.currentUserId(), id, req)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(auth.currentUserId(), id);
        return ApiResponse.ok(null);
    }
}
