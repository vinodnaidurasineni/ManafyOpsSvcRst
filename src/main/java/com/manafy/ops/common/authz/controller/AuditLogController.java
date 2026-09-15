package com.manafy.ops.common.authz.controller;

import com.manafy.ops.common.authz.dto.AuthzDtos.AuditLogResponse;
import com.manafy.ops.common.authz.entity.AuditLog;
import com.manafy.ops.common.authz.repository.AuditLogRepository;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.common.security.AuthorizationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only audit log access (Artifact #3 §1, spec §48). Requires AUDIT_VIEW. */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditRepo;
    private final AuthenticationContext authContext;
    private final AuthorizationService authz;

    public AuditLogController(AuditLogRepository auditRepo, AuthenticationContext authContext,
                              AuthorizationService authz) {
        this.auditRepo = auditRepo;
        this.authContext = authContext;
        this.authz = authz;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer pageSize) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "AUDIT_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        var result = auditRepo.findByOrderByCreatedAtDesc(PageRequest.of(p - 1, ps));
        List<AuditLogResponse> data = result.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(data, p, ps, result.getTotalElements());
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getActorUserId(), a.getActorRole(), a.getAction(),
                a.getResourceType(), a.getResourceId(), a.getReason(), a.getSource(),
                a.getCorrelationId(), a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
    }
}
