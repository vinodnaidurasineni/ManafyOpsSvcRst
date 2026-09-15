package com.manafy.ops.servicerequest.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.servicerequest.dto.ServiceRequestDtos.*;
import com.manafy.ops.servicerequest.entity.ServiceRequest;
import com.manafy.ops.servicerequest.service.ServiceRequestAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Service Request endpoints (Phase 4A). Thin controller — all rules live in the service. */
@RestController
@RequestMapping("/api/v1/service-requests")
public class ServiceRequestController {

    private final ServiceRequestAppService service;
    private final AuthenticationContext auth;

    public ServiceRequestController(ServiceRequestAppService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    private ServiceRequestResponse detail(ServiceRequest sr) {
        return new ServiceRequestResponse(sr.getId(), sr.getReferenceNo(), sr.getApartmentId(),
                sr.getAreaId(), sr.getRegionId(), sr.getRequesterUserId(), sr.getCategory(),
                sr.getPriority(), sr.getDescription(), sr.getStatus(), sr.getCancelledAt(),
                sr.getCancelReason(), sr.getCreatedAt(), sr.getVersion());
    }

    @PostMapping
    public ApiResponse<ServiceRequestResponse> create(
            @Valid @RequestBody CreateRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return ApiResponse.ok(detail(service.create(auth.currentUserId(), req, idemKey)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceRequestResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(detail(service.get(auth.currentUserId(), id)));
    }

    @GetMapping
    public PageResponse<ServiceRequestListItemResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) UUID apartmentId,
            @RequestParam(required = false) UUID areaId,
            @RequestParam(required = false) UUID requesterId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        return service.list(auth.currentUserId(), page, pageSize, apartmentId, areaId, requesterId, status, priority);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ServiceRequestResponse> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelRequest req,
            @RequestParam(required = false) Long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        String reason = req == null ? null : req.reason();
        return ApiResponse.ok(detail(service.cancel(auth.currentUserId(), id, reason, version, idemKey)));
    }
}
