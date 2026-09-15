package com.manafy.ops.dispatch.service;

import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.*;
import com.manafy.ops.dispatch.domain.AssignmentStateMachine;
import com.manafy.ops.dispatch.dto.DispatchDtos.*;
import com.manafy.ops.dispatch.entity.Assignment;
import com.manafy.ops.dispatch.entity.FieldVisit;
import com.manafy.ops.dispatch.repository.AssignmentRepository;
import com.manafy.ops.dispatch.repository.FieldVisitRepository;
import com.manafy.ops.servicerequest.domain.ServiceRequestStateMachine;
import com.manafy.ops.servicerequest.entity.ServiceRequest;
import com.manafy.ops.servicerequest.service.ServiceRequestAppService;
import com.manafy.ops.workforce.entity.Technician;
import com.manafy.ops.workforce.repository.TechnicianRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Operations & Dispatch application service (Phase 4B). Owns the assignment lifecycle
 * and the atomic service-request projection (DD-36): every assignment mutation updates
 * the assignment, writes assignment history, projects the request status, and audits —
 * all in one transaction, so request and assignment cannot drift.
 *
 * Authorization reuses AuthorizationService (permission + scope resolved through the
 * parent service request). Reassignment preserves history: the old assignment is
 * CANCELLED and a new row inserted. Optimistic locking (@Version) guards concurrent
 * operations; a stale version yields CONCURRENCY_CONFLICT.
 */
@Service
public class AssignmentAppService {

    private final AssignmentRepository assignmentRepo;
    private final FieldVisitRepository fieldVisitRepo;
    private final TechnicianRepository technicianRepo;
    private final ServiceRequestAppService serviceRequestService;
    private final TechnicianEligibilityService eligibility;
    private final AssignmentLifecycleService lifecycle;
    private final AuthorizationService authz;
    private final ScopeService scopeService;
    private final ResourceScopeResolver resolver;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final IdempotencyService idempotency;

    public AssignmentAppService(AssignmentRepository assignmentRepo, FieldVisitRepository fieldVisitRepo,
                                TechnicianRepository technicianRepo, ServiceRequestAppService serviceRequestService,
                                TechnicianEligibilityService eligibility, AssignmentLifecycleService lifecycle,
                                AuthorizationService authz, ScopeService scopeService,
                                ResourceScopeResolver resolver, PermissionService permissionService,
                                AuditService audit, IdempotencyService idempotency) {
        this.assignmentRepo = assignmentRepo;
        this.fieldVisitRepo = fieldVisitRepo;
        this.technicianRepo = technicianRepo;
        this.serviceRequestService = serviceRequestService;
        this.eligibility = eligibility;
        this.lifecycle = lifecycle;
        this.authz = authz;
        this.scopeService = scopeService;
        this.resolver = resolver;
        this.permissionService = permissionService;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    // ─── helpers ─────────────────────────────────────────────────────

    private Assignment load(UUID id) {
        return assignmentRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Assignment not found"));
    }

    /** ResourceRef for an assignment, anchored through its parent service request (IDOR-safe). */
    private ResourceRef refFor(Assignment a) {
        ServiceRequest sr = serviceRequestService.loadForDispatch(a.getServiceRequestId());
        return resolver.assignment(a.getId(), sr.getApartmentId(), sr.getAreaId(), sr.getRegionId());
    }

    private ResourceRef refForRequest(ServiceRequest sr) {
        return resolver.serviceRequest(sr.getId(), sr.getApartmentId(), sr.getAreaId(), sr.getRegionId());
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private void requireVersion(Assignment a, Long expected) {
        if (expected != null && expected != a.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT",
                    "Assignment was modified concurrently. Reload and retry.", HttpStatus.CONFLICT);
        }
    }

    private String generateReference() {
        return "ASG-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase().replace("-", "");
    }

    /** Validate a technician is assignable to a request (existence + full eligibility). */
    private Technician requireEligibleTechnician(ServiceRequest sr, UUID technicianId) {
        Technician t = technicianRepo.findByIdAndDeletedFalse(technicianId)
                .orElseThrow(() -> BusinessException.validation("Technician not found: " + technicianId));
        var reason = eligibility.evaluate(sr, t);
        if (!reason.eligible()) {
            throw BusinessException.validation("Technician not eligible: " + reason.reason());
        }
        return t;
    }

    private void validateSchedule(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw BusinessException.validation("scheduled_end must not be before scheduled_start");
        }
    }

