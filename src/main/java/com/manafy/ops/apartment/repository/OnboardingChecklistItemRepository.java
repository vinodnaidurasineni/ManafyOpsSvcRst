package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.OnboardingChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnboardingChecklistItemRepository extends JpaRepository<OnboardingChecklistItem, UUID> {
    List<OnboardingChecklistItem> findByOnboardingIdAndDeletedFalse(UUID onboardingId);
    Optional<OnboardingChecklistItem> findByOnboardingIdAndItemKeyAndDeletedFalse(UUID onboardingId, String itemKey);
    List<OnboardingChecklistItem> findByOnboardingIdAndMandatoryTrueAndCompleteFalseAndDeletedFalse(UUID onboardingId);
}
