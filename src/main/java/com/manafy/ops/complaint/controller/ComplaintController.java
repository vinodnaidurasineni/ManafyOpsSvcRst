package com.manafy.ops.complaint.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.complaint.dto.ComplaintDtos.*;
import com.manafy.ops.complaint.entity.OpsComplaint;
import com.manafy.ops.complaint.service.OpsComplaintService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Ops complaint endpoints. Thin controller — all rules (permission + area scope +
 * version) live in the app service. Complaints are viewed and worked by Ops users;
 * the maid/helper handling a complaint is recorded as an opaque assignee.
 */
@RestController
@RequestMapping("/api/v1/complaints")
public class ComplaintController {

    private final OpsComplaintService service;
    private final AuthenticationContext auth;

    public ComplaintController(OpsComplaintService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    private ComplaintResponse detail(OpsComplaint c) {
        return new ComplaintResponse(c.getId(), c.getReferenceNo(), c.getComplaintType(), c.getStatus(),
                c.getPriority(), c.getDescription(), c.getCustomerName(), c.getCustomerMobile(),
                c.getAssigneeRef(), c.getAssigneeName(), c.getAssigneeMobile(), c.getBookingReference(),
                c.getAdminNotes(), c.getCommunityApartmentId(), c.getAreaId(), c.getRegionId(),
                c.getResolvedAt(), c.getClosedAt(), c.getCreatedAt(), c.getVersion());
    }

    @GetMapping
    public PageResponse<ComplaintListItem> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status) {
        return service.list(auth.currentUserId(), page, pageSize, status);
    }

    @GetMapping("/{id}")
    public ApiResponse<ComplaintResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(detail(service.get(auth.currentUserId(), id)));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<ComplaintResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest req,
            @RequestParam(required = false) Long version) {
        return ApiResponse.ok(detail(service.updateStatus(auth.currentUserId(), id, req.status(), req.adminNotes(), version)));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<ComplaintResponse> assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRequest req,
            @RequestParam(required = false) Long version) {
        return ApiResponse.ok(detail(service.assign(auth.currentUserId(), id, req, version)));
    }
}