    // ─── Create assignment (dispatch) ────────────────────────────────

    @Transactional
    public Assignment create(UUID actor, UUID serviceRequestId, CreateAssignmentRequest req, String idemKey) {
        ServiceRequest sr = serviceRequestService.loadForDispatch(serviceRequestId);
        authz.authorize(actor, "ASSIGNMENT_CREATE", refForRequest(sr));
        idempotency.register(idemKey, "POST /service-requests/{id}/assignments", actor);

        // The request must be dispatchable (NEW or REWORK — awaiting an assignment).
        if (!ServiceRequestStateMachine.NEW.equals(sr.getStatus())
                && !ServiceRequestStateMachine.REWORK.equals(sr.getStatus())) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Service request is not awaiting assignment (status=" + sr.getStatus() + ")", HttpStatus.CONFLICT);
        }
        // One active assignment per request (invariant enforced in-service).
        if (assignmentRepo.findByServiceRequestIdAndActiveTrueAndDeletedFalse(serviceRequestId).isPresent()) {
            throw new BusinessException("RESOURCE_CONFLICT",
                    "Service request already has an active assignment", HttpStatus.CONFLICT);
        }
        validateSchedule(req.scheduledStart(), req.scheduledEnd());
        Technician t = requireEligibleTechnician(sr, req.technicianId());

        Assignment a = new Assignment();
        a.setReferenceNo(generateReference());
        a.setServiceRequestId(serviceRequestId);
        a.setTechnicianId(t.getId());
        a.setHelperId(req.helperId());
        a.setVendorId(t.getVendorId());
        a.setStatus(AssignmentStateMachine.ASSIGNED);
        a.setActive(true);
        a.setScheduledStart(req.scheduledStart());
        a.setScheduledEnd(req.scheduledEnd());
        a.setAssignedAt(LocalDateTime.now());
        a.setAssignedBy(actor);
        Assignment saved = assignmentRepo.save(a);
        lifecycle.record(saved.getId(), null, AssignmentStateMachine.ASSIGNED, actor, "Assignment created");

