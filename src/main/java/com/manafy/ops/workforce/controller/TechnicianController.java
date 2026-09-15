package com.manafy.ops.workforce.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.*;
import com.manafy.ops.workforce.service.TechnicianService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Technician endpoints (Phase 3 §6, §13, §14, §29). Thin controller. */
@RestController
@RequestMapping("/api/v1/technicians")
public class TechnicianController {

    private final TechnicianService service;
    private final AuthenticationContext auth;

    public TechnicianController(TechnicianService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    private TechnicianDetailResponse detail(Technician t, boolean canViewPii) {
        String phone = canViewPii ? t.getPhone() : mask(t.getPhone());
        String email = canViewPii ? t.getEmail() : maskEmail(t.getEmail());
        String dob = canViewPii ? (t.getDateOfBirth() == null ? null : t.getDateOfBirth().toString()) : null;
        return new TechnicianDetailResponse(t.getId(), t.getCode(), t.getName(), t.getVendorId(),
                phone, email, dob, t.getRegionId(), t.getServiceRadiusKm(),
                t.getMaxConcurrentJobs(), t.getMaxDailyJobs(), t.getStatus(), t.getAvailabilityStatus(),
                !canViewPii, t.getVersion());
    }
    private static String mask(String v) {
        if (v == null || v.isBlank()) return v;
        int keep = Math.min(2, v.length());
        return "*".repeat(Math.max(0, v.length() - keep)) + v.substring(v.length() - keep);
    }
    private static String maskEmail(String v) {
        if (v == null || !v.contains("@")) return v == null ? null : "***";
        String[] p = v.split("@", 2);
        return (p[0].isEmpty() ? "" : p[0].charAt(0) + "***") + "@" + p[1];
    }

    @GetMapping
    public PageResponse<TechnicianListItemResponse> list(@RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer pageSize,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) UUID vendorId) {
        return service.list(auth.currentUserId(), page, pageSize, status, vendorId);
    }

    @PostMapping
    public ApiResponse<TechnicianDetailResponse> create(@Valid @RequestBody TechnicianCreateRequest req,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        UUID actor = auth.currentUserId();
        return ApiResponse.ok(detail(service.create(actor, req, idemKey), service.canViewPii(actor)));
    }

    @GetMapping("/{id}")
    public ApiResponse<TechnicianDetailResponse> get(@PathVariable UUID id) {
        UUID actor = auth.currentUserId();
        return ApiResponse.ok(detail(service.get(actor, id), service.canViewPii(actor)));
    }

    @PutMapping("/{id}")
    public ApiResponse<TechnicianDetailResponse> update(@PathVariable UUID id, @Valid @RequestBody TechnicianUpdateRequest req) {
        UUID actor = auth.currentUserId();
        return ApiResponse.ok(detail(service.update(actor, id, req), service.canViewPii(actor)));
    }

    // ─── Lifecycle ───────────────────────────────────────────────────

