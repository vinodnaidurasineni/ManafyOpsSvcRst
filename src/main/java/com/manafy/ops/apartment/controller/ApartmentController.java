package com.manafy.ops.apartment.controller;

import com.manafy.ops.apartment.dto.ApartmentDtos.*;
import com.manafy.ops.apartment.dto.ApartmentMapper;
import com.manafy.ops.apartment.service.ApartmentAppService;
import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Apartment CRUD + lifecycle + Field Officer assignment (Phase 2 §24). Thin
 * controller: binds input, resolves the acting user, delegates to the application
 * service, maps to DTO. No business logic here.
 */
@RestController
@RequestMapping("/api/v1/apartments")
public class ApartmentController {

    private final ApartmentAppService service;
    private final AuthenticationContext auth;

    public ApartmentController(ApartmentAppService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping
    public PageResponse<ApartmentListItemResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) UUID regionId,
            @RequestParam(required = false) UUID areaId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID fieldOfficerId) {
        return service.list(auth.currentUserId(), page, pageSize, regionId, areaId, status, fieldOfficerId);
    }

    @PostMapping
    public ApiResponse<ApartmentResponse> create(@Valid @RequestBody ApartmentCreateRequest req,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(ApartmentMapper.toApartment(service.create(auth.currentUserId(), req, idemKey)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApartmentResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(ApartmentMapper.toApartment(service.get(auth.currentUserId(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApartmentResponse> update(@PathVariable UUID id, @Valid @RequestBody ApartmentUpdateRequest req) {
        return ApiResponse.ok(ApartmentMapper.toApartment(service.update(auth.currentUserId(), id, req)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.softDelete(auth.currentUserId(), id);
        return ApiResponse.ok(null);
    }

    // ─── Lifecycle ───────────────────────────────────────────────────

    @PostMapping("/{id}/activate")
    public ApiResponse<ApartmentResponse> activate(@PathVariable UUID id,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(ApartmentMapper.toApartment(service.activate(auth.currentUserId(), id, idemKey)));
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<ApartmentResponse> suspend(@PathVariable UUID id, @Valid @RequestBody SuspendRequest req,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(ApartmentMapper.toApartment(service.suspend(auth.currentUserId(), id, req.reason(), idemKey)));
    }

    // ─── Field Officer assignment ────────────────────────────────────

    @PostMapping("/{id}/field-officer")
    public ApiResponse<ApartmentResponse> assignFieldOfficer(
            @PathVariable UUID id, @Valid @RequestBody FieldOfficerAssignmentRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(ApartmentMapper.toApartment(
                service.assignFieldOfficer(auth.currentUserId(), id, req.fieldOfficerId(), idemKey)));
    }

    @DeleteMapping("/{id}/field-officer")
    public ApiResponse<ApartmentResponse> unassignFieldOfficer(@PathVariable UUID id) {
        return ApiResponse.ok(ApartmentMapper.toApartment(service.unassignFieldOfficer(auth.currentUserId(), id)));
    }
}