        // Project the request: NEW/REWORK → ASSIGNED, atomically.
        serviceRequestService.projectStatus(actor, serviceRequestId,
                ServiceRequestStateMachine.ASSIGNED, saved.getId(), "Technician assigned");
        return saved;
    }

    // ─── Accept / Decline (technician-side; ops acts on behalf in MVP) ─

    @Transactional
    public Assignment accept(UUID actor, UUID id, Long version, String idemKey) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_ACCEPT", refFor(a));
        idempotency.register(idemKey, "POST /assignments/{id}/accept", actor);
        requireVersion(a, version);
        lifecycle.transition(a, AssignmentStateMachine.ACCEPTED, actor, null);
        a.setAcceptedAt(LocalDateTime.now());
        return assignmentRepo.save(a);
    }

    @Transactional
    public Assignment decline(UUID actor, UUID id, String reason, Long version, String idemKey) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_ACCEPT", refFor(a));
        idempotency.register(idemKey, "POST /assignments/{id}/decline", actor);
        requireVersion(a, version);
        lifecycle.transition(a, AssignmentStateMachine.DECLINED, actor, reason);
        a.setDeclinedAt(LocalDateTime.now());
        a.setDeclineReason(reason);
        a.setActive(false);
        Assignment saved = assignmentRepo.save(a);
        // No active assignment → request returns to NEW (dispatchable).
        serviceRequestService.projectStatus(actor, a.getServiceRequestId(),
                ServiceRequestStateMachine.NEW, null, "Assignment declined");
        return saved;
    }

    // ─── Execution milestones ────────────────────────────────────────

    @Transactional
    public Assignment enRoute(UUID actor, UUID id, Long version, String idemKey) {
        return execTransition(actor, id, AssignmentStateMachine.EN_ROUTE, version, idemKey, a -> a.setEnRouteAt(LocalDateTime.now()));
    }

    @Transactional
    public Assignment arrive(UUID actor, UUID id, Long version, String idemKey) {
        return execTransition(actor, id, AssignmentStateMachine.ARRIVED, version, idemKey, a -> a.setArrivedAt(LocalDateTime.now()));
    }

    @Transactional
    public Assignment start(UUID actor, UUID id, Long version, String idemKey) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_EXECUTE", refFor(a));
        idempotency.register(idemKey, "POST /assignments/{id}/start", actor);
        requireVersion(a, version);
        lifecycle.transition(a, AssignmentStateMachine.IN_PROGRESS, actor, null);
        a.setStartedAt(LocalDateTime.now());
        Assignment saved = assignmentRepo.save(a);
        // Request projection: ASSIGNED → IN_PROGRESS.
        serviceRequestService.projectStatus(actor, a.getServiceRequestId(),
                ServiceRequestStateMachine.IN_PROGRESS, saved.getId(), "Work started");
        return saved;
    }

    @Transactional
    public Assignment complete(UUID actor, UUID id, String notes, Long version, String idemKey) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_EXECUTE", refFor(a));
        idempotency.register(idemKey, "POST /assignments/{id}/complete", actor);
        requireVersion(a, version);
        lifecycle.transition(a, AssignmentStateMachine.COMPLETED, actor, notes);
        a.setCompletedAt(LocalDateTime.now());
        a.setCompletionNotes(notes);
        a.setActive(false);
        Assignment saved = assignmentRepo.save(a);
        // Request projection: IN_PROGRESS → COMPLETED.
        serviceRequestService.projectStatus(actor, a.getServiceRequestId(),
                ServiceRequestStateMachine.COMPLETED, saved.getId(), "Work completed");
        return saved;
    }

    private Assignment execTransition(UUID actor, UUID id, String to, Long version, String idemKey,
                                      java.util.function.Consumer<Assignment> stamp) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_EXECUTE", refFor(a));
        idempotency.register(idemKey, "POST /assignments/{id}/" + to, actor);
        requireVersion(a, version);
        lifecycle.transition(a, to, actor, null);
        stamp.accept(a);
        return assignmentRepo.save(a);
    }

    // ─── No-show ─────────────────────────────────────────────────────

    @Transactional
    public Assignment noShow(UUID actor, UUID id, String reason, Long version, String idemKey) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_EXECUTE", refFor(a));
        idempotency.register(idemKey, "POST /assignments/{id}/no-show", actor);
        requireVersion(a, version);
        lifecycle.transition(a, AssignmentStateMachine.NO_SHOW, actor, reason);
        a.setNoShowReason(reason);
        a.setActive(false);
        Assignment saved = assignmentRepo.save(a);
        // No active assignment → request returns to NEW (needs re-dispatch).
        serviceRequestService.projectStatus(actor, a.getServiceRequestId(),
                ServiceRequestStateMachine.NEW, null, "Technician no-show");
        return saved;
    }

    // ─── Cancel assignment ───────────────────────────────────────────

    @Transactional
    public Assignment cancel(UUID actor, UUID id, String reason, Long version, String idemKey) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_CANCEL", refFor(a));
        idempotency.register(idemKey, "POST /assignments/{id}/cancel", actor);
        requireVersion(a, version);
        lifecycle.transition(a, AssignmentStateMachine.CANCELLED, actor, reason);
        a.setCancelledAt(LocalDateTime.now());
        a.setCancelReason(reason);
        a.setActive(false);
        Assignment saved = assignmentRepo.save(a);
        // Cancelling the active assignment returns the request to NEW (dispatchable),
        // unless the request itself is already CANCELLED (then leave it as-is).
        ServiceRequest sr = serviceRequestService.loadForDispatch(a.getServiceRequestId());
        if (!ServiceRequestStateMachine.CANCELLED.equals(sr.getStatus())) {
            serviceRequestService.projectStatus(actor, a.getServiceRequestId(),
                    ServiceRequestStateMachine.NEW, null, "Assignment cancelled");
        }
        return saved;
    }

    // ─── Reschedule ──────────────────────────────────────────────────

    @Transactional
    public Assignment reschedule(UUID actor, UUID id, RescheduleRequest req, Long version, String idemKey) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_RESCHEDULE", refFor(a));
        idempotency.register(idemKey, "POST /assignments/{id}/reschedule", actor);
        requireVersion(a, version);
        if (AssignmentStateMachine.isTerminal(a.getStatus())) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot reschedule a " + a.getStatus() + " assignment", HttpStatus.CONFLICT);
        }
        if (AssignmentStateMachine.COMPLETED.equals(a.getStatus())) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot reschedule a completed assignment", HttpStatus.CONFLICT);
        }
        validateSchedule(req.scheduledStart(), req.scheduledEnd());
        a.setScheduledStart(req.scheduledStart());
        a.setScheduledEnd(req.scheduledEnd());
        Assignment saved = assignmentRepo.save(a);
        lifecycle.record(saved.getId(), a.getStatus(), a.getStatus(), actor, "Rescheduled");
        return saved;
    }

    // ─── Reassign (history-preserving) ───────────────────────────────

    @Transactional
    public Assignment reassign(UUID actor, UUID id, ReassignRequest req, Long version, String idemKey) {
        Assignment old = load(id);
        authz.authorize(actor, "ASSIGNMENT_REASSIGN", refFor(old));
        idempotency.register(idemKey, "POST /assignments/{id}/reassign", actor);
        requireVersion(old, version);
        if (AssignmentStateMachine.isTerminal(old.getStatus())) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot reassign a " + old.getStatus() + " assignment", HttpStatus.CONFLICT);
        }
        ServiceRequest sr = serviceRequestService.loadForDispatch(old.getServiceRequestId());
        validateSchedule(req.scheduledStart(), req.scheduledEnd());
        Technician t = requireEligibleTechnician(sr, req.technicianId());

        // Close the previous assignment (preserve history; NEVER rewrite it).
        lifecycle.transition(old, AssignmentStateMachine.CANCELLED, actor,
                req.reason() == null ? "Reassigned" : req.reason());
        old.setCancelledAt(LocalDateTime.now());
        old.setCancelReason(req.reason() == null ? "Reassigned to another technician" : req.reason());
        old.setActive(false);
        assignmentRepo.save(old);

        // Create the new assignment linked back to the old one.
        Assignment next = new Assignment();
        next.setReferenceNo(generateReference());
        next.setServiceRequestId(sr.getId());
        next.setTechnicianId(t.getId());
        next.setHelperId(req.helperId());
        next.setVendorId(t.getVendorId());
        next.setStatus(AssignmentStateMachine.ASSIGNED);
        next.setActive(true);
        next.setScheduledStart(req.scheduledStart());
        next.setScheduledEnd(req.scheduledEnd());
        next.setAssignedAt(LocalDateTime.now());
        next.setAssignedBy(actor);
        next.setPreviousAssignmentId(old.getId());
        Assignment saved = assignmentRepo.save(next);
        lifecycle.record(saved.getId(), null, AssignmentStateMachine.ASSIGNED, actor,
                "Reassigned from " + old.getReferenceNo());

        // Request stays ASSIGNED (or moves NEW→ASSIGNED); active-assignment pointer updated.
        String target = ServiceRequestStateMachine.ASSIGNED;
        serviceRequestService.projectStatus(actor, sr.getId(), target, saved.getId(), "Reassigned");
        return saved;
    }

    // ─── Rework ──────────────────────────────────────────────────────

    @Transactional
    public Assignment rework(UUID actor, UUID id, ReassignRequest req, Long version, String idemKey) {
        Assignment completed = load(id);
        authz.authorize(actor, "ASSIGNMENT_REASSIGN", refFor(completed));
        idempotency.register(idemKey, "POST /assignments/{id}/rework", actor);
        requireVersion(completed, version);
        // Only a COMPLETED assignment can be reworked.
        lifecycle.transition(completed, AssignmentStateMachine.REWORK, actor,
                req.reason() == null ? "Rework required" : req.reason());
        completed.setReworkReason(req.reason() == null ? "Rework required" : req.reason());
        completed.setActive(false);
        assignmentRepo.save(completed);

        ServiceRequest sr = serviceRequestService.loadForDispatch(completed.getServiceRequestId());
        // Request COMPLETED → REWORK.
        serviceRequestService.projectStatus(actor, sr.getId(),
                ServiceRequestStateMachine.REWORK, null, "Rework requested");

        // Create the follow-up assignment (REWORK → ASSIGNED on the request).
        Technician t = requireEligibleTechnician(sr, req.technicianId());
        validateSchedule(req.scheduledStart(), req.scheduledEnd());
        Assignment next = new Assignment();
        next.setReferenceNo(generateReference());
        next.setServiceRequestId(sr.getId());
        next.setTechnicianId(t.getId());
        next.setHelperId(req.helperId());
        next.setVendorId(t.getVendorId());
        next.setStatus(AssignmentStateMachine.ASSIGNED);
        next.setActive(true);
        next.setScheduledStart(req.scheduledStart());
        next.setScheduledEnd(req.scheduledEnd());
        next.setAssignedAt(LocalDateTime.now());
        next.setAssignedBy(actor);
        next.setPreviousAssignmentId(completed.getId());
        Assignment saved = assignmentRepo.save(next);
        lifecycle.record(saved.getId(), null, AssignmentStateMachine.ASSIGNED, actor,
                "Rework assignment from " + completed.getReferenceNo());
        serviceRequestService.projectStatus(actor, sr.getId(),
                ServiceRequestStateMachine.ASSIGNED, saved.getId(), "Rework assigned");
        return saved;
    }

    // ─── Reads ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Assignment get(UUID actor, UUID id) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_VIEW", refFor(a));
        return a;
    }

    @Transactional(readOnly = true)
    public List<Assignment> listForRequest(UUID actor, UUID serviceRequestId) {
        ServiceRequest sr = serviceRequestService.loadForDispatch(serviceRequestId);
        authz.authorize(actor, "ASSIGNMENT_VIEW", refForRequest(sr));
        return assignmentRepo.findByServiceRequestIdAndDeletedFalseOrderByCreatedAtAsc(serviceRequestId);
    }

    @Transactional(readOnly = true)
    public List<com.manafy.ops.dispatch.entity.AssignmentStatusHistory> history(UUID actor, UUID id) {
        Assignment a = load(id);
        authz.authorize(actor, "ASSIGNMENT_VIEW", refFor(a));
        return lifecycle.history(id);
    }

    // ─── Eligible technicians ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EligibleTechnicianResponse> eligibleTechnicians(UUID actor, UUID serviceRequestId) {
        ServiceRequest sr = serviceRequestService.loadForDispatch(serviceRequestId);
        authz.authorize(actor, "ASSIGNMENT_VIEW", refForRequest(sr));
        return eligibility.eligibleTechnicians(sr);
    }

    // ─── Field visit (created on scheduling/start) ───────────────────

    @Transactional
    public FieldVisit createVisit(UUID actor, UUID assignmentId, LocalDateTime start, LocalDateTime end) {
        Assignment a = load(assignmentId);
        authz.authorize(actor, "FIELD_VISIT_CREATE", refFor(a));
        ServiceRequest sr = serviceRequestService.loadForDispatch(a.getServiceRequestId());
        validateSchedule(start, end);
        FieldVisit v = new FieldVisit();
        v.setAssignmentId(a.getId());
        v.setServiceRequestId(sr.getId());
        v.setTechnicianId(a.getTechnicianId());
        v.setApartmentId(sr.getApartmentId());
        v.setScheduledStart(start);
        v.setScheduledEnd(end);
        v.setStatus("SCHEDULED");
        FieldVisit saved = fieldVisitRepo.save(v);
        audit.audit(actor, primaryRole(actor), "FIELD_VISIT_CREATED", "FIELD_VISIT", saved.getId(),
                null, "SCHEDULED", "Field visit scheduled");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FieldVisit> visitsForAssignment(UUID actor, UUID assignmentId) {
        Assignment a = load(assignmentId);
        authz.authorize(actor, "FIELD_VISIT_VIEW", refFor(a));
        return fieldVisitRepo.findByAssignmentIdAndDeletedFalse(assignmentId);
    }
}
