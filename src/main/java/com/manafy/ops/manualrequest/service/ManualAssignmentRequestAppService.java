package com.manafy.ops.manualrequest.service;

import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.*;
import com.manafy.ops.manualrequest.domain.ManualAssignmentStateMachine;
import com.manafy.ops.manualrequest.dto.ManualAssignmentDtos.*;
import com.manafy.ops.manualrequest.entity.ManualAssignmentRequest;
import com.manafy.ops.manualrequest.repository.ManualAssignmentRequestRepository;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.entity.Technician;
import com.manafy.ops.workforce.repository.HelperRepository;
import com.manafy.ops.workforce.repository.TechnicianRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for the manual (Field-Officer-coordinated) fulfillment
 * workflow. This is deliberately SEPARATE from the technician dispatch path:
 * it never touches service_request / assignment / field_visit and never runs
 * TechnicianEligibilityService.
 *
 * Reuses the shared infrastructure exactly like ServiceRequestAppService:
 * permission gate + area scope (AREA_RESPONSIBLE) + idempotency + audit +
 * optimistic version + response envelope + a validated state machine.
 *
 * Assignment is MANUAL: an FO records an opaque assignee (Community helper/vendor
 * ref, Ops workforce member, or external person). Availability is validated only
 * when the assignee is an Ops workforce member (OPS_WORKFORCE) — a maid/cook
 * request does NOT require technician skill/certification.
 */
@Service
public class ManualAssignmentRequestAppService {

    private static final String SOURCE_SYSTEM_COMMUNITY = "COMMUNITY";
    private static final Set<String> ASSIGNEE_TYPES =
            Set.of("EXTERNAL_PERSON", "COMMUNITY_HELPER_REF", "COMMUNITY_VENDOR_REF", "OPS_WORKFORCE");
    private static final Set<String> FREQUENCIES = Set.of("ONE_TIME", "DAILY", "WEEKLY", "MONTHLY");
    private static final Set<String> TIME_SLOTS = Set.of("MORNING", "AFTERNOON", "EVENING", "FULL_DAY");

    private final ManualAssignmentRequestRepository repo;
    private final AuthorizationService authz;
    private final ScopeService scopeService;
    private final ResourceScopeResolver resolver;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final IdempotencyService idempotency;
    private final CommunityCallbackService communityCallback;
    private final TechnicianRepository technicianRepo;
    private final HelperRepository helperRepo;
    private final HelperAssignmentService helperAssignment;

    /** Service types that are auto-assigned on intake (lowest-daily-workload maid). */
    private static final Set<String> AUTO_ASSIGN_TYPES = Set.of("MAID");

    public ManualAssignmentRequestAppService(ManualAssignmentRequestRepository repo,
                                             AuthorizationService authz, ScopeService scopeService,
                                             ResourceScopeResolver resolver, PermissionService permissionService,
                                             AuditService audit, IdempotencyService idempotency,
                                             CommunityCallbackService communityCallback,
                                             TechnicianRepository technicianRepo, HelperRepository helperRepo,
                                             HelperAssignmentService helperAssignment) {
        this.repo = repo;
        this.authz = authz;
        this.scopeService = scopeService;
        this.resolver = resolver;
        this.permissionService = permissionService;
        this.audit = audit;
        this.idempotency = idempotency;
        this.communityCallback = communityCallback;
        this.technicianRepo = technicianRepo;
        this.helperRepo = helperRepo;
        this.helperAssignment = helperAssignment;
    }

    // ─── helpers ─────────────────────────────────────────────────────

    private ManualAssignmentRequest load(UUID id) {
        return repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Manual request not found"));
    }

    /** Server-resolved scope ref. area/region are the request's persisted anchors (may be null). */
    private ResourceRef ref(ManualAssignmentRequest r) {
        // Reuse the assignment resolver shape (apartment/area/region anchors). We do
        // not have an Ops apartment id here (Community apartment is a foreign ref),
        // so only area/region anchor scope; a null-area request is GLOBAL-only.
        return resolver.assignment(r.getId(), null, r.getAreaId(), r.getRegionId());
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private void requireVersion(ManualAssignmentRequest r, Long expected) {
        if (expected != null && expected != r.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT",
                    "Manual request was modified concurrently. Reload and retry.", HttpStatus.CONFLICT);
        }
    }

