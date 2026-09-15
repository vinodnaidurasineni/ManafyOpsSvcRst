package com.manafy.ops.servicerequest.service;

import com.manafy.ops.apartment.entity.Apartment;
import com.manafy.ops.apartment.repository.ApartmentRepository;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.*;
import com.manafy.ops.servicerequest.domain.ServiceRequestStateMachine;
import com.manafy.ops.servicerequest.dto.ServiceRequestDtos.*;
import com.manafy.ops.servicerequest.entity.ServiceRequest;
import com.manafy.ops.servicerequest.repository.ServiceRequestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for Service Request CRUD + limited lifecycle (Phase 4A).
 * Holds all business rules; the controller is thin. Authorization (permission +
 * scope), audit, idempotency, optimistic concurrency and the response envelope all
 * reuse the existing Phase 1 infrastructure — none of it is re-implemented.
 *
 * Scope anchors (apartment/area/region) are persisted on the request and resolved
 * server-side via {@link ResourceScopeResolver#serviceRequest}, so a caller cannot
 * reach another request by changing the UUID (IDOR-safe, same model as apartments).
 */
@Service
public class ServiceRequestAppService {

    private static final Set<String> CATEGORIES =
            Set.of("PLUMBING", "ELECTRICAL", "HVAC", "CLEANING", "SECURITY", "GENERAL", "OTHER");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private final ServiceRequestRepository srRepo;
    private final ApartmentRepository apartmentRepo;
    private final AuthorizationService authz;
    private final ScopeService scopeService;
    private final ResourceScopeResolver resolver;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final IdempotencyService idempotency;

    public ServiceRequestAppService(ServiceRequestRepository srRepo, ApartmentRepository apartmentRepo,
                                    AuthorizationService authz, ScopeService scopeService,
                                    ResourceScopeResolver resolver, PermissionService permissionService,
                                    AuditService audit, IdempotencyService idempotency) {
        this.srRepo = srRepo;
        this.apartmentRepo = apartmentRepo;
        this.authz = authz;
        this.scopeService = scopeService;
        this.resolver = resolver;
        this.permissionService = permissionService;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    // ─── helpers ─────────────────────────────────────────────────────

    private ServiceRequest load(UUID id) {
        return srRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Service request not found"));
    }

    /** Server-resolved ResourceRef for a service request (IDOR-safe anchors). */
    private ResourceRef ref(ServiceRequest sr) {
        return resolver.serviceRequest(sr.getId(), sr.getApartmentId(), sr.getAreaId(), sr.getRegionId());
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private void requireVersion(ServiceRequest sr, Long expected) {
        if (expected != null && expected != sr.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT",
                    "Service request was modified concurrently. Reload and retry.", HttpStatus.CONFLICT);
        }
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) return "MEDIUM";
        String p = priority.trim().toUpperCase();
        if (!PRIORITIES.contains(p)) throw BusinessException.validation("Invalid priority: " + priority);
        return p;
    }

    private String normalizeCategory(String category) {
        String c = category.trim().toUpperCase();
        if (!CATEGORIES.contains(c)) throw BusinessException.validation("Invalid category: " + category);
        return c;
    }

    private String generateReference() {
        return "SR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase().replace("-", "");
    }

    // ─── CRUD + lifecycle ────────────────────────────────────────────

    @Transactional
    public ServiceRequest create(UUID actor, CreateRequest req, String idemKey) {
        // Permission gate first, then idempotency claim (matches apartment convention).
        authz.requirePermission(actor, "SERVICE_REQUEST_CREATE");
        idempotency.register(idemKey, "POST /service-requests", actor);

        // Resolve the apartment (existence) and derive scope anchors server-side.
        Apartment apartment = apartmentRepo.findByIdAndDeletedFalse(req.apartmentId())
                .orElseThrow(() -> BusinessException.validation("Apartment not found: " + req.apartmentId()));

        // Scope gate: the actor must be authorized to CREATE against THIS apartment's
        // area/region (server-resolved). Prevents raising a request against an
        // apartment outside the caller's scope.
        authz.authorize(actor, "SERVICE_REQUEST_CREATE",
                resolver.serviceRequest(null, apartment.getId(), apartment.getAreaId(), apartment.getRegionId()));

        String category = normalizeCategory(req.category());
        String priority = normalizePriority(req.priority());

        ServiceRequest sr = new ServiceRequest();
        sr.setReferenceNo(generateReference());
        sr.setApartmentId(apartment.getId());
        sr.setAreaId(apartment.getAreaId());
        sr.setRegionId(apartment.getRegionId());
        sr.setRequesterUserId(actor);
        sr.setCategory(category);
        sr.setPriority(priority);
        sr.setDescription(req.description());
        sr.setStatus(ServiceRequestStateMachine.NEW);
        ServiceRequest saved = srRepo.save(sr);

        audit.audit(actor, primaryRole(actor), "SERVICE_REQUEST_CREATED", "SERVICE_REQUEST", saved.getId(),
                null, "NEW", "Service request created: " + saved.getReferenceNo());
        audit.activity("SERVICE_REQUEST", saved.getId(), actor, "CREATED", "Service request created");
        return saved;
    }

    @Transactional(readOnly = true)
    public ServiceRequest get(UUID actor, UUID id) {
        ServiceRequest sr = load(id);
        authz.authorize(actor, "SERVICE_REQUEST_VIEW", ref(sr));
        return sr;
    }

    @Transactional
    public ServiceRequest cancel(UUID actor, UUID id, String reason, Long expectedVersion, String idemKey) {
        ServiceRequest sr = load(id);
        authz.authorize(actor, "SERVICE_REQUEST_CANCEL", ref(sr));
        idempotency.register(idemKey, "POST /service-requests/{id}/cancel", actor);
        requireVersion(sr, expectedVersion);
        // Explicit, validated transition — no arbitrary status write.
        ServiceRequestStateMachine.requireTransition(sr.getStatus(), ServiceRequestStateMachine.CANCELLED);
        String from = sr.getStatus();
        sr.setStatus(ServiceRequestStateMachine.CANCELLED);
        sr.setCancelledAt(LocalDateTime.now());
        sr.setCancelReason(reason);
        ServiceRequest saved = srRepo.save(sr);
        audit.audit(actor, primaryRole(actor), "SERVICE_REQUEST_CANCELLED", "SERVICE_REQUEST", saved.getId(),
                from, "CANCELLED", reason);
        audit.activity("SERVICE_REQUEST", saved.getId(), actor, "CANCELLED", "Service request cancelled");
        return saved;
    }

    // ─── Phase 4B: assignment-driven projection (DD-36) ──────────────
    //
    // The request status is a PROJECTION of assignment activity. These methods are
    // called by the dispatch layer WITHIN the same transaction as the assignment
    // mutation, so the request and assignment states cannot drift. Authorization is
    // already enforced on the assignment action by the dispatch service; these do not
    // re-open a second authorization decision (the SR was resolved via the same
    // parent). Each projection validates the SR transition (state machine) and audits.

    /**
     * Project a service request onto a new status because of assignment activity.
     * Validates the transition; a no-op transition (from == to) is allowed silently
     * so idempotent replays don't fail. Updates the active-assignment pointer.
     */
    @Transactional
    public void projectStatus(UUID actor, UUID serviceRequestId, String toStatus,
                              UUID activeAssignmentId, String reason) {
        ServiceRequest sr = load(serviceRequestId);
        String from = sr.getStatus();
        if (!from.equals(toStatus)) {
            ServiceRequestStateMachine.requireTransition(from, toStatus);
            sr.setStatus(toStatus);
        }
        sr.setActiveAssignmentId(activeAssignmentId);
        srRepo.save(sr);
        audit.audit(actor, primaryRole(actor), "SERVICE_REQUEST_" + toStatus, "SERVICE_REQUEST",
                sr.getId(), from, toStatus, reason);
        audit.activity("SERVICE_REQUEST", sr.getId(), actor, "PROJECTED_" + toStatus,
                "Request projected " + from + " → " + toStatus);
    }

    /** Load a request for the dispatch layer (existence + not-deleted); no authz here. */
    @Transactional(readOnly = true)
    public ServiceRequest loadForDispatch(UUID serviceRequestId) {
        return load(serviceRequestId);
    }

    /** Scoped, paginated list with filters. Non-GLOBAL callers see only in-scope requests. */
    @Transactional(readOnly = true)
    public PageResponse<ServiceRequestListItemResponse> list(UUID actor, Integer page, Integer pageSize,
                                                             UUID apartmentId, UUID areaId, UUID requesterId,
                                                             String status, String priority) {
        authz.requirePermission(actor, "SERVICE_REQUEST_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        boolean global = scopeService.hasGlobal(actor);
        var visibleAreas = scopeService.visibleAreaIds(actor);
        var apartmentScope = scopeService.grantedApartmentIds(actor);

        String statusFilter = status == null ? null : status.trim().toUpperCase();
        String priorityFilter = priority == null ? null : priority.trim().toUpperCase();

        var all = srRepo.findByDeletedFalse(PageRequest.of(0, Integer.MAX_VALUE)).getContent().stream()
                .filter(sr -> global
                        || visibleAreas.contains(sr.getAreaId())
                        || apartmentScope.contains(sr.getApartmentId()))
                .filter(sr -> apartmentId == null || apartmentId.equals(sr.getApartmentId()))
                .filter(sr -> areaId == null || areaId.equals(sr.getAreaId()))
                .filter(sr -> requesterId == null || requesterId.equals(sr.getRequesterUserId()))
                .filter(sr -> statusFilter == null || statusFilter.equals(sr.getStatus()))
                .filter(sr -> priorityFilter == null || priorityFilter.equals(sr.getPriority()))
                // Newest first (created date order), stable tiebreak on reference.
                .sorted((x, y) -> {
                    LocalDateTime xc = x.getCreatedAt(), yc = y.getCreatedAt();
                    int cmp = (xc == null || yc == null) ? 0 : yc.compareTo(xc);
                    return cmp != 0 ? cmp : x.getReferenceNo().compareTo(y.getReferenceNo());
                })
                .toList();
        long total = all.size();
        int from = Math.min((p - 1) * ps, all.size());
        int to = Math.min(from + ps, all.size());
        List<ServiceRequestListItemResponse> data = all.subList(from, to).stream()
                .map(sr -> new ServiceRequestListItemResponse(sr.getId(), sr.getReferenceNo(),
                        sr.getApartmentId(), sr.getAreaId(), sr.getRequesterUserId(),
                        sr.getCategory(), sr.getPriority(), sr.getStatus(), sr.getCreatedAt()))
                .toList();
        return PageResponse.of(data, p, ps, total);
    }
}
