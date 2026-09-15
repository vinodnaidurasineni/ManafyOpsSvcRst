package com.manafy.ops.org.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.org.dto.OrgDtos.*;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.org.repository.RegionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Region administration (Artifact #3 §2). */
@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

    private final RegionRepository regionRepo;
    private final AuthenticationContext authContext;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final AuditService audit;

    public RegionController(RegionRepository regionRepo, AuthenticationContext authContext,
                            AuthorizationService authz, PermissionService permissionService, AuditService audit) {
        this.regionRepo = regionRepo;
        this.authContext = authContext;
        this.authz = authz;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<RegionResponse>> list() {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "REGION_VIEW");
        return ApiResponse.ok(regionRepo.findByDeletedFalse().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<RegionResponse> get(@PathVariable UUID id) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "REGION_VIEW");
        Region r = regionRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Region not found"));
        return ApiResponse.ok(toResponse(r));
    }

    @PostMapping
    public ApiResponse<RegionResponse> create(@Valid @RequestBody CreateRegionRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "REGION_CREATE");
        if (regionRepo.existsByCode(req.code())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Region code already exists", HttpStatus.CONFLICT);
        }
        Region r = new Region();
        r.setCode(req.code());
        r.setName(req.name());
        r.setCity(req.city());
        r.setState(req.state());
        if (req.timezone() != null) r.setTimezone(req.timezone());
        Region saved = regionRepo.save(r);
        audit.audit(actor, primaryRole(actor), "REGION_CREATED", "REGION", saved.getId(), null, null,
                "Region created: " + req.code());
        return ApiResponse.ok(toResponse(saved));
    }

    @PatchMapping("/{id}")
    public ApiResponse<RegionResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRegionRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "REGION_UPDATE");
        Region r = regionRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Region not found"));
        if (req.version() != null && req.version() != r.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT", "Region modified concurrently", HttpStatus.CONFLICT);
        }
        if (req.name() != null) r.setName(req.name());
        if (req.city() != null) r.setCity(req.city());
        if (req.state() != null) r.setState(req.state());
        if (req.timezone() != null) r.setTimezone(req.timezone());
        if (req.status() != null) r.setStatus(req.status());
        Region saved = regionRepo.save(r);
        audit.audit(actor, primaryRole(actor), "REGION_UPDATED", "REGION", saved.getId(), null, null, "Region updated");
        return ApiResponse.ok(toResponse(saved));
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private RegionResponse toResponse(Region r) {
        return new RegionResponse(r.getId(), r.getCode(), r.getName(), r.getCity(), r.getState(),
                r.getCountry(), r.getTimezone(), r.getStatus(), r.getVersion());
    }
}
