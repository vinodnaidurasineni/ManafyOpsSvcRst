package com.manafy.ops.apartment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Apartment request/response DTOs (Phase 2 §25). Entities are never exposed directly. */
public final class ApartmentDtos {

    private ApartmentDtos() {}

    public record ApartmentCreateRequest(
            @NotBlank String code,
            @NotBlank String name,
            String legalName,
            @NotNull UUID regionId,
            @NotNull UUID areaId,
            String addressLine1, String addressLine2, String city, String state, String pincode,
            BigDecimal latitude, BigDecimal longitude,
            String managementCompany, String timezone
    ) {}

    public record ApartmentUpdateRequest(
            String name, String legalName,
            String addressLine1, String addressLine2, String city, String state, String pincode,
            BigDecimal latitude, BigDecimal longitude,
            String managementCompany, String timezone,
            Long version
    ) {}

    public record ApartmentResponse(
            UUID id, String code, String name, String legalName,
            UUID regionId, UUID areaId,
            String addressLine1, String addressLine2, String city, String state, String country, String pincode,
            BigDecimal latitude, BigDecimal longitude,
            String status, String managementCompany, String timezone,
            UUID assignedFieldOfficerId, long version
    ) {}

    public record ApartmentListItemResponse(
            UUID id, String code, String name, UUID regionId, UUID areaId,
            String status, UUID assignedFieldOfficerId
    ) {}

    public record FieldOfficerAssignmentRequest(@NotNull UUID fieldOfficerId) {}

    public record SuspendRequest(@NotBlank String reason) {}
}
