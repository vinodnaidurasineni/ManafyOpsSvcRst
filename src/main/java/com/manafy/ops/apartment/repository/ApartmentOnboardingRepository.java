package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.ApartmentOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApartmentOnboardingRepository extends JpaRepository<ApartmentOnboarding, UUID> {
    Optional<ApartmentOnboarding> findByIdAndDeletedFalse(UUID id);
    Optional<ApartmentOnboarding> findByApartmentIdAndDeletedFalse(UUID apartmentId);
}
