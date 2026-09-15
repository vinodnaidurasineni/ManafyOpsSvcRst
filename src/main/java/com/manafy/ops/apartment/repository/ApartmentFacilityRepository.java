package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.ApartmentFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApartmentFacilityRepository extends JpaRepository<ApartmentFacility, UUID> {
    Optional<ApartmentFacility> findByIdAndDeletedFalse(UUID id);
    List<ApartmentFacility> findByApartmentIdAndDeletedFalse(UUID apartmentId);
    long countByApartmentIdAndDeletedFalse(UUID apartmentId);
}
