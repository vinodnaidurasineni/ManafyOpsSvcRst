package com.manafy.ops.dispatch.controller;

import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.dispatch.dto.DispatchDtos.*;
import com.manafy.ops.dispatch.service.OperationsQueueService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Operational + dispatch queue endpoints (Phase 4B §14, §15). Thin controller;
 * queries are scoped, paginated and bounded in the service. Backend data only — no UI.
 */
@RestController
@RequestMapping("/api/v1/operations")
public class OperationsQueueController {

    private final OperationsQueueService service;
    private final AuthenticationContext auth;

    public OperationsQueueController(OperationsQueueService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping("/service-requests/queue")
    public PageResponse<OperationalQueueItem> operationalQueue(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID areaId,
            @RequestParam(required = false) String assignmentStatus,
            @RequestParam(required = false) UUID technicianId,
            @RequestParam(required = false) UUID vendorId) {
        return service.operationalQueue(auth.currentUserId(), page, pageSize, status, priority, areaId,
                assignmentStatus, technicianId, vendorId);
    }

    @GetMapping("/dispatch/queue")
    public PageResponse<DispatchQueueItem> dispatchQueue(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) UUID areaId) {
        return service.dispatchQueue(auth.currentUserId(), page, pageSize, areaId);
    }
}
