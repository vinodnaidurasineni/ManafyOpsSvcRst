package com.manafy.ops.manualrequest.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.manualrequest.dto.ManualAssignmentDtos.*;
import com.manafy.ops.manualrequest.entity.ManualAssignmentRequest;
import com.manafy.ops.manualrequest.service.ManualAssignmentRequestAppService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Manual assignment request endpoints (Recurring Helpers & other manual, FO-driven
 * fulfillment). Thin controller — all rules live in the app service.
 *
 * Two auth models:
 *  - /intake is service-to-service (Community), authenticated by a shared
 *    X-Service-Token (NOT a resident/Ops JWT). No OpsUser actor.
 *  - all other endpoints are Ops-user endpoints gated by permission + area scope
 *    inside the service (ASSIGNMENT_VIEW/CREATE/REASSIGN/CANCEL + AREA_RESPONSIBLE).
 */
@RestController
@RequestMapping("/api/v1/manual-requests")
public class ManualAssignmentRequestController {

    private final ManualAssignmentRequestAppService service;
    private final AuthenticationContext auth;

    @Value("${manafy.integration.service-token:}")
    private String serviceToken;

    public ManualAssignmentRequestController(ManualAssignmentRequestAppService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    private ManualRequestResponse detail(ManualAssignmentRequest r) {
        return new ManualRequestResponse(r.getId(), r.getReferenceNo(), r.getStatus(),
                r.getSourceSystem(), r.getSourceType(), r.getSourceId(),
                r.getCommunityApartmentId(), r.getCommunityFlatId(), r.getAreaId(), r.getRegionId(),
                r.getServiceType(), r.getServiceTitle(), r.getFrequency(), r.getStartDate(),
                r.getTimeSlot(), r.getServiceAddress(), r.getResidentName(), r.getContactNumber(),
                r.getNotes(), r.getAmount(), r.getPaymentStatus(), r.getPriority(),
                r.getFieldOfficerId(), r.getAssigneeType(), r.getAssigneeRef(), r.getAssigneeName(),
                r.getAssigneePhone(), r.getCompletionNotes(), r.getCancelReason(),
                r.getCreatedAt(), r.getVersion());
    }

    // ─── SERVICE-TO-SERVICE INTAKE ───────────────────────────────────

    @PostMapping("/intake")
    public ApiResponse<ManualRequestResponse> intake(
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
            @Valid @RequestBody IntakeRequest req) {
        requireServiceToken(token);
        return ApiResponse.ok(detail(service.intake(req, idemKey)));
    }

    private void requireServiceToken(String token) {
        if (serviceToken == null || serviceToken.isBlank() || !serviceToken.equals(token)) {
            throw new BusinessException("FORBIDDEN", "Invalid service token", HttpStatus.FORBIDDEN);
        }
    }

    // ─── OPS-USER ENDPOINTS (permission + area scope in service) ─────

    @GetMapping
    public PageResponse<ManualRequestListItem> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status) {
        return service.list(auth.currentUserId(), page, pageSize, status);
    }

    /**
     * Eligible assignees for the FO picker. When {@code requestId} is supplied the
     * list is filtered to helpers who can perform that request's service and is
     * ordered by today's workload (least-loaded first). Without it, all active +
     * available helpers are returned (also workload-ordered).
     */
    @GetMapping("/candidates")
    public ApiResponse<List<AssignableCandidate>> candidates(
            @RequestParam(required = false) UUID requestId) {
        return ApiResponse.ok(service.assignableCandidates(auth.currentUserId(), requestId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ManualRequestResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(detail(service.get(auth.currentUserId(), id)));
    }

    @PostMapping("/{id}/ack")
    public ApiResponse<ManualRequestResponse> ack(
            @PathVariable UUID id,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.ack(auth.currentUserId(), id, version, idemKey)));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<ManualRequestResponse> assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.assign(auth.currentUserId(), id, req, version, idemKey)));
    }

    @PostMapping("/{id}/reassign")
    public ApiResponse<ManualRequestResponse> reassign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.reassign(auth.currentUserId(), id, req, version, idemKey)));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<ManualRequestResponse> start(
            @PathVariable UUID id,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.start(auth.currentUserId(), id, version, idemKey)));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<ManualRequestResponse> complete(
            @PathVariable UUID id,
            @RequestBody(required = false) CompleteRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        String notes = req == null ? null : req.notes();
        return ApiResponse.ok(detail(service.complete(auth.currentUserId(), id, notes, version, idemKey)));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ManualRequestResponse> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        String reason = req == null ? null : req.reason();
        return ApiResponse.ok(detail(service.cancel(auth.currentUserId(), id, reason, version, idemKey)));
    }
}
