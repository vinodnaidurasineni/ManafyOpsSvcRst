package com.manafy.ops.apartment.controller;

import com.manafy.ops.apartment.dto.ApartmentMapper;
import com.manafy.ops.apartment.dto.ChildResourceDtos.*;
import com.manafy.ops.apartment.service.ApartmentStructureService;
import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Building / unit / facility endpoints (Phase 2 §13, §14). Nested under apartment
 * for creation/listing; direct by id for update/delete. All authorization resolves
 * through the parent apartment (IDOR-safe). Thin controller.
 */
@RestController
@RequestMapping("/api/v1")
public class ApartmentStructureController {

    private final ApartmentStructureService service;
    private final AuthenticationContext auth;

    public ApartmentStructureController(ApartmentStructureService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    // ─── Buildings ───────────────────────────────────────────────────

    @GetMapping("/apartments/{apartmentId}/buildings")
    public ApiResponse<List<BuildingResponse>> listBuildings(@PathVariable UUID apartmentId) {
        return ApiResponse.ok(service.listBuildings(auth.currentUserId(), apartmentId).stream()
                .map(ApartmentMapper::toBuilding).toList());
    }

    @PostMapping("/apartments/{apartmentId}/buildings")
    public ApiResponse<BuildingResponse> createBuilding(@PathVariable UUID apartmentId,
                                                        @Valid @RequestBody BuildingCreateRequest req) {
        return ApiResponse.ok(ApartmentMapper.toBuilding(service.createBuilding(auth.currentUserId(), apartmentId, req)));
    }

    @PutMapping("/buildings/{id}")
    public ApiResponse<BuildingResponse> updateBuilding(@PathVariable UUID id, @Valid @RequestBody BuildingUpdateRequest req) {
        return ApiResponse.ok(ApartmentMapper.toBuilding(service.updateBuilding(auth.currentUserId(), id, req)));
    }

    @DeleteMapping("/buildings/{id}")
    public ApiResponse<Void> deleteBuilding(@PathVariable UUID id) {
        service.deleteBuilding(auth.currentUserId(), id);
        return ApiResponse.ok(null);
    }

    // ─── Units ───────────────────────────────────────────────────────

    @GetMapping("/buildings/{buildingId}/units")
    public ApiResponse<List<UnitResponse>> listUnits(@PathVariable UUID buildingId) {
        return ApiResponse.ok(service.listUnits(auth.currentUserId(), buildingId).stream()
                .map(ApartmentMapper::toUnit).toList());
    }

    @PostMapping("/buildings/{buildingId}/units")
    public ApiResponse<UnitResponse> createUnit(@PathVariable UUID buildingId, @Valid @RequestBody UnitCreateRequest req) {
        return ApiResponse.ok(ApartmentMapper.toUnit(service.createUnit(auth.currentUserId(), buildingId, req)));
    }

    @PutMapping("/units/{id}")
    public ApiResponse<UnitResponse> updateUnit(@PathVariable UUID id, @Valid @RequestBody UnitUpdateRequest req) {
        return ApiResponse.ok(ApartmentMapper.toUnit(service.updateUnit(auth.currentUserId(), id, req)));
    }

    @DeleteMapping("/units/{id}")
    public ApiResponse<Void> deleteUnit(@PathVariable UUID id) {
        service.deleteUnit(auth.currentUserId(), id);
        return ApiResponse.ok(null);
    }

    // ─── Facilities ──────────────────────────────────────────────────

    @GetMapping("/apartments/{apartmentId}/facilities")
    public ApiResponse<List<FacilityResponse>> listFacilities(@PathVariable UUID apartmentId) {
        return ApiResponse.ok(service.listFacilities(auth.currentUserId(), apartmentId).stream()
                .map(ApartmentMapper::toFacility).toList());
    }

    @PostMapping("/apartments/{apartmentId}/facilities")
    public ApiResponse<FacilityResponse> createFacility(@PathVariable UUID apartmentId, @Valid @RequestBody FacilityRequest req) {
        return ApiResponse.ok(ApartmentMapper.toFacility(service.createFacility(auth.currentUserId(), apartmentId, req)));
    }

    @PutMapping("/apartments/{apartmentId}/facilities/{facilityId}")
    public ApiResponse<FacilityResponse> updateFacility(@PathVariable UUID apartmentId, @PathVariable UUID facilityId,
                                                        @Valid @RequestBody FacilityRequest req) {
        return ApiResponse.ok(ApartmentMapper.toFacility(service.updateFacility(auth.currentUserId(), apartmentId, facilityId, req)));
    }

    @DeleteMapping("/apartments/{apartmentId}/facilities/{facilityId}")
    public ApiResponse<Void> deleteFacility(@PathVariable UUID apartmentId, @PathVariable UUID facilityId) {
        service.deleteFacility(auth.currentUserId(), apartmentId, facilityId);
        return ApiResponse.ok(null);
    }
}
