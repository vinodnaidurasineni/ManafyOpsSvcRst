package com.manafy.ops.config.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.config.entity.SystemConfiguration;
import com.manafy.ops.config.repository.SystemConfigurationRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * System configuration (Artifact #3 §1, spec §91). Sensitive keys require Super
 * Admin to change; all changes are audited.
 */
@RestController
@RequestMapping("/api/v1/configuration")
public class ConfigurationController {

    public record ConfigResponse(String key, String value, boolean sensitive, long version) {}
    public record UpdateConfigRequest(@NotNull String value, Long version) {}

    private final SystemConfigurationRepository configRepo;
    private final AuthenticationContext authContext;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final AuditService audit;

    public ConfigurationController(SystemConfigurationRepository configRepo, AuthenticationContext authContext,
                                   AuthorizationService authz, PermissionService permissionService, AuditService audit) {
        this.configRepo = configRepo;
        this.authContext = authContext;
        this.authz = authz;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<ConfigResponse>> list() {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "CONFIG_VIEW");
        return ApiResponse.ok(configRepo.findByDeletedFalse().stream()
                .map(c -> new ConfigResponse(c.getConfigKey(), c.getConfigValue(), c.isSensitive(), c.getVersion()))
                .toList());
    }

    @PatchMapping("/{key}")
    public ApiResponse<ConfigResponse> update(@PathVariable String key, @RequestBody UpdateConfigRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "CONFIG_UPDATE");
        SystemConfiguration c = configRepo.findByConfigKeyAndDeletedFalse(key)
                .orElseThrow(() -> BusinessException.notFound("Configuration key not found"));

        // Sensitive keys require Super Admin (spec §91).
        if (c.isSensitive() && !authContext.roles(actor).contains("SUPER_ADMIN")) {
            throw BusinessException.forbidden("Sensitive configuration requires Super Admin");
        }
        if (req.version() != null && req.version() != c.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT", "Configuration modified concurrently", HttpStatus.CONFLICT);
        }
        String before = c.getConfigValue();
        c.setConfigValue(req.value());
        SystemConfiguration saved = configRepo.save(c);
        audit.audit(actor, primaryRole(actor), "CONFIG_UPDATED", "SYSTEM_CONFIGURATION", saved.getId(),
                before, req.value(), "Configuration updated: " + key);
        return ApiResponse.ok(new ConfigResponse(saved.getConfigKey(), saved.getConfigValue(),
                saved.isSensitive(), saved.getVersion()));
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }
}
