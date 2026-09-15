package com.manafy.ops.dispatch.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.dispatch.dto.DispatchDtos.*;
import com.manafy.ops.dispatch.entity.Assignment;
import com.manafy.ops.dispatch.service.AssignmentAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Assignment action endpoints (Phase 4B §7). Thin controller — all lifecycle rules,
 * eligibility, scope and projection live in the service. Explicit action endpoints
 * only; there is NO generic PATCH /assignments/{id}/status.
 */
@RestController
@RequestMapping("/api/v1")
public class AssignmentController {

    private final AssignmentAppService service;
    private final AuthenticationContext auth;

    public AssignmentController(AssignmentAppService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    static AssignmentResponse detail(Assignment a) {
        return new AssignmentResponse(a.getId(), a.getReferenceNo(), a.getServiceRequestId(),
                a.getTechnicianId(), a.getHelperId(), a.getVendorId(), a.getStatus(), a.isActive(),
                a.getScheduledStart(), a.getScheduledEnd(), a.getAssignedAt(), a.getAcceptedAt(),
                a.getDeclinedAt(), a.getEnRouteAt(), a.getArrivedAt(), a.getStartedAt(),
                a.getCompletedAt(), a.getCancelledAt(), a.getCompletionNotes(), a.getDeclineReason(),
                a.getCancelReason(), a.getNoShowReason(), a.getReworkReason(), a.getPreviousAssignmentId(),
                a.getCreatedAt(), a.getVersion());
    }

    // ─── Create / list / get / history (nested under the request) ────

    @PostMapping("/service-requests/{requestId}/assignments")
    public ApiResponse<AssignmentResponse> create(
            @PathVariable UUID requestId, @Valid @RequestBody CreateAssignmentRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.create(auth.currentUserId(), requestId, req, idemKey)));
    }

    @GetMapping("/service-requests/{requestId}/assignments")
    public ApiResponse<List<AssignmentListItemResponse>> listForRequest(@PathVariable UUID requestId) {
        return ApiResponse.ok(service.listForRequest(auth.currentUserId(), requestId).stream()
                .map(a -> new AssignmentListItemResponse(a.getId(), a.getReferenceNo(), a.getServiceRequestId(),
                        a.getTechnicianId(), a.getStatus(), a.isActive(), a.getScheduledStart(), a.getCreatedAt()))
                .toList());
    }

    @GetMapping("/service-requests/{requestId}/eligible-technicians")
    public ApiResponse<List<EligibleTechnicianResponse>> eligible(@PathVariable UUID requestId) {
        return ApiResponse.ok(service.eligibleTechnicians(auth.currentUserId(), requestId));
    }

    @GetMapping("/assignments/{id}")
    public ApiResponse<AssignmentResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(detail(service.get(auth.currentUserId(), id)));
    }

    @GetMapping("/assignments/{id}/history")
    public ApiResponse<List<AssignmentHistoryItemResponse>> history(@PathVariable UUID id) {
        return ApiResponse.ok(service.history(auth.currentUserId(), id).stream()
                .map(h -> new AssignmentHistoryItemResponse(h.getFromStatus(), h.getToStatus(),
                        h.getChangedBy(), h.getReason(), h.getCreatedAt()))
                .toList());
    }

    // ─── Lifecycle actions ───────────────────────────────────────────

    @PostMapping("/assignments/{id}/accept")
    public ApiResponse<AssignmentResponse> accept(@PathVariable UUID id, @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.accept(auth.currentUserId(), id, version, idemKey)));
    }

    @PostMapping("/assignments/{id}/decline")
    public ApiResponse<AssignmentResponse> decline(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.decline(auth.currentUserId(), id, reason(req), version, idemKey)));
    }

    @PostMapping("/assignments/{id}/reassign")
    public ApiResponse<AssignmentResponse> reassign(@PathVariable UUID id, @Valid @RequestBody ReassignRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.reassign(auth.currentUserId(), id, req, version, idemKey)));
    }

    @PostMapping("/assignments/{id}/cancel")
    public ApiResponse<AssignmentResponse> cancel(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.cancel(auth.currentUserId(), id, reason(req), version, idemKey)));
    }

    @PostMapping("/assignments/{id}/reschedule")
    public ApiResponse<AssignmentResponse> reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.reschedule(auth.currentUserId(), id, req, version, idemKey)));
    }

    @PostMapping("/assignments/{id}/en-route")
    public ApiResponse<AssignmentResponse> enRoute(@PathVariable UUID id, @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.enRoute(auth.currentUserId(), id, version, idemKey)));
    }

    @PostMapping("/assignments/{id}/arrive")
    public ApiResponse<AssignmentResponse> arrive(@PathVariable UUID id, @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.arrive(auth.currentUserId(), id, version, idemKey)));
    }

    @PostMapping("/assignments/{id}/start")
    public ApiResponse<AssignmentResponse> start(@PathVariable UUID id, @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.start(auth.currentUserId(), id, version, idemKey)));
    }

    @PostMapping("/assignments/{id}/complete")
    public ApiResponse<AssignmentResponse> complete(@PathVariable UUID id, @RequestBody(required = false) CompleteRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        String notes = req == null ? null : req.notes();
        return ApiResponse.ok(detail(service.complete(auth.currentUserId(), id, notes, version, idemKey)));
    }

    @PostMapping("/assignments/{id}/no-show")
    public ApiResponse<AssignmentResponse> noShow(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.noShow(auth.currentUserId(), id, reason(req), version, idemKey)));
    }

    @PostMapping("/assignments/{id}/rework")
    public ApiResponse<AssignmentResponse> rework(@PathVariable UUID id, @Valid @RequestBody ReassignRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.rework(auth.currentUserId(), id, req, version, idemKey)));
    }

    private static String reason(ReasonRequest req) { return req == null ? null : req.reason(); }
}
