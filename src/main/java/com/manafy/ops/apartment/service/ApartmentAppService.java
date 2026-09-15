package com.manafy.ops.apartment.service;

import com.manafy.ops.apartment.dto.ApartmentDtos.*;
import com.manafy.ops.apartment.entity.Apartment;
import com.manafy.ops.apartment.entity.ApartmentOnboarding;
import com.manafy.ops.apartment.repository.ApartmentOnboardingRepository;
import com.manafy.ops.apartment.repository.ApartmentRepository;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.*;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import com.manafy.ops.org.entity.AreaFieldOfficer;
import com.manafy.ops.org.repository.AreaFieldOfficerRepository;
import com.manafy.ops.org.repository.AreaRepository;
import com.manafy.ops.org.repository.RegionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Application service for apartment CRUD + lifecycle + Field Officer assignment
 * (Phase 2). Holds ALL business rules (region/area consistency, lifecycle
 * transitions, FO area-validity, soft delete). Controllers are thin.
 *
 * Every mutating method is @Transactional so the state change + audit commit
 * atomically. Authorization is enforced via the Phase-1 AuthorizationService
 * (permission + scope), never re-implemented.
 */
@Service
public class ApartmentAppService {

    private final ApartmentRepository apartmentRepo;
    private final ApartmentOnboardingRepository onboardingRepo;
    private final RegionRepository regionRepo;
    private final AreaRepository areaRepo;
    private final AreaFieldOfficerRepository afoRepo;
    private final OpsUserRepository userRepo;
    private final AuthorizationService authz;
    private final ScopeService scopeService;
    private final ResourceScopeResolver resolver;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final IdempotencyService idempotency;

    public ApartmentAppService(ApartmentRepository apartmentRepo, ApartmentOnboardingRepository onboardingRepo,
                               RegionRepository regionRepo, AreaRepository areaRepo,
                               AreaFieldOfficerRepository afoRepo, OpsUserRepository userRepo,
                               AuthorizationService authz, ScopeService scopeService,
                               ResourceScopeResolver resolver, PermissionService permissionService,
                               AuditService audit, IdempotencyService idempotency) {
        this.apartmentRepo = apartmentRepo;
        this.onboardingRepo = onboardingRepo;
        this.regionRepo = regionRepo;
        this.areaRepo = areaRepo;
        this.afoRepo = afoRepo;
        this.userRepo = userRepo;
        this.authz = authz;
        this.scopeService = scopeService;
        this.resolver = resolver;
        this.permissionService = permissionService;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    // ─── helpers ─────────────────────────────────────────────────────

    /** Load a non-deleted apartment or 404. */
    private Apartment load(UUID id) {
        return apartmentRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Apartment not found"));
    }

    /** Server-resolved ResourceRef for an apartment (IDOR-safe anchors). */
    private ResourceRef ref(Apartment a) {
        return resolver.apartment(a.getId(), a.getAreaId(), a.getRegionId());
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private void requireVersion(Apartment a, Long expected) {
        if (expected != null && expected != a.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT",
                    "Apartment was modified concurrently. Reload and retry.", HttpStatus.CONFLICT);
        }
    }

    /** Validate the (region, area) pair: area must belong to the region. */
    private void validateRegionArea(UUID regionId, UUID areaId) {
        regionRepo.findByIdAndDeletedFalse(regionId)
                .orElseThrow(() -> BusinessException.validation("Region not found: " + regionId));
        var area = areaRepo.findByIdAndDeletedFalse(areaId)
                .orElseThrow(() -> BusinessException.validation("Area not found: " + areaId));
        if (!area.getRegionId().equals(regionId)) {
            throw BusinessException.validation("Area " + areaId + " does not belong to region " + regionId);
        }
    }

    // ─── CRUD ────────────────────────────────────────────────────────

    @Transactional
    public Apartment create(UUID actor, ApartmentCreateRequest req, String idemKey) {
        authz.requirePermission(actor, "APARTMENT_CREATE");
        idempotency.register(idemKey, "POST /apartments", actor);
        validateRegionArea(req.regionId(), req.areaId());
        if (apartmentRepo.existsByCode(req.code())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Apartment code already exists", HttpStatus.CONFLICT);
        }
        Apartment a = new Apartment();
        a.setCode(req.code());
        a.setName(req.name());
        a.setLegalName(req.legalName());
        a.setRegionId(req.regionId());
        a.setAreaId(req.areaId());
        a.setAddressLine1(req.addressLine1());
        a.setAddressLine2(req.addressLine2());
        a.setCity(req.city());
        a.setState(req.state());
        a.setPincode(req.pincode());
        a.setLatitude(req.latitude());
        a.setLongitude(req.longitude());
        a.setManagementCompany(req.managementCompany());
        if (req.timezone() != null) a.setTimezone(req.timezone());
        a.setStatus("PROSPECT");
        Apartment saved = apartmentRepo.save(a);
        audit.audit(actor, primaryRole(actor), "APARTMENT_CREATED", "APARTMENT", saved.getId(),
                null, null, "Apartment created: " + req.code());
        audit.activity("APARTMENT", saved.getId(), actor, "CREATED", "Apartment created");
        return saved;
    }

    @Transactional(readOnly = true)
    public Apartment get(UUID actor, UUID id) {
        Apartment a = load(id);
        authz.authorize(actor, "APARTMENT_VIEW", ref(a));
        return a;
    }

    @Transactional
    public Apartment update(UUID actor, UUID id, ApartmentUpdateRequest req) {
        Apartment a = load(id);
        authz.authorize(actor, "APARTMENT_UPDATE", ref(a));
        requireVersion(a, req.version());
        if (req.name() != null) a.setName(req.name());
        if (req.legalName() != null) a.setLegalName(req.legalName());
        if (req.addressLine1() != null) a.setAddressLine1(req.addressLine1());
        if (req.addressLine2() != null) a.setAddressLine2(req.addressLine2());
        if (req.city() != null) a.setCity(req.city());
        if (req.state() != null) a.setState(req.state());
        if (req.pincode() != null) a.setPincode(req.pincode());
        if (req.latitude() != null) a.setLatitude(req.latitude());
        if (req.longitude() != null) a.setLongitude(req.longitude());
        if (req.managementCompany() != null) a.setManagementCompany(req.managementCompany());
        if (req.timezone() != null) a.setTimezone(req.timezone());
        Apartment saved = apartmentRepo.save(a);
        audit.audit(actor, primaryRole(actor), "APARTMENT_UPDATED", "APARTMENT", saved.getId(),
                null, null, "Apartment updated");
        return saved;
    }

    @Transactional
    public void softDelete(UUID actor, UUID id) {
        Apartment a = load(id);
        authz.authorize(actor, "APARTMENT_DELETE", ref(a));
        if ("ACTIVE".equals(a.getStatus())) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Active apartment cannot be deleted; suspend first.", HttpStatus.CONFLICT);
        }
        a.setDeleted(true);
        a.setDeletedAt(LocalDateTime.now());
        apartmentRepo.save(a);
        audit.audit(actor, primaryRole(actor), "APARTMENT_DELETED", "APARTMENT", a.getId(),
                null, null, "Apartment soft-deleted");
    }

