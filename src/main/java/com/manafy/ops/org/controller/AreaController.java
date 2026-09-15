package com.manafy.ops.org.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.*;
import com.manafy.ops.identity.repository.OpsUserRepository;
import com.manafy.ops.org.dto.OrgDtos.*;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.AreaFieldOfficer;
import com.manafy.ops.org.repository.AreaFieldOfficerRepository;
import com.manafy.ops.org.repository.AreaRepository;
import com.manafy.ops.org.repository.RegionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Area administration + authoritative area↔Field Officer assignment (Artifact #3
 * §2, C-1/DD-20). AREA_VIEW is scope-enforced: a Field Officer sees only areas in
 * their scope. Assignment writes to area_field_officer (the source of truth).
 */
@RestController
@RequestMapping("/api/v1")
public class AreaController {

    private final AreaRepository areaRepo;
    private final RegionRepository regionRepo;
    private final AreaFieldOfficerRepository afoRepo;
    private final OpsUserRepository userRepo;
    private final AuthenticationContext authContext;
    private final AuthorizationService authz;
    private final ScopeService scopeService;
    private final ResourceScopeResolver resourceResolver;
    private final PermissionService permissionService;
    private final AuditService audit;

    public AreaController(AreaRepository areaRepo, RegionRepository regionRepo,
                          AreaFieldOfficerRepository afoRepo, OpsUserRepository userRepo,
                          AuthenticationContext authContext, AuthorizationService authz,
                          ScopeService scopeService, ResourceScopeResolver resourceResolver,
                          PermissionService permissionService, AuditService audit) {
        this.areaRepo = areaRepo;
        this.regionRepo = regionRepo;
        this.afoRepo = afoRepo;
        this.userRepo = userRepo;
        this.authContext = authContext;
        this.authz = authz;
        this.scopeService = scopeService;
        this.resourceResolver = resourceResolver;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    /** List areas visible to the caller (scope pre-filtered; GLOBAL sees all). */
    @GetMapping("/areas")
    public ApiResponse<List<AreaResponse>> list() {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "AREA_VIEW");
        boolean global = scopeService.hasGlobal(actor);
        var visibleAreas = scopeService.visibleAreaIds(actor);
        List<AreaResponse> data = areaRepo.findByDeletedFalse().stream()
                .filter(a -> global || visibleAreas.contains(a.getId()))
                .map(this::toResponse).toList();
        return ApiResponse.ok(data);
    }

    /** Get a single area — permission + scope (IDOR: scope resolved server-side). */
    @GetMapping("/areas/{id}")
    public ApiResponse<AreaResponse> get(@PathVariable UUID id) {
        UUID actor = authContext.currentUserId();
        Area a = areaRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Area not found"));
        // permission + scope over THIS area (anchors resolved from the persisted row)
        authz.authorize(actor, "AREA_VIEW", resourceResolver.area(a.getId()).region(a.getRegionId()));
        return ApiResponse.ok(toResponse(a));
    }

