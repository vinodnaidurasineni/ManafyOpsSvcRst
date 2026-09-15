package com.manafy.ops.servicerequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/** Phase 4A service request DTOs. Entities are never returned directly. */
public final class ServiceRequestDtos {

    private ServiceRequestDtos() {}

    /**
     * Create request. area_id is derived server-side from the apartment (never
     * trusted from the client for scope) — the client only supplies the apartment,
     * category, priority and description.
     */
    public record CreateRequest(
            @NotNull UUID apartmentId,
            @NotBlank String category,
            String priority,
            @NotBlank @Size(max = 2000) String description) {}

    /** Cancel request (optional reason). */
    public record CancelRequest(@Size(max = 1000) String reason) {}

    public record ServiceRequestResponse(
            UUID id, String referenceNo, UUID apartmentId, UUID areaId, UUID regionId,
            UUID requesterUserId, String category, String priority, String description,
            String status, LocalDateTime cancelledAt, String cancelReason,
            LocalDateTime createdAt, long version) {}

    public record ServiceRequestListItemResponse(
            UUID id, String referenceNo, UUID apartmentId, UUID areaId,
            UUID requesterUserId, String category, String priority, String status,
            LocalDateTime createdAt) {}
}