    /** Scoped, paginated list with filters. Non-GLOBAL callers see only in-scope apartments. */
    @Transactional(readOnly = true)
    public PageResponse<ApartmentListItemResponse> list(UUID actor, Integer page, Integer pageSize,
                                                        UUID regionId, UUID areaId, String status,
                                                        UUID fieldOfficerId) {
        authz.requirePermission(actor, "APARTMENT_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        boolean global = scopeService.hasGlobal(actor);
        var visibleAreas = scopeService.visibleAreaIds(actor);
        var apartmentScope = scopeService.grantedApartmentIds(actor);

        // Fetch a page and filter by scope + query filters in-service (foundation-simple;
        // a Specification-based scoped query is a later optimization).
        var all = apartmentRepo.findByDeletedFalse(PageRequest.of(0, Integer.MAX_VALUE)).getContent().stream()
                .filter(a -> global || visibleAreas.contains(a.getAreaId()) || apartmentScope.contains(a.getId()))
                .filter(a -> regionId == null || regionId.equals(a.getRegionId()))
                .filter(a -> areaId == null || areaId.equals(a.getAreaId()))
                .filter(a -> status == null || status.equalsIgnoreCase(a.getStatus()))
                .filter(a -> fieldOfficerId == null || fieldOfficerId.equals(a.getAssignedFieldOfficerId()))
                .sorted((x, y) -> x.getCode().compareToIgnoreCase(y.getCode()))
                .toList();
        long total = all.size();
        int from = Math.min((p - 1) * ps, all.size());
        int to = Math.min(from + ps, all.size());
        List<ApartmentListItemResponse> data = all.subList(from, to).stream()
                .map(a -> new ApartmentListItemResponse(a.getId(), a.getCode(), a.getName(),
                        a.getRegionId(), a.getAreaId(), a.getStatus(), a.getAssignedFieldOfficerId()))
                .toList();
        return PageResponse.of(data, p, ps, total);
    }

    // ─── Lifecycle: activate / suspend ───────────────────────────────

    @Transactional
    public Apartment activate(UUID actor, UUID id, String idemKey) {
        Apartment a = load(id);
        authz.authorize(actor, "APARTMENT_ACTIVATE", ref(a));
        idempotency.register(idemKey, "POST /apartments/{id}/activate", actor);
        // Activation prerequisites are enforced by the onboarding service (checklist +
        // VERIFIED). Delegate the check; never activate merely because /activate was called.
        ApartmentOnboarding onb = onboardingRepo.findByApartmentIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("ONBOARDING_INCOMPLETE",
                        "Apartment has no onboarding record; cannot activate.", HttpStatus.CONFLICT));
        if (!"VERIFIED".equals(onb.getStatus())) {
            throw new BusinessException("ONBOARDING_INCOMPLETE",
                    "Apartment onboarding must be VERIFIED before activation (current: " + onb.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }
        a.setStatus("ACTIVE");
        a.setActivatedAt(LocalDateTime.now());
        Apartment saved = apartmentRepo.save(a);
        audit.audit(actor, primaryRole(actor), "APARTMENT_ACTIVATED", "APARTMENT", saved.getId(),
                null, "ACTIVE", "Apartment activated");
        audit.activity("APARTMENT", saved.getId(), actor, "ACTIVATED", "Apartment activated");
        return saved;
    }

    @Transactional
    public Apartment suspend(UUID actor, UUID id, String reason, String idemKey) {
        Apartment a = load(id);
        authz.authorize(actor, "APARTMENT_SUSPEND", ref(a));
        idempotency.register(idemKey, "POST /apartments/{id}/suspend", actor);
        if (!"ACTIVE".equals(a.getStatus())) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Only an ACTIVE apartment can be suspended (current: " + a.getStatus() + ")", HttpStatus.CONFLICT);
        }
        a.setStatus("SUSPENDED");
        a.setSuspendedAt(LocalDateTime.now());
        Apartment saved = apartmentRepo.save(a);
        audit.audit(actor, primaryRole(actor), "APARTMENT_SUSPENDED", "APARTMENT", saved.getId(),
                "ACTIVE", "SUSPENDED", reason);
        return saved;
    }