    private String generateReference() {
        return "MR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase().replace("-", "");
    }

    private String upper(String s, Set<String> allowed, String field) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().toUpperCase();
        if (!allowed.contains(v)) throw BusinessException.validation("Invalid " + field + ": " + s);
        return v;
    }

    // ─── INTAKE (service-to-service; authenticated by shared token upstream) ──

    /**
     * Idempotent intake of a Community request. Called only after the service-token
     * filter has authenticated the caller (no OpsUser actor). Two-layer dedupe:
     * the Idempotency-Key header AND the unique (source_system, source_type,
     * source_id) constraint — a retry returns the existing record, never a duplicate.
     */
    @Transactional
    public ManualAssignmentRequest intake(IntakeRequest req, String idemKey) {
        // Second-layer dedupe backstop (DB unique triple) before the idempotency claim.
        var existing = repo.findBySourceSystemAndSourceTypeAndSourceIdAndDeletedFalse(
                req.sourceSystem(), req.sourceType(), req.sourceId());
        if (existing.isPresent()) {
            return existing.get(); // idempotent: same source -> same record
        }
        // Idempotency-Key guard (system actor for intake).
        idempotency.register(idemKey, "POST /manual-requests/intake", null);

        ManualAssignmentRequest r = new ManualAssignmentRequest();
        r.setReferenceNo(generateReference());
        r.setSourceSystem(req.sourceSystem());
        r.setSourceType(req.sourceType());
        r.setSourceId(req.sourceId());
        r.setCommunityCustomerId(req.communityCustomerId());
        r.setCommunityApartmentId(req.communityApartmentId());
        r.setCommunityFlatId(req.communityFlatId());
        r.setServiceType(req.serviceType());
        r.setServiceTitle(req.serviceTitle());
        r.setFrequency(upper(req.frequency(), FREQUENCIES, "frequency"));
        if (req.startDate() != null && !req.startDate().isBlank()) {
            try { r.setStartDate(LocalDate.parse(req.startDate())); }
            catch (Exception e) { throw BusinessException.validation("startDate must be YYYY-MM-DD"); }
        }
        r.setTimeSlot(upper(req.timeSlot(), TIME_SLOTS, "timeSlot"));
        r.setServiceAddress(req.serviceAddress());
        r.setResidentName(req.residentName());
        r.setContactNumber(req.contactNumber());
        r.setNotes(req.notes());
        if (req.amount() != null && !req.amount().isBlank()) {
            try { r.setAmount(new BigDecimal(req.amount())); } catch (NumberFormatException ignored) {}
        }
        r.setPaymentStatus(req.paymentStatus() != null ? req.paymentStatus() : "NONE");
        // No Ops apartment->area mapping yet, so area/region stay null (GLOBAL-visible).
        r.setStatus(ManualAssignmentStateMachine.QUEUED); // straight onto the FO queue
        ManualAssignmentRequest saved = repo.save(r);

        audit.audit(null, "SYSTEM", "MANUAL_REQUEST_INTAKE", "MANUAL_ASSIGNMENT_REQUEST", saved.getId(),
                null, "QUEUED", "Intake from " + req.sourceSystem() + ":" + req.sourceType() + ":" + req.sourceId());
        audit.activity("MANUAL_ASSIGNMENT_REQUEST", saved.getId(), null, "INTAKE",
                "Manual request received from Community");

        // AUTOMATIC ASSIGNMENT for auto-assignable services (MAID): pick the
        // lowest-daily-workload available eligible helper and assign transactionally.
        // Non-auto services (plumber/cook/etc.) stay QUEUED for manual assignment.
        boolean autoAssigned = tryAutoAssign(saved);

        // Reflect the resulting status back to Community so the resident sees either
        // "Helper assigned" (auto) or "Finding a helper" (queued for manual).
        communityCallback.pushStatus(saved);
        return saved;
    }

    /**
     * Attempt automatic assignment for an auto-assignable service. Returns true if
     * a helper was assigned. Runs inside the intake transaction so the request and
     * its assignment are persisted atomically (no partially-assigned request).
     */
    private boolean tryAutoAssign(ManualAssignmentRequest r) {
        String type = r.getServiceType() == null ? null : r.getServiceType().trim().toUpperCase();
        if (type == null || !AUTO_ASSIGN_TYPES.contains(type)) return false;

        HelperAssignmentService.RankedHelper best = helperAssignment.selectForAuto(type, r.getStartDate());
        if (best == null) {
            // No available maid today — leave QUEUED for a Field Officer to handle.
            audit.activity("MANUAL_ASSIGNMENT_REQUEST", r.getId(), null, "AUTO_ASSIGN_SKIPPED",
                    "No available " + type + " for automatic assignment; left on queue");
            return false;
        }
        var helper = best.helper();
        String from = r.getStatus();
        ManualAssignmentStateMachine.requireTransition(from, ManualAssignmentStateMachine.ASSIGNED);
        r.setStatus(ManualAssignmentStateMachine.ASSIGNED);
        r.setAssigneeType("OPS_WORKFORCE");
        r.setAssigneeRef(helper.getId().toString());
        r.setAssigneeName(helper.getName());
        r.setAssigneePhone(helper.getPhone());
        repo.save(r);
        audit.audit(null, "SYSTEM", "MANUAL_REQUEST_AUTO_ASSIGNED", "MANUAL_ASSIGNMENT_REQUEST", r.getId(),
                from, "ASSIGNED", "Auto-assigned to " + helper.getName()
                        + " (today's workload before assign=" + best.todaysAssignments() + ")");
        return true;
    }

    // ─── OPS ACTIONS (permission + area scope) ───────────────────────

    @Transactional(readOnly = true)
    public ManualAssignmentRequest get(UUID actor, UUID id) {
        ManualAssignmentRequest r = load(id);
        authz.authorize(actor, "ASSIGNMENT_VIEW", ref(r), RelationshipPredicate.AREA_RESPONSIBLE);
        return r;
    }

    @Transactional
    public ManualAssignmentRequest ack(UUID actor, UUID id, Long version, String idemKey) {
        ManualAssignmentRequest r = load(id);
        authz.authorize(actor, "ASSIGNMENT_VIEW", ref(r), RelationshipPredicate.AREA_RESPONSIBLE);
        idempotency.register(idemKey, "POST /manual-requests/{id}/ack", actor);
        requireVersion(r, version);
        String from = r.getStatus();
        ManualAssignmentStateMachine.requireTransition(from, ManualAssignmentStateMachine.ACKED_BY_FO);
        r.setStatus(ManualAssignmentStateMachine.ACKED_BY_FO);
        r.setFieldOfficerId(actor);
        ManualAssignmentRequest saved = repo.save(r);
        audit.audit(actor, primaryRole(actor), "MANUAL_REQUEST_ACKED", "MANUAL_ASSIGNMENT_REQUEST",
                saved.getId(), from, "ACKED_BY_FO", null);
        communityCallback.pushStatus(saved);
        return saved;
    }

    @Transactional
    public ManualAssignmentRequest assign(UUID actor, UUID id, AssignRequest req, Long version, String idemKey) {
        ManualAssignmentRequest r = load(id);
        authz.authorize(actor, "ASSIGNMENT_CREATE", ref(r), RelationshipPredicate.AREA_RESPONSIBLE);
        idempotency.register(idemKey, "POST /manual-requests/{id}/assign", actor);
        requireVersion(r, version);

        String assigneeType = upper(req.assigneeType(), ASSIGNEE_TYPES, "assigneeType");
        if (assigneeType == null) throw BusinessException.validation("assigneeType is required");
        if (req.assigneeName() == null || req.assigneeName().isBlank()) {
            throw BusinessException.validation("assigneeName is required");
        }
        // Availability is validated ONLY for Ops workforce assignees — never
        // technician skill/certification/eligibility (this is manual fulfillment).
        if ("OPS_WORKFORCE".equals(assigneeType)) {
            validateOpsWorkforceAvailable(req.assigneeRef());
        }

        String from = r.getStatus();
        ManualAssignmentStateMachine.requireTransition(from, ManualAssignmentStateMachine.ASSIGNED);
        r.setStatus(ManualAssignmentStateMachine.ASSIGNED);
        if (r.getFieldOfficerId() == null) r.setFieldOfficerId(actor);
        r.setAssigneeType(assigneeType);
        r.setAssigneeRef(req.assigneeRef());
        r.setAssigneeName(req.assigneeName().trim());
        r.setAssigneePhone(req.assigneePhone());
        ManualAssignmentRequest saved = repo.save(r);
        audit.audit(actor, primaryRole(actor), "MANUAL_REQUEST_ASSIGNED", "MANUAL_ASSIGNMENT_REQUEST",
                saved.getId(), from, "ASSIGNED", "Assigned to " + assigneeType + ": " + req.assigneeName());
        communityCallback.pushStatus(saved);
        return saved;
    }

    /** Reassign to a different person; stays in ASSIGNED, history preserved via audit. */
    @Transactional
    public ManualAssignmentRequest reassign(UUID actor, UUID id, AssignRequest req, Long version, String idemKey) {
        ManualAssignmentRequest r = load(id);
        authz.authorize(actor, "ASSIGNMENT_REASSIGN", ref(r), RelationshipPredicate.AREA_RESPONSIBLE);
        idempotency.register(idemKey, "POST /manual-requests/{id}/reassign", actor);
        requireVersion(r, version);
        if (!ManualAssignmentStateMachine.ASSIGNED.equals(r.getStatus())
                && !ManualAssignmentStateMachine.IN_PROGRESS.equals(r.getStatus())) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Can only reassign an ASSIGNED or IN_PROGRESS request", HttpStatus.CONFLICT);
        }
        String assigneeType = upper(req.assigneeType(), ASSIGNEE_TYPES, "assigneeType");
        if (assigneeType == null || req.assigneeName() == null || req.assigneeName().isBlank()) {
            throw BusinessException.validation("assigneeType and assigneeName are required");
        }
        if ("OPS_WORKFORCE".equals(assigneeType)) {
            validateOpsWorkforceAvailable(req.assigneeRef());
        }
        String prev = r.getAssigneeName();
        r.setStatus(ManualAssignmentStateMachine.ASSIGNED);
        r.setAssigneeType(assigneeType);
        r.setAssigneeRef(req.assigneeRef());
        r.setAssigneeName(req.assigneeName().trim());
        r.setAssigneePhone(req.assigneePhone());
        ManualAssignmentRequest saved = repo.save(r);
        audit.audit(actor, primaryRole(actor), "MANUAL_REQUEST_REASSIGNED", "MANUAL_ASSIGNMENT_REQUEST",
                saved.getId(), prev, req.assigneeName(), "Reassigned");
        communityCallback.pushStatus(saved);
        return saved;
    }

    @Transactional
    public ManualAssignmentRequest start(UUID actor, UUID id, Long version, String idemKey) {
        ManualAssignmentRequest r = load(id);
        authz.authorize(actor, "ASSIGNMENT_CREATE", ref(r), RelationshipPredicate.AREA_RESPONSIBLE);
        idempotency.register(idemKey, "POST /manual-requests/{id}/start", actor);
        requireVersion(r, version);
        String from = r.getStatus();
        ManualAssignmentStateMachine.requireTransition(from, ManualAssignmentStateMachine.IN_PROGRESS);
        r.setStatus(ManualAssignmentStateMachine.IN_PROGRESS);
        ManualAssignmentRequest saved = repo.save(r);
        audit.audit(actor, primaryRole(actor), "MANUAL_REQUEST_STARTED", "MANUAL_ASSIGNMENT_REQUEST",
                saved.getId(), from, "IN_PROGRESS", null);
        communityCallback.pushStatus(saved);
        return saved;
    }

    @Transactional
    public ManualAssignmentRequest complete(UUID actor, UUID id, String notes, Long version, String idemKey) {
        ManualAssignmentRequest r = load(id);
        authz.authorize(actor, "ASSIGNMENT_CREATE", ref(r), RelationshipPredicate.AREA_RESPONSIBLE);
        idempotency.register(idemKey, "POST /manual-requests/{id}/complete", actor);
        requireVersion(r, version);
        String from = r.getStatus();
        ManualAssignmentStateMachine.requireTransition(from, ManualAssignmentStateMachine.COMPLETED);
        r.setStatus(ManualAssignmentStateMachine.COMPLETED);
        r.setCompletionNotes(notes);
        ManualAssignmentRequest saved = repo.save(r);
        audit.audit(actor, primaryRole(actor), "MANUAL_REQUEST_COMPLETED", "MANUAL_ASSIGNMENT_REQUEST",
                saved.getId(), from, "COMPLETED", notes);
        communityCallback.pushStatus(saved);
        return saved;
    }

    @Transactional
    public ManualAssignmentRequest cancel(UUID actor, UUID id, String reason, Long version, String idemKey) {
        ManualAssignmentRequest r = load(id);
        authz.authorize(actor, "ASSIGNMENT_CANCEL", ref(r), RelationshipPredicate.AREA_RESPONSIBLE);
        idempotency.register(idemKey, "POST /manual-requests/{id}/cancel", actor);
        requireVersion(r, version);
        String from = r.getStatus();
        ManualAssignmentStateMachine.requireTransition(from, ManualAssignmentStateMachine.CANCELLED);
        r.setStatus(ManualAssignmentStateMachine.CANCELLED);
        r.setCancelReason(reason);
        ManualAssignmentRequest saved = repo.save(r);
        audit.audit(actor, primaryRole(actor), "MANUAL_REQUEST_CANCELLED", "MANUAL_ASSIGNMENT_REQUEST",
                saved.getId(), from, "CANCELLED", reason);
        communityCallback.pushStatus(saved);
        return saved;
    }

    /** Manual-fulfillment availability check: reject an Ops workforce member that is not available. */
    private void validateOpsWorkforceAvailable(String assigneeRef) {
        if (assigneeRef == null || assigneeRef.isBlank()) {
            throw BusinessException.validation("assigneeRef (workforce id) is required for OPS_WORKFORCE");
        }
        UUID refId;
        try { refId = UUID.fromString(assigneeRef); }
        catch (Exception e) { throw BusinessException.validation("assigneeRef must be a valid workforce id"); }

        // A helper (maid/cook) must be ACTIVE; a technician must be ACTIVE + AVAILABLE.
        var helper = helperRepo.findByIdAndDeletedFalse(refId);
        if (helper.isPresent()) {
            if (!"ACTIVE".equals(helper.get().getStatus())) {
                throw new BusinessException("ASSIGNEE_UNAVAILABLE",
                        "Selected helper is not active", HttpStatus.CONFLICT);
            }
            return;
        }
        var tech = technicianRepo.findByIdAndDeletedFalse(refId);
        if (tech.isPresent()) {
            Technician t = tech.get();
            if (!"ACTIVE".equals(t.getStatus()) || !"AVAILABLE".equals(t.getAvailabilityStatus())) {
                throw new BusinessException("ASSIGNEE_UNAVAILABLE",
                        "Selected workforce member is not available", HttpStatus.CONFLICT);
            }
            return;
        }
        throw BusinessException.validation("No Ops workforce member for assigneeRef: " + assigneeRef);
    }

    // ─── LIST (scoped) + CANDIDATES ──────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ManualRequestListItem> list(UUID actor, Integer page, Integer pageSize, String status) {
        authz.requirePermission(actor, "ASSIGNMENT_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        boolean global = scopeService.hasGlobal(actor);
        Set<UUID> visibleAreas = scopeService.visibleAreaIds(actor);
        String statusFilter = status == null ? null : status.trim().toUpperCase();

        var all = repo.findByDeletedFalse(PageRequest.of(0, Integer.MAX_VALUE)).getContent().stream()
                // Scope: GLOBAL sees all; area-scoped FOs see requests in their areas.
                // A request with no resolved area is visible to GLOBAL operators only.
                .filter(r -> global || (r.getAreaId() != null && visibleAreas.contains(r.getAreaId())))
                .filter(r -> statusFilter == null || statusFilter.equals(r.getStatus()))
                .sorted((x, y) -> {
                    LocalDateTime xc = x.getCreatedAt(), yc = y.getCreatedAt();
                    int cmp = (xc == null || yc == null) ? 0 : yc.compareTo(xc);
                    return cmp != 0 ? cmp : x.getReferenceNo().compareTo(y.getReferenceNo());
                })
                .toList();
        long total = all.size();
        int from = Math.min((p - 1) * ps, all.size());
        int to = Math.min(from + ps, all.size());
        List<ManualRequestListItem> data = all.subList(from, to).stream()
                .map(r -> new ManualRequestListItem(r.getId(), r.getReferenceNo(), r.getStatus(),
                        r.getServiceType(), r.getFrequency(), r.getStartDate(), r.getTimeSlot(),
                        r.getCommunityApartmentId(), r.getAreaId(), r.getResidentName(),
                        r.getAssigneeName(), r.getCreatedAt()))
                .toList();
        return PageResponse.of(data, p, ps, total);
    }

    /**
     * Candidate assignable people for the FO picker, from REAL Ops workforce data,
     * ORDERED BY TODAY'S WORKLOAD (least-loaded first). Requires ASSIGNMENT_VIEW.
     *
     * When {@code requestId} is provided, candidates are the helpers ELIGIBLE for
     * that request's service category (active + available + matching category),
     * ranked by the shared {@link HelperAssignmentService} so the ordering is
     * identical to automatic assignment. When absent, all active + available
     * helpers are returned, each annotated with today's workload and ordered by it.
     * The FO may always also enter an external person / Community ref directly.
     */
    @Transactional(readOnly = true)
    public List<AssignableCandidate> assignableCandidates(UUID actor, UUID requestId) {
        authz.requirePermission(actor, "ASSIGNMENT_VIEW");

        if (requestId != null) {
            ManualAssignmentRequest r = load(requestId);
            // Authorize scope on the specific request the FO is assigning.
            authz.authorize(actor, "ASSIGNMENT_VIEW", ref(r), RelationshipPredicate.AREA_RESPONSIBLE);
            LocalDate day = r.getStartDate();
            return helperAssignment.rankedEligible(r.getServiceType(), day).stream()
                    .map(rh -> new AssignableCandidate(
                            "OPS_WORKFORCE", rh.helper().getId().toString(),
                            rh.helper().getName(), rh.helper().getPhone(),
                            "HELPER", rh.helper().getAvailabilityStatus(),
                            rh.helper().getCategory(), rh.todaysAssignments()))
                    .toList();
        }

        // No request context: all active + available helpers, workload-ordered (today).
        LocalDate today = LocalDate.now();
        List<AssignableCandidate> out = new ArrayList<>();
        for (Helper h : helperRepo.findByStatusAndAvailabilityStatusAndDeletedFalse("ACTIVE", "AVAILABLE")) {
            long load = repo.countDailyWorkload(h.getId().toString(), today);
            out.add(new AssignableCandidate("OPS_WORKFORCE", h.getId().toString(),
                    h.getName(), h.getPhone(), "HELPER", h.getAvailabilityStatus(),
                    h.getCategory(), load));
        }
        out.sort(Comparator.comparingLong(AssignableCandidate::todaysAssignments)
                .thenComparing(c -> c.name() == null ? "" : c.name())
                .thenComparing(AssignableCandidate::assigneeRef));
        return out;
    }
}
