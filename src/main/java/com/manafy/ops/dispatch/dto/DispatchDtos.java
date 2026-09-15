package com.manafy.ops.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Phase 4B dispatch DTOs. Entities are never returned directly. */
public final class DispatchDtos {

    private DispatchDtos() {}

    // ─── Action requests ─────────────────────────────────────────────

    public record CreateAssignmentRequest(
            @NotNull UUID technicianId,
            UUID helperId,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd) {}

    public record ReassignRequest(
            @NotNull UUID technicianId,
            UUID helperId,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            @Size(max = 1000) String reason) {}

    public record RescheduleRequest(
            @NotNull LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd) {}

    public record ReasonRequest(@Size(max = 1000) String reason) {}

    public record CompleteRequest(@Size(max = 2000) String notes, String outcome) {}

    // ─── Responses ───────────────────────────────────────────────────

    public record AssignmentResponse(
            UUID id, String referenceNo, UUID serviceRequestId, UUID technicianId, UUID helperId,
            UUID vendorId, String status, boolean active,
            LocalDateTime scheduledStart, LocalDateTime scheduledEnd,
            LocalDateTime assignedAt, LocalDateTime acceptedAt, LocalDateTime declinedAt,
            LocalDateTime enRouteAt, LocalDateTime arrivedAt, LocalDateTime startedAt,
            LocalDateTime completedAt, LocalDateTime cancelledAt,
            String completionNotes, String declineReason, String cancelReason,
            String noShowReason, String reworkReason, UUID previousAssignmentId,
            LocalDateTime createdAt, long version) {}

    public record AssignmentListItemResponse(
            UUID id, String referenceNo, UUID serviceRequestId, UUID technicianId,
            String status, boolean active, LocalDateTime scheduledStart, LocalDateTime createdAt) {}

    public record AssignmentHistoryItemResponse(
            String fromStatus, String toStatus, UUID changedBy, String reason, LocalDateTime changedAt) {}

    public record EligibleTechnicianResponse(
            UUID technicianId, String code, String name, UUID vendorId,
            String availabilityStatus, UUID areaId, List<String> skills,
            long currentActiveJobs, int maxConcurrentJobs) {}

    // ─── Queue rows ──────────────────────────────────────────────────

    public record OperationalQueueItem(
            UUID serviceRequestId, String referenceNo, UUID apartmentId, UUID areaId,
            String category, String priority, String requestStatus,
            UUID activeAssignmentId, UUID technicianId, String assignmentStatus,
            LocalDateTime scheduledStart, LocalDateTime createdAt) {}

    public record DispatchQueueItem(
            UUID serviceRequestId, String referenceNo, UUID apartmentId, UUID areaId,
            String category, String priority, String requestStatus,
            UUID activeAssignmentId, String assignmentStatus, String dispatchReason,
            LocalDateTime createdAt) {}

    // ─── Eligibility internal helper ─────────────────────────────────

    /** Deterministic eligibility result: eligible flag + human reason. */
    public record EligibilityReason(boolean eligible, String reason) {
        public static EligibilityReason pass() { return new EligibilityReason(true, "ELIGIBLE"); }
        public static EligibilityReason fail(String reason) { return new EligibilityReason(false, reason); }
    }
}
