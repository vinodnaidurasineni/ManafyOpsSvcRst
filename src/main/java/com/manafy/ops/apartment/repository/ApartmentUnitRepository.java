package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.ApartmentUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApartmentUnitRepository extends JpaRepository<ApartmentUnit, UUID> {
    Optional<ApartmentUnit> findByIdAndDeletedFalse(UUID id);
    List<ApartmentUnit> findByBuildingIdAndDeletedFalse(UUID buildingId);
    long countByApartmentIdAndDeletedFalse(UUID apartmentId);
    boolean existsByBuildingIdAndUnitNumberAndDeletedFalse(UUID buildingId, String unitNumber);
}
