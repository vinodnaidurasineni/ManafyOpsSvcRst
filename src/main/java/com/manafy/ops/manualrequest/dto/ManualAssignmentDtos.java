package com.manafy.ops.manualrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Manual assignment request DTOs. Entities are never returned directly. */
public final class ManualAssignmentDtos {

    private ManualAssignmentDtos() {}

    /**
     * Cross-service intake payload from Community (service-to-service). Scope
     * anchors are NOT accepted from the client here; area/region are resolved (or
     * left null) server-side. Source ids are opaque strings, never FKs.
     */
    public record IntakeRequest(
            @NotBlank String sourceSystem,
            @NotBlank String sourceType,
            @NotBlank String sourceId,
            String communityCustomerId,
            String communityApartmentId,
            String communityFlatId,
            String serviceType,
            String serviceTitle,
            String frequency,
            String startDate,
            String timeSlot,
            String serviceAddress,
            String residentName,
            String contactNumber,
            String notes,
            String amount,
            String paymentStatus) {}

    /** Field-Officer records the chosen assignee (opaque; no technician eligibility). */
    public record AssignRequest(
            @NotBlank String assigneeType,   // EXTERNAL_PERSON | COMMUNITY_HELPER_REF | COMMUNITY_VENDOR_REF | OPS_WORKFORCE
            String assigneeRef,
            @NotBlank String assigneeName,
            String assigneePhone) {}

    public record CompleteRequest(@Size(max = 1000) String notes) {}

    public record CancelRequest(@Size(max = 1000) String reason) {}

    public record ManualRequestResponse(
            UUID id, String referenceNo, String status,
            String sourceSystem, String sourceType, String sourceId,
            String communityApartmentId, String communityFlatId,
            UUID areaId, UUID regionId,
            String serviceType, String serviceTitle, String frequency, LocalDate startDate,
            String timeSlot, String serviceAddress, String residentName, String contactNumber,
            String notes, BigDecimal amount, String paymentStatus, String priority,
            UUID fieldOfficerId,
            String assigneeType, String assigneeRef, String assigneeName, String assigneePhone,
            String completionNotes, String cancelReason,
            LocalDateTime createdAt, long version) {}

    public record ManualRequestListItem(
            UUID id, String referenceNo, String status, String serviceType,
            String frequency, LocalDate startDate, String timeSlot,
            String communityApartmentId, UUID areaId, String residentName,
            String assigneeName, LocalDateTime createdAt) {}

    /**
     * A candidate assignable person surfaced to the FO (from Ops workforce data),
     * ordered by today's workload so the least-loaded eligible helper is first.
     */
    public record AssignableCandidate(
            String assigneeType, String assigneeRef, String name, String phone,
            String kind, String availabilityStatus, String category, long todaysAssignments) {}
}
