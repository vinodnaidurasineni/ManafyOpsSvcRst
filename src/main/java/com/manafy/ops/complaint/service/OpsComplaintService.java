package com.manafy.ops.complaint.service;

import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.*;
import com.manafy.ops.complaint.dto.ComplaintDtos.*;
import com.manafy.ops.complaint.entity.OpsComplaint;
import com.manafy.ops.complaint.repository.OpsComplaintRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Operational complaint management for Ops users. Mirrors the manual-request app
 * service conventions exactly: permission gate + area scope (AREA_RESPONSIBLE) +
 * optimistic version + audit + PageResponse envelope. Deliberately NOT wired to
 * technician dispatch — a complaint is worked in place and the maid/helper is an
 * opaque assignee, never a workforce FK.
 *
 * Community remains the source of truth for resident-facing society complaints;
 * this service only reads/updates the Ops-side operational projection.
 */
@Service
public class OpsComplaintService {

    private static final String KIND = "OPS_COMPLAINT";
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");

    private final OpsComplaintRepository repo;
    private final AuthorizationService authz;
    private final ScopeService scopeService;
    private final ResourceScopeResolver resolver;
    private final PermissionService permissionService;
    private final AuditService audit;

    public OpsComplaintService(OpsComplaintRepository repo, AuthorizationService authz,
                               ScopeService scopeService, ResourceScopeResolver resolver,
                               PermissionService permissionService, AuditService audit) {
        this.repo = repo;
        this.authz = authz;
        this.scopeService = scopeService;
        this.resolver = resolver;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    private OpsComplaint load(UUID id) {
        return repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Complaint not found"));
    }

    /** Scope ref reusing the assignment shape (area/region anchors; null-area = GLOBAL-only). */
    private ResourceRef ref(OpsComplaint c) {
        return resolver.assignment(c.getId(), null, c.getAreaId(), c.getRegionId());
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private void requireVersion(OpsComplaint c, Long expected) {
        if (expected != null && expected != c.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT",
                    "Complaint was modified concurrently. Reload and retry.", HttpStatus.CONFLICT);
        }
    }

    // ─── READ ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ComplaintListItem> list(UUID actor, Integer page, Integer pageSize, String status) {
        authz.requirePermission(actor, "COMPLAINT_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        boolean global = scopeService.hasGlobal(actor);
        Set<UUID> visibleAreas = scopeService.visibleAreaIds(actor);
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();

        var all = repo.findByDeletedFalse(PageRequest.of(0, Integer.MAX_VALUE)).getContent().stream()
                .filter(c -> global || (c.getAreaId() != null && visibleAreas.contains(c.getAreaId())))
                .filter(c -> statusFilter == null || statusFilter.equals(c.getStatus()))
                .sorted((x, y) -> {
                    LocalDateTime xc = x.getCreatedAt(), yc = y.getCreatedAt();
                    int cmp = (xc == null || yc == null) ? 0 : yc.compareTo(xc);
                    return cmp != 0 ? cmp : x.getReferenceNo().compareTo(y.getReferenceNo());
                })
                .toList();
        long total = all.size();
        int from = Math.min((p - 1) * ps, all.size());
        int to = Math.min(from + ps, all.size());
        List<ComplaintListItem> data = all.subList(from, to).stream()
                .map(c -> new ComplaintListItem(c.getId(), c.getReferenceNo(), c.getComplaintType(),
                        c.getStatus(), c.getPriority(), c.getCustomerName(), c.getAssigneeName(), c.getCreatedAt()))
                .toList();
        return PageResponse.of(data, p, ps, total);
    }

    @Transactional(readOnly = true)
    public OpsComplaint get(UUID actor, UUID id) {
        OpsComplaint c = load(id);
        authz.authorize(actor, "COMPLAINT_VIEW", ref(c), RelationshipPredicate.AREA_RESPONSIBLE);
        return c;
    }

    // ─── UPDATE STATUS ────────────────────────────────────────────────

    @Transactional
    public OpsComplaint updateStatus(UUID actor, UUID id, String status, String adminNotes, Long version) {
        OpsComplaint c = load(id);
        authz.authorize(actor, "COMPLAINT_UPDATE", ref(c), RelationshipPredicate.AREA_RESPONSIBLE);
        requireVersion(c, version);
        String to = status == null ? null : status.trim().toUpperCase();
        if (to == null || !STATUSES.contains(to)) {
            throw BusinessException.validation("Invalid status: " + status);
        }
        String from = c.getStatus();
        c.setStatus(to);
        if (adminNotes != null) c.setAdminNotes(adminNotes);
        if ("RESOLVED".equals(to) && c.getResolvedAt() == null) c.setResolvedAt(LocalDateTime.now());
        if ("CLOSED".equals(to) && c.getClosedAt() == null) c.setClosedAt(LocalDateTime.now());
        OpsComplaint saved = repo.save(c);
        audit.audit(actor, primaryRole(actor), "OPS_COMPLAINT_STATUS_CHANGED", KIND, saved.getId(),
                from, to, adminNotes);
        return saved;
    }

    // ─── ASSIGN (record the maid/helper handling it; opaque) ──────────

    @Transactional
    public OpsComplaint assign(UUID actor, UUID id, AssignRequest req, Long version) {
        OpsComplaint c = load(id);
        authz.authorize(actor, "COMPLAINT_UPDATE", ref(c), RelationshipPredicate.AREA_RESPONSIBLE);
        requireVersion(c, version);
        if (req.assigneeName() == null || req.assigneeName().isBlank()) {
            throw BusinessException.validation("assigneeName is required");
        }
        String from = c.getStatus();
        c.setAssigneeRef(req.assigneeRef());
        c.setAssigneeName(req.assigneeName().trim());
        c.setAssigneeMobile(req.assigneeMobile());
        if (req.adminNotes() != null) c.setAdminNotes(req.adminNotes());
        // Assigning work on an OPEN complaint moves it into progress.
        if ("OPEN".equals(c.getStatus())) c.setStatus("IN_PROGRESS");
        OpsComplaint saved = repo.save(c);
        audit.audit(actor, primaryRole(actor), "OPS_COMPLAINT_ASSIGNED", KIND, saved.getId(),
                from, saved.getStatus(), "Assigned to " + req.assigneeName());
        return saved;
    }

    // ─── Counts (for dashboard) ───────────────────────────────────────

    @Transactional(readOnly = true)
    public long countOpen() {
        return repo.countByStatusAndDeletedFalse("OPEN");
    }
}