    @PostMapping("/areas")
    public ApiResponse<AreaResponse> create(@Valid @RequestBody CreateAreaRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "AREA_CREATE");
        regionRepo.findByIdAndDeletedFalse(req.regionId())
                .orElseThrow(() -> BusinessException.notFound("Region not found"));
        if (areaRepo.existsByCode(req.code())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Area code already exists", HttpStatus.CONFLICT);
        }
        Area a = new Area();
        a.setRegionId(req.regionId());
        a.setCode(req.code());
        a.setName(req.name());
        a.setCity(req.city());
        a.setState(req.state());
        a.setPostalCodes(req.postalCodes());
        if (req.timezone() != null) a.setTimezone(req.timezone());
        Area saved = areaRepo.save(a);
        audit.audit(actor, primaryRole(actor), "AREA_CREATED", "AREA", saved.getId(), null, null,
                "Area created: " + req.code());
        return ApiResponse.ok(toResponse(saved));
    }

    @PatchMapping("/areas/{id}")
    public ApiResponse<AreaResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateAreaRequest req) {
        UUID actor = authContext.currentUserId();
        Area a = areaRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Area not found"));
        authz.authorize(actor, "AREA_UPDATE", resourceResolver.area(a.getId()).region(a.getRegionId()));
        if (req.version() != null && req.version() != a.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT", "Area modified concurrently", HttpStatus.CONFLICT);
        }
        if (req.name() != null) a.setName(req.name());
        if (req.city() != null) a.setCity(req.city());
        if (req.state() != null) a.setState(req.state());
        if (req.postalCodes() != null) a.setPostalCodes(req.postalCodes());
        if (req.timezone() != null) a.setTimezone(req.timezone());
        if (req.status() != null) a.setStatus(req.status());
        Area saved = areaRepo.save(a);
        audit.audit(actor, primaryRole(actor), "AREA_UPDATED", "AREA", saved.getId(), null, null, "Area updated");
        return ApiResponse.ok(toResponse(saved));
    }

    /** Current + historical FO assignments for an area (C-1 source of truth). */
    @GetMapping("/areas/{id}/field-officers")
    public ApiResponse<List<AreaFieldOfficerResponse>> listFieldOfficers(@PathVariable UUID id) {
        UUID actor = authContext.currentUserId();
        Area a = areaRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Area not found"));
        authz.authorize(actor, "AREA_VIEW", resourceResolver.area(a.getId()).region(a.getRegionId()));
        List<AreaFieldOfficerResponse> data = afoRepo
                .findByAreaIdAndDeletedFalseOrderByEffectiveFromDesc(id).stream()
                .map(this::toAfoResponse).toList();
        return ApiResponse.ok(data);
    }

    /** Assign a Field Officer to an area (inserts a current row; DD-20). */
    @PostMapping("/areas/{id}/field-officers")
    public ApiResponse<AreaFieldOfficerResponse> assignFieldOfficer(
            @PathVariable UUID id, @Valid @RequestBody AssignFieldOfficerRequest req) {
        UUID actor = authContext.currentUserId();
        Area a = areaRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Area not found"));
        authz.authorize(actor, "AREA_ASSIGN_OFFICER", resourceResolver.area(a.getId()).region(a.getRegionId()));

        userRepo.findByIdAndDeletedFalse(req.fieldOfficerId())
                .orElseThrow(() -> BusinessException.notFound("Field officer (user) not found"));

        String designation = req.designation() == null ? "PRIMARY" : req.designation();
        if (!designation.equals("PRIMARY") && !designation.equals("SECONDARY")) {
            throw BusinessException.validation("designation must be PRIMARY or SECONDARY");
        }

        // Application-layer enforcement of "at most one CURRENT primary per area"
        // (H2 lacks partial unique indexes; Postgres prod profile adds it too).
        if (designation.equals("PRIMARY")) {
            for (AreaFieldOfficer cur : afoRepo.findByAreaIdAndEffectiveToIsNullAndDeletedFalse(id)) {
                if ("PRIMARY".equals(cur.getDesignation())) {
                    cur.setEffectiveTo(LocalDateTime.now());
                    afoRepo.save(cur);
                }
            }
        }
        // Prevent duplicate current (area, officer).
        afoRepo.findByAreaIdAndFieldOfficerIdAndEffectiveToIsNullAndDeletedFalse(id, req.fieldOfficerId())
                .ifPresent(existing -> {
                    existing.setEffectiveTo(LocalDateTime.now());
                    afoRepo.save(existing);
                });

        AreaFieldOfficer afo = new AreaFieldOfficer();
        afo.setAreaId(id);
        afo.setFieldOfficerId(req.fieldOfficerId());
        afo.setDesignation(designation);
        afo.setEffectiveFrom(LocalDateTime.now());
        afo.setAssignedBy(actor);
        AreaFieldOfficer saved = afoRepo.save(afo);
        audit.audit(actor, primaryRole(actor), "AREA_OFFICER_ASSIGNED", "AREA", id, null, null,
                "Field officer assigned (" + designation + ")");
        return ApiResponse.ok(toAfoResponse(saved));
    }

    /** Unassign: close the current assignment (history preserved; DD-20). */
    @DeleteMapping("/areas/{id}/field-officers/{foId}")
    public ApiResponse<Void> unassignFieldOfficer(@PathVariable UUID id, @PathVariable UUID foId) {
        UUID actor = authContext.currentUserId();
        Area a = areaRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Area not found"));
        authz.authorize(actor, "AREA_ASSIGN_OFFICER", resourceResolver.area(a.getId()).region(a.getRegionId()));
        AreaFieldOfficer cur = afoRepo
                .findByAreaIdAndFieldOfficerIdAndEffectiveToIsNullAndDeletedFalse(id, foId)
                .orElseThrow(() -> BusinessException.notFound("Current assignment not found"));
        cur.setEffectiveTo(LocalDateTime.now());
        afoRepo.save(cur);
        audit.audit(actor, primaryRole(actor), "AREA_OFFICER_UNASSIGNED", "AREA", id, null, null,
                "Field officer unassigned");
        return ApiResponse.ok(null);
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private AreaResponse toResponse(Area a) {
        return new AreaResponse(a.getId(), a.getRegionId(), a.getCode(), a.getName(), a.getCity(), a.getState(),
                a.getPostalCodes(), a.getTimezone(), a.getStatus(), a.getVersion());
    }

    private AreaFieldOfficerResponse toAfoResponse(AreaFieldOfficer afo) {
        return new AreaFieldOfficerResponse(afo.getId(), afo.getAreaId(), afo.getFieldOfficerId(),
                afo.getDesignation(),
                afo.getEffectiveFrom() == null ? null : afo.getEffectiveFrom().toString(),
                afo.getEffectiveTo() == null ? null : afo.getEffectiveTo().toString(),
                afo.getEffectiveTo() == null);
    }
}