    @PostMapping("/{id}/activate")
    public ApiResponse<TechnicianDetailResponse> activate(@PathVariable UUID id,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        UUID actor = auth.currentUserId();
        return ApiResponse.ok(detail(service.changeStatus(actor, id, "TECHNICIAN_ACTIVATE", "ACTIVE", null, idemKey), service.canViewPii(actor)));
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<TechnicianDetailResponse> suspend(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        UUID actor = auth.currentUserId();
        String reason = req == null ? null : req.reason();
        return ApiResponse.ok(detail(service.changeStatus(actor, id, "TECHNICIAN_SUSPEND", "SUSPENDED", reason, idemKey), service.canViewPii(actor)));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<TechnicianDetailResponse> deactivate(@PathVariable UUID id,
                                                            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        UUID actor = auth.currentUserId();
        return ApiResponse.ok(detail(service.changeStatus(actor, id, "TECHNICIAN_DEACTIVATE", "INACTIVE", null, idemKey), service.canViewPii(actor)));
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<TechnicianDetailResponse> terminate(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        UUID actor = auth.currentUserId();
        String reason = req == null ? null : req.reason();
        return ApiResponse.ok(detail(service.changeStatus(actor, id, "TECHNICIAN_TERMINATE", "TERMINATED", reason, idemKey), service.canViewPii(actor)));
    }

    @PostMapping("/{id}/availability")
    public ApiResponse<TechnicianDetailResponse> availability(@PathVariable UUID id, @Valid @RequestBody AvailabilityUpdateRequest req) {
        UUID actor = auth.currentUserId();
        return ApiResponse.ok(detail(service.updateAvailability(actor, id, req.availabilityStatus()), service.canViewPii(actor)));
    }

    // ─── Skills ──────────────────────────────────────────────────────

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

    @DeleteMapping("/{id}/skills/{skillId}")
    public ApiResponse<Void> removeSkill(@PathVariable UUID id, @PathVariable UUID skillId) {
        service.removeSkill(auth.currentUserId(), id, skillId);
        return ApiResponse.ok(null);
    }

    // ─── Areas ───────────────────────────────────────────────────────

    @GetMapping("/{id}/areas")
    public ApiResponse<List<WorkforceAreaResponse>> listAreas(@PathVariable UUID id) {
        return ApiResponse.ok(service.listAreas(auth.currentUserId(), id).stream()
                .map(a -> new WorkforceAreaResponse(a.getId(), a.getAreaId())).toList());
    }

    @PostMapping("/{id}/areas")
    public ApiResponse<WorkforceAreaResponse> addArea(@PathVariable UUID id, @Valid @RequestBody AssignAreaRequest req) {
        WorkforceArea a = service.addArea(auth.currentUserId(), id, req.areaId());
        return ApiResponse.ok(new WorkforceAreaResponse(a.getId(), a.getAreaId()));
    }

    @DeleteMapping("/{id}/areas/{areaId}")
    public ApiResponse<Void> removeArea(@PathVariable UUID id, @PathVariable UUID areaId) {
        service.removeArea(auth.currentUserId(), id, areaId);
        return ApiResponse.ok(null);
    }

    // ─── Certifications ──────────────────────────────────────────────

    @GetMapping("/{id}/certifications")
    public ApiResponse<List<CertificationResponse>> listCerts(@PathVariable UUID id) {
        return ApiResponse.ok(service.listCertifications(auth.currentUserId(), id).stream()
                .map(this::mapCert).toList());
    }

    @PostMapping("/{id}/certifications")
    public ApiResponse<CertificationResponse> addCert(@PathVariable UUID id, @Valid @RequestBody CertificationRequest req) {
        return ApiResponse.ok(mapCert(service.addCertification(auth.currentUserId(), id, req)));
    }

    private CertificationResponse mapCert(Certification c) {
        boolean expired = c.getExpiryDate() != null && c.getExpiryDate().isBefore(LocalDate.now());
        boolean expiringSoon = !expired && c.getExpiryDate() != null
                && c.getExpiryDate().isBefore(LocalDate.now().plusDays(30));
        return new CertificationResponse(c.getId(), c.getCertType(), c.getIssuingAuthority(), c.getReferenceNo(),
                c.getIssuedDate() == null ? null : c.getIssuedDate().toString(),
                c.getExpiryDate() == null ? null : c.getExpiryDate().toString(),
                c.getStatus(), expired, expiringSoon, c.getVersion());
    }

    // ─── Availability windows ────────────────────────────────────────

    @GetMapping("/{id}/availability-windows")
    public ApiResponse<List<AvailabilityResponse>> listAvailability(@PathVariable UUID id) {
        return ApiResponse.ok(service.listAvailability(auth.currentUserId(), id).stream()
                .map(w -> new AvailabilityResponse(w.getId(), w.getDayOfWeek(),
                        w.getSpecificDate() == null ? null : w.getSpecificDate().toString(),
                        w.getStartTime(), w.getEndTime(), w.isLeave(), w.getNote(), w.getVersion())).toList());
    }

    @PostMapping("/{id}/availability-windows")
    public ApiResponse<AvailabilityResponse> addAvailability(@PathVariable UUID id, @Valid @RequestBody AvailabilityRequest req) {
        WorkforceAvailability w = service.addAvailability(auth.currentUserId(), id, req);
        return ApiResponse.ok(new AvailabilityResponse(w.getId(), w.getDayOfWeek(),
                w.getSpecificDate() == null ? null : w.getSpecificDate().toString(),
                w.getStartTime(), w.getEndTime(), w.isLeave(), w.getNote(), w.getVersion()));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<StatusHistoryResponse>> history(@PathVariable UUID id) {
        return ApiResponse.ok(service.history(auth.currentUserId(), id).stream()
                .map(h -> new StatusHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getChangedBy(),
                        h.getReason(), h.getCreatedAt() == null ? null : h.getCreatedAt().toString())).toList());
    }
}
