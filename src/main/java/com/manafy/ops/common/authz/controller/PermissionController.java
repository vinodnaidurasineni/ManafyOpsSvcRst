package com.manafy.ops.common.authz.controller;

import com.manafy.ops.common.authz.dto.AuthzDtos.PermissionResponse;
import com.manafy.ops.common.authz.repository.PermissionRepository;
import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.common.security.AuthorizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only permission metadata, grouped by domain (Artifact #3 §1, spec §120). */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionRepository permissionRepo;
    private final AuthenticationContext authContext;
    private final AuthorizationService authz;

    public PermissionController(PermissionRepository permissionRepo, AuthenticationContext authContext,
                                AuthorizationService authz) {
        this.permissionRepo = permissionRepo;
        this.authContext = authContext;
        this.authz = authz;
    }

    @GetMapping
    public ApiResponse<List<PermissionResponse>> list() {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "PERMISSION_VIEW");
        List<PermissionResponse> data = permissionRepo.findByDeletedFalseOrderByDomainAscCodeAsc().stream()
                .map(p -> new PermissionResponse(p.getId(), p.getCode(), p.getName(), p.getDomain(),
                        p.getResource(), p.getAction(), p.isSensitive()))
                .toList();
        return ApiResponse.ok(data);
    }
}
