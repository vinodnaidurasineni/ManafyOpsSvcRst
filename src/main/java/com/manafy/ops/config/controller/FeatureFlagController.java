package com.manafy.ops.config.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.config.entity.FeatureFlag;
import com.manafy.ops.config.repository.FeatureFlagRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Feature flags (Artifact #3 §1, spec §92). Not an authorization mechanism (DD-28). */
@RestController
@RequestMapping("/api/v1/feature-flags")
public class FeatureFlagController {

    public record FlagResponse(String key, boolean enabled, String description, long version) {}
    public record UpdateFlagRequest(@NotNull Boolean enabled) {}

    private final FeatureFlagRepository flagRepo;
    private final AuthenticationContext authContext;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final AuditService audit;

    public FeatureFlagController(FeatureFlagRepository flagRepo, AuthenticationContext authContext,
                                 AuthorizationService authz, PermissionService permissionService, AuditService audit) {
        this.flagRepo = flagRepo;
        this.authContext = authContext;
        this.authz = authz;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<FlagResponse>> list() {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "CONFIG_VIEW");
        return ApiResponse.ok(flagRepo.findByDeletedFalse().stream()
                .map(f -> new FlagResponse(f.getFlagKey(), f.isEnabled(), f.getDescription(), f.getVersion()))
                .toList());
    }

    @PatchMapping("/{key}")
    public ApiResponse<FlagResponse> update(@PathVariable String key, @RequestBody UpdateFlagRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "FEATURE_FLAG_MANAGE");
        FeatureFlag f = flagRepo.findByFlagKeyAndDeletedFalse(key).orElseGet(() -> {
            FeatureFlag nf = new FeatureFlag();
            nf.setFlagKey(key);
            return nf;
        });
        f.setEnabled(req.enabled());
        FeatureFlag saved = flagRepo.save(f);
        audit.audit(actor, primaryRole(actor), "FEATURE_FLAG_CHANGED", "FEATURE_FLAG", saved.getId(),
                null, String.valueOf(req.enabled()), "Feature flag updated: " + key);
        return ApiResponse.ok(new FlagResponse(saved.getFlagKey(), saved.isEnabled(),
                saved.getDescription(), saved.getVersion()));
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }
}
