package com.manafy.ops.apartment.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** A persisted onboarding checklist item; mandatory items gate activation. */
@Entity
@Table(name = "apartment_onboarding_checklist",
        uniqueConstraints = @UniqueConstraint(name = "uk_checklist_onboarding_item", columnNames = {"onboarding_id", "item_key"}))
public class OnboardingChecklistItem extends BaseEntity {

    @Column(name = "onboarding_id", nullable = false)
    private UUID onboardingId;

    @Column(name = "item_key", nullable = false, length = 50)
    private String itemKey;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "is_complete", nullable = false)
    private boolean complete = false;

    @Column(name = "completed_by")
    private UUID completedBy;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public UUID getOnboardingId() { return onboardingId; }
    public void setOnboardingId(UUID onboardingId) { this.onboardingId = onboardingId; }
    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public boolean isComplete() { return complete; }
    public void setComplete(boolean complete) { this.complete = complete; }
    public UUID getCompletedBy() { return completedBy; }
    public void setCompletedBy(UUID completedBy) { this.completedBy = completedBy; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
