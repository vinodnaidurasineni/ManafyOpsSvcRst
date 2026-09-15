package com.manafy.ops.dispatch.service;

import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.dispatch.domain.AssignmentStateMachine;
import com.manafy.ops.dispatch.entity.Assignment;
import com.manafy.ops.dispatch.entity.AssignmentStatusHistory;
import com.manafy.ops.dispatch.repository.AssignmentStatusHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Shared assignment transition helper: validates the transition via
 * {@link AssignmentStateMachine}, writes insert-only status history and an audit
 * record in the caller's transaction. Mirrors WorkforceLifecycleService so we don't
 * duplicate transition/history/audit plumbing per action.
 */
@Service
public class AssignmentLifecycleService {

    private final AssignmentStatusHistoryRepository historyRepo;
    private final AuditService audit;
    private final PermissionService permissionService;

    public AssignmentLifecycleService(AssignmentStatusHistoryRepository historyRepo,
                                      AuditService audit, PermissionService permissionService) {
        this.historyRepo = historyRepo;
        this.audit = audit;
        this.permissionService = permissionService;
    }

    /**
     * Validate + record an assignment status transition and apply it to the entity.
     * The caller saves the entity in the same transaction.
     */
    public void transition(Assignment a, String to, UUID actor, String reason) {
        String from = a.getStatus();
        AssignmentStateMachine.requireTransition(from, to);
        a.setStatus(to);
        record(a.getId(), from, to, actor, reason);
    }

    /** Write an insert-only history row + audit for a transition (no state-machine check). */
    public void record(UUID assignmentId, String from, String to, UUID actor, String reason) {
        AssignmentStatusHistory h = new AssignmentStatusHistory();
        h.setAssignmentId(assignmentId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(actor);
        h.setReason(reason);
        historyRepo.save(h);
        String role = permissionService.effectiveRoleCodes(actor).stream().sorted().findFirst().orElse(null);
        audit.audit(actor, role, "ASSIGNMENT_" + to, "ASSIGNMENT", assignmentId, from, to, reason);
        audit.activity("ASSIGNMENT", assignmentId, actor, "ASSIGNMENT_" + to, "Assignment " + from + " → " + to);
    }

    public List<AssignmentStatusHistory> history(UUID assignmentId) {
        return historyRepo.findByAssignmentIdOrderByCreatedAtAsc(assignmentId);
    }
}
