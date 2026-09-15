package com.manafy.ops.workforce.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Employee / Field Officer master endpoints (Phase 3 §15). Area↔FO assignment is
 * handled by the Phase-1 AreaController (/areas/{id}/field-officers), NOT here —
 * area_field_officer remains the single source of truth.
 */
@RestController
@RequestMapping("/api/v1")
public class EmployeeController {

    private final EmployeeService service;
    private final AuthenticationContext auth;

    public EmployeeController(EmployeeService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping("/employees")
    public PageResponse<EmployeeResponse> list(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer pageSize) {
        return service.list(auth.currentUserId(), page, pageSize);
    }

    @PostMapping("/employees")
    public ApiResponse<EmployeeResponse> create(@Valid @RequestBody EmployeeCreateRequest req,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(service.map(service.create(auth.currentUserId(), req, idemKey)));
    }

    @GetMapping("/employees/{id}")
    public ApiResponse<EmployeeResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.map(service.get(auth.currentUserId(), id)));
    }

    @PutMapping("/employees/{id}")
    public ApiResponse<EmployeeResponse> update(@PathVariable UUID id, @Valid @RequestBody EmployeeUpdateRequest req) {
        return ApiResponse.ok(service.map(service.update(auth.currentUserId(), id, req)));
    }

    @PostMapping("/employees/{id}/activate")
    public ApiResponse<EmployeeResponse> activate(@PathVariable UUID id) {
        return ApiResponse.ok(service.map(service.changeStatus(auth.currentUserId(), id, "ACTIVE", null)));
    }

    @PostMapping("/employees/{id}/deactivate")
    public ApiResponse<EmployeeResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.ok(service.map(service.changeStatus(auth.currentUserId(), id, "INACTIVE", null)));
    }

    /** List employees of type FIELD_OFFICER. Area assignment lives in /areas/{id}/field-officers. */
    @GetMapping("/field-officers")
    public ApiResponse<List<EmployeeResponse>> listFieldOfficers() {
        return ApiResponse.ok(service.listFieldOfficers(auth.currentUserId()).stream().map(service::map).toList());
    }
}
