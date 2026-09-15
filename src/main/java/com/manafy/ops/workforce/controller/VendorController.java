package com.manafy.ops.workforce.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.Vendor;
import com.manafy.ops.workforce.entity.VendorStaff;
import com.manafy.ops.workforce.service.VendorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Vendor + vendor-staff endpoints (Phase 3 §9, §10). Thin controller. */
@RestController
@RequestMapping("/api/v1")
public class VendorController {

    private final VendorService service;
    private final AuthenticationContext auth;

    public VendorController(VendorService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    private VendorDetailResponse detail(Vendor v) {
        return new VendorDetailResponse(v.getId(), v.getCode(), v.getLegalName(), v.getDisplayName(),
                v.getRegistrationNo(), v.getEmail(), v.getPhone(), v.getAddress(), v.getStatus(), v.getVersion());
    }
    private VendorStaffResponse staff(VendorStaff s) {
        return new VendorStaffResponse(s.getId(), s.getVendorId(), s.getTechnicianId(), s.getHelperId(),
                s.getStaffName(), s.getRoleTitle(), s.getStatus(), s.getVersion());
    }

    @GetMapping("/vendors")
    public PageResponse<VendorListItemResponse> list(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer pageSize) {
        return service.list(auth.currentUserId(), page, pageSize);
    }

    @PostMapping("/vendors")
    public ApiResponse<VendorDetailResponse> create(@Valid @RequestBody VendorCreateRequest req,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.create(auth.currentUserId(), req, idemKey)));
    }

    @GetMapping("/vendors/{id}")
    public ApiResponse<VendorDetailResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(detail(service.get(auth.currentUserId(), id)));
    }

    @PutMapping("/vendors/{id}")
    public ApiResponse<VendorDetailResponse> update(@PathVariable UUID id, @Valid @RequestBody VendorUpdateRequest req) {
        return ApiResponse.ok(detail(service.update(auth.currentUserId(), id, req)));
    }

    @PostMapping("/vendors/{id}/activate")
    public ApiResponse<VendorDetailResponse> activate(@PathVariable UUID id,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.changeStatus(auth.currentUserId(), id, "VENDOR_ACTIVATE", "ACTIVE", null, idemKey)));
    }

    @PostMapping("/vendors/{id}/suspend")
    public ApiResponse<VendorDetailResponse> suspend(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        String reason = req == null ? null : req.reason();
        return ApiResponse.ok(detail(service.changeStatus(auth.currentUserId(), id, "VENDOR_SUSPEND", "SUSPENDED", reason, idemKey)));
    }

    @PostMapping("/vendors/{id}/deactivate")
    public ApiResponse<VendorDetailResponse> deactivate(@PathVariable UUID id,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.changeStatus(auth.currentUserId(), id, "VENDOR_DEACTIVATE", "INACTIVE", null, idemKey)));
    }

    @PostMapping("/vendors/{id}/terminate")
    public ApiResponse<VendorDetailResponse> terminate(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        String reason = req == null ? null : req.reason();
        return ApiResponse.ok(detail(service.changeStatus(auth.currentUserId(), id, "VENDOR_TERMINATE", "TERMINATED", reason, idemKey)));
    }

    // ─── Vendor staff ────────────────────────────────────────────────

    @GetMapping("/vendors/{vendorId}/staff")
    public ApiResponse<List<VendorStaffResponse>> listStaff(@PathVariable UUID vendorId) {
        return ApiResponse.ok(service.listStaff(auth.currentUserId(), vendorId).stream().map(this::staff).toList());
    }

    @PostMapping("/vendors/{vendorId}/staff")
    public ApiResponse<VendorStaffResponse> addStaff(@PathVariable UUID vendorId, @Valid @RequestBody VendorStaffRequest req) {
        return ApiResponse.ok(staff(service.addStaff(auth.currentUserId(), vendorId, req)));
    }

    @GetMapping("/vendor-staff/{id}")
    public ApiResponse<VendorStaffResponse> getStaff(@PathVariable UUID id) {
        return ApiResponse.ok(staff(service.getStaff(auth.currentUserId(), id)));
    }

    @PutMapping("/vendor-staff/{id}")
    public ApiResponse<VendorStaffResponse> updateStaff(@PathVariable UUID id, @Valid @RequestBody VendorStaffRequest req) {
        return ApiResponse.ok(staff(service.updateStaff(auth.currentUserId(), id, req)));
    }
}
