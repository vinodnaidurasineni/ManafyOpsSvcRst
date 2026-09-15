package com.manafy.ops.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request/response DTOs for user administration (Artifact #3 §1). */
public final class UserDtos {

    private UserDtos() {}

    public record CreateUserRequest(
            @NotBlank String displayName,
            String email,
            String mobile,
            String cognitoSub
    ) {}

    public record UpdateUserRequest(
            String displayName,
            String email,
            String mobile,
            Long version
    ) {}

    public record AssignRoleRequest(@NotBlank String roleCode) {}

    public record AssignScopeRequest(
            @NotNull String scopeType,   // GLOBAL|REGION|AREA|APARTMENT|VENDOR|SELF|ASSIGNED
            UUID regionId,
            UUID areaId,
            UUID apartmentId,
            UUID vendorId
    ) {}

    public record UserResponse(
            UUID id,
            String displayName,
            String email,
            String mobile,
            String status,
            boolean superAdmin,
            long version
    ) {}
}
