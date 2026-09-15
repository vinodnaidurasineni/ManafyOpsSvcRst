package com.manafy.ops.common.authz.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** DTOs for role / permission administration (Artifact #3 §1). */
public final class AuthzDtos {

    private AuthzDtos() {}

    public record RoleResponse(UUID id, String code, String name, String description,
                               boolean system, String assignableByMinRole, String status, long version) {}

    public record PermissionResponse(UUID id, String code, String name, String domain,
                                     String resource, String action, boolean sensitive) {}

    public record CreateRoleRequest(@NotBlank String code, @NotBlank String name,
                                    String description, String assignableByMinRole) {}

    public record UpdateRoleRequest(String name, String description, String assignableByMinRole, Long version) {}

    public record RolePermissionRequest(@NotBlank String permissionCode) {}

    public record AuditLogResponse(UUID id, UUID actorUserId, String actorRole, String action,
                                   String resourceType, UUID resourceId, String reason,
                                   String source, String correlationId, String createdAt) {}
}
