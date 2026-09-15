package com.manafy.ops.apartment.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/** Onboarding request/response DTOs (Phase 2 §5, §6, §25). */
public final class OnboardingDtos {

    private OnboardingDtos() {}

    /** Start onboarding for an existing apartment (or reference by apartmentId). */
    public record OnboardingStartRequest(UUID apartmentId) {}

    public record OnboardingRejectRequest(@NotBlank String reason) {}

    public record ChecklistUpdateRequest(@NotBlank String itemKey, boolean complete) {}

    public record ChecklistItemResponse(String itemKey, String label, boolean mandatory,
                                        boolean complete, UUID completedBy, String completedAt) {}

    public record OnboardingChecklistResponse(UUID onboardingId, UUID apartmentId, String status,
                                              List<ChecklistItemResponse> items) {}

    public record OnboardingResponse(
            UUID id, UUID apartmentId, String status,
            UUID startedBy, String submittedAt,
            UUID verifiedBy, String verifiedAt,
            UUID rejectedBy, String rejectedAt, String rejectionReason,
            String resubmittedAt, long version,
            List<ChecklistItemResponse> checklist
    ) {}

    public record StatusHistoryResponse(String fromStatus, String toStatus, UUID changedBy,
                                        String reason, String changedAt) {}
}
