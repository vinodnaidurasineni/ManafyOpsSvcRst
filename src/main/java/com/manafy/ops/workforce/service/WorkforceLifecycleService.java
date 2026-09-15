package com.manafy.ops.workforce.service;

import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.workforce.domain.WorkforceStateMachine;
import com.manafy.ops.workforce.entity.WorkforceStatusHistory;
import com.manafy.ops.workforce.repository.WorkforceStatusHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Shared lifecycle transition helper for all workforce kinds (technician, helper,
 * vendor, employee). Validates the transition via {@link WorkforceStateMachine},
 * writes insert-only status history and an audit record in the caller's
 * transaction. Avoids duplicating rollover/transition logic per entity.
 */
@Service
public class WorkforceLifecycleService {

    private final WorkforceStatusHistoryRepository historyRepo;
    private final AuditService audit;
    private final PermissionService permissionService;

    public WorkforceLifecycleService(WorkforceStatusHistoryRepository historyRepo,
                                     AuditService audit, PermissionService permissionService) {
        this.historyRepo = historyRepo;
        this.audit = audit;
        this.permissionService = permissionService;
    }

    /**
     * Validate + record a workforce status transition. Returns the new status.
     * The caller applies the status to its entity and saves it in the same txn.
     */
    public String transition(String kind, UUID workforceId, String from, String to,
                             UUID actor, String reason) {
        WorkforceStateMachine.requireTransition(from, to);
        WorkforceStatusHistory h = new WorkforceStatusHistory();
        h.setWorkforceKind(kind);
        h.setWorkforceId(workforceId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(actor);
        h.setReason(reason);
        historyRepo.save(h);
        String role = permissionService.effectiveRoleCodes(actor).stream().sorted().findFirst().orElse(null);
        audit.audit(actor, role, kind + "_" + to, kind, workforceId, from, to, reason);
        audit.activity(kind, workforceId, actor, kind + "_" + to, "Status " + from + " → " + to);
        return to;
    }

    public java.util.List<WorkforceStatusHistory> history(String kind, UUID workforceId) {
        return historyRepo.findByWorkforceKindAndWorkforceIdOrderByCreatedAtAsc(kind, workforceId);
    }
}
