package com.manafy.ops.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/** Ops complaint DTOs. Entities are never returned directly. */
public final class ComplaintDtos {

    private ComplaintDtos() {}

    /** Update the complaint status (and optionally attach admin notes). */
    public record UpdateStatusRequest(
            @NotBlank String status,
            @Size(max = 2000) String adminNotes) {}

    /** Record the maid/helper handling the complaint (opaque; no workforce FK). */
    public record AssignRequest(
            String assigneeRef,
            @NotBlank String assigneeName,
            String assigneeMobile,
            @Size(max = 2000) String adminNotes) {}

    public record ComplaintListItem(
            UUID id, String referenceNo, String complaintType, String status, String priority,
            String customerName, String assigneeName, LocalDateTime createdAt) {}

    public record ComplaintResponse(
            UUID id, String referenceNo, String complaintType, String status, String priority,
            String description,
            String customerName, String customerMobile,
            String assigneeRef, String assigneeName, String assigneeMobile,
            String bookingReference, String adminNotes,
            String communityApartmentId, UUID areaId, UUID regionId,
            LocalDateTime resolvedAt, LocalDateTime closedAt,
            LocalDateTime createdAt, long version) {}
}
