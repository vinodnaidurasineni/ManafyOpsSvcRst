package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.OnboardingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OnboardingStatusHistoryRepository extends JpaRepository<OnboardingStatusHistory, UUID> {
    List<OnboardingStatusHistory> findByOnboardingIdOrderByCreatedAtAsc(UUID onboardingId);
}
