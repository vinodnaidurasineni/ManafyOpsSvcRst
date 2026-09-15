package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.ApartmentBuilding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApartmentBuildingRepository extends JpaRepository<ApartmentBuilding, UUID> {
    Optional<ApartmentBuilding> findByIdAndDeletedFalse(UUID id);
    List<ApartmentBuilding> findByApartmentIdAndDeletedFalse(UUID apartmentId);
    long countByApartmentIdAndDeletedFalse(UUID apartmentId);
    boolean existsByApartmentIdAndNameAndDeletedFalse(UUID apartmentId, String name);
}