    // ─── Field Officer assignment (DD-46: area-valid only) ───────────

    @Transactional
    public Apartment assignFieldOfficer(UUID actor, UUID id, UUID fieldOfficerId, String idemKey) {
        Apartment a = load(id);
        authz.authorize(actor, "APARTMENT_ASSIGN_FIELD_OFFICER", ref(a));
        idempotency.register(idemKey, "POST /apartments/{id}/field-officer", actor);

        OpsUser fo = userRepo.findByIdAndDeletedFalse(fieldOfficerId)
                .orElseThrow(() -> BusinessException.notFound("Field officer (user) not found"));
        if (!"ACTIVE".equals(fo.getStatus())) {
            throw BusinessException.validation("Field officer is not ACTIVE");
        }
        // DD-46: the assigned FO must CURRENTLY cover the apartment's area
        // (area_field_officer, the single source of truth). No cross-area override.
        boolean coversArea = afoRepo
                .findByAreaIdAndFieldOfficerIdAndEffectiveToIsNullAndDeletedFalse(a.getAreaId(), fieldOfficerId)
                .isPresent();
        if (!coversArea) {
            throw new BusinessException("FIELD_OFFICER_AREA_MISMATCH",
                    "Field officer does not currently cover this apartment's area; cross-area assignment is forbidden.",
                    HttpStatus.CONFLICT);
        }
        a.setAssignedFieldOfficerId(fieldOfficerId);
        Apartment saved = apartmentRepo.save(a);
        audit.audit(actor, primaryRole(actor), "APARTMENT_FIELD_OFFICER_ASSIGNED", "APARTMENT", saved.getId(),
                null, null, "Field officer assigned");
        return saved;
    }

    @Transactional
    public Apartment unassignFieldOfficer(UUID actor, UUID id) {
        Apartment a = load(id);
        authz.authorize(actor, "APARTMENT_ASSIGN_FIELD_OFFICER", ref(a));
        a.setAssignedFieldOfficerId(null);
        Apartment saved = apartmentRepo.save(a);
        audit.audit(actor, primaryRole(actor), "APARTMENT_FIELD_OFFICER_UNASSIGNED", "APARTMENT", saved.getId(),
                null, null, "Field officer unassigned");
        return saved;
    }

    // Exposed for other services (onboarding) to resolve + authorize an apartment.
    public Apartment loadForAuthorized(UUID actor, UUID id, String permission) {
        Apartment a = load(id);
        authz.authorize(actor, permission, ref(a));
        return a;
    }

    public ResourceRef refFor(Apartment a) { return ref(a); }
    public ApartmentRepository repo() { return apartmentRepo; }
}
