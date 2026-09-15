package com.manafy.ops.dispatch.service;

import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.security.*;
import com.manafy.ops.dispatch.dto.DispatchDtos.*;
import com.manafy.ops.dispatch.entity.Assignment;
import com.manafy.ops.dispatch.repository.AssignmentRepository;
import com.manafy.ops.servicerequest.entity.ServiceRequest;
import com.manafy.ops.servicerequest.repository.ServiceRequestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Operational + dispatch queue reads (Phase 4B §14, §15). Scoped, paginated, and
 * bounded (never unbounded). Reuses ScopeService for the same area-visibility model
 * as service-request listing. Read-only.
 */
@Service
public class OperationsQueueService {

    private final ServiceRequestRepository srRepo;
    private final AssignmentRepository assignmentRepo;
    private final AuthorizationService authz;
    private final ScopeService scopeService;

    public OperationsQueueService(ServiceRequestRepository srRepo, AssignmentRepository assignmentRepo,
                                  AuthorizationService authz, ScopeService scopeService) {
        this.srRepo = srRepo;
        this.assignmentRepo = assignmentRepo;
        this.authz = authz;
        this.scopeService = scopeService;
    }

    private boolean inScope(ServiceRequest sr, boolean global, Set<UUID> visibleAreas, Set<UUID> apartmentScope) {
        return global || visibleAreas.contains(sr.getAreaId()) || apartmentScope.contains(sr.getApartmentId());
    }

    private Optional<Assignment> activeAssignment(UUID srId) {
        return assignmentRepo.findByServiceRequestIdAndActiveTrueAndDeletedFalse(srId);
    }

    /** Operational queue: scoped service requests with their active-assignment view + filters. */
    @Transactional(readOnly = true)
    public PageResponse<OperationalQueueItem> operationalQueue(
            UUID actor, Integer page, Integer pageSize, String status, String priority, UUID areaId,
            String assignmentStatus, UUID technicianId, UUID vendorId) {
        authz.requirePermission(actor, "ASSIGNMENT_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        boolean global = scopeService.hasGlobal(actor);
        var visibleAreas = scopeService.visibleAreaIds(actor);
        var apartmentScope = scopeService.grantedApartmentIds(actor);
        String st = status == null ? null : status.trim().toUpperCase();
        String pr = priority == null ? null : priority.trim().toUpperCase();
        String ast = assignmentStatus == null ? null : assignmentStatus.trim().toUpperCase();

        List<OperationalQueueItem> all = new ArrayList<>();
        for (ServiceRequest sr : srRepo.findByDeletedFalse(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
            if (!inScope(sr, global, visibleAreas, apartmentScope)) continue;
            if (st != null && !st.equals(sr.getStatus())) continue;
            if (pr != null && !pr.equals(sr.getPriority())) continue;
            if (areaId != null && !areaId.equals(sr.getAreaId())) continue;
            Optional<Assignment> a = activeAssignment(sr.getId());
            if (assignmentStatus != null && (a.isEmpty() || !ast.equals(a.get().getStatus()))) continue;
            if (technicianId != null && (a.isEmpty() || !technicianId.equals(a.get().getTechnicianId()))) continue;
            if (vendorId != null && (a.isEmpty() || !vendorId.equals(a.get().getVendorId()))) continue;
            all.add(new OperationalQueueItem(sr.getId(), sr.getReferenceNo(), sr.getApartmentId(), sr.getAreaId(),
                    sr.getCategory(), sr.getPriority(), sr.getStatus(),
                    a.map(Assignment::getId).orElse(null), a.map(Assignment::getTechnicianId).orElse(null),
                    a.map(Assignment::getStatus).orElse(null), a.map(Assignment::getScheduledStart).orElse(null),
                    sr.getCreatedAt()));
        }
        all.sort((x, y) -> {
            int cmp = priorityRank(y.priority()) - priorityRank(x.priority());
            if (cmp != 0) return cmp;
            LocalDateTime xc = x.createdAt(), yc = y.createdAt();
            return (xc == null || yc == null) ? 0 : xc.compareTo(yc);
        });
        return paginate(all, p, ps);
    }

    /** Dispatch queue: requests that need a dispatch decision (unassigned/declined/rework/etc.). */
    @Transactional(readOnly = true)
    public PageResponse<DispatchQueueItem> dispatchQueue(UUID actor, Integer page, Integer pageSize, UUID areaId) {
        authz.requirePermission(actor, "ASSIGNMENT_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        boolean global = scopeService.hasGlobal(actor);
        var visibleAreas = scopeService.visibleAreaIds(actor);
        var apartmentScope = scopeService.grantedApartmentIds(actor);

        List<DispatchQueueItem> all = new ArrayList<>();
        for (ServiceRequest sr : srRepo.findByDeletedFalse(PageRequest.of(0, Integer.MAX_VALUE)).getContent()) {
            if (!inScope(sr, global, visibleAreas, apartmentScope)) continue;
            if (areaId != null && !areaId.equals(sr.getAreaId())) continue;
            Optional<Assignment> a = activeAssignment(sr.getId());
            String reason = dispatchReason(sr, a.orElse(null));
            if (reason == null) continue; // not a dispatch concern
            all.add(new DispatchQueueItem(sr.getId(), sr.getReferenceNo(), sr.getApartmentId(), sr.getAreaId(),
                    sr.getCategory(), sr.getPriority(), sr.getStatus(),
                    a.map(Assignment::getId).orElse(null), a.map(Assignment::getStatus).orElse(null),
                    reason, sr.getCreatedAt()));
        }
        all.sort((x, y) -> {
            int cmp = priorityRank(y.priority()) - priorityRank(x.priority());
            if (cmp != 0) return cmp;
            LocalDateTime xc = x.createdAt(), yc = y.createdAt();
            return (xc == null || yc == null) ? 0 : xc.compareTo(yc);
        });
        return paginate(all, p, ps);
    }

    /** Why a request appears on the dispatch queue, or null if it doesn't. */
    private String dispatchReason(ServiceRequest sr, Assignment active) {
        switch (sr.getStatus()) {
            case "NEW":    return "UNASSIGNED";
            case "REWORK": return "REWORK";
            default: break;
        }
        if (active != null) {
            switch (active.getStatus()) {
                case "ASSIGNED": return "AWAITING_ACCEPTANCE";
                default: return null;
            }
        }
        return null;
    }

    private int priorityRank(String priority) {
        if (priority == null) return 0;
        return switch (priority) {
            case "URGENT" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private <T> PageResponse<T> paginate(List<T> all, int p, int ps) {
        long total = all.size();
        int from = Math.min((p - 1) * ps, all.size());
        int to = Math.min(from + ps, all.size());
        return PageResponse.of(all.subList(from, to), p, ps, total);
    }
}
