package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.ApartmentServiceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApartmentServiceConfigRepository extends JpaRepository<ApartmentServiceConfig, UUID> {
    Optional<ApartmentServiceConfig> findByIdAndDeletedFalse(UUID id);
    List<ApartmentServiceConfig> findByApartmentIdAndDeletedFalse(UUID apartmentId);
    Optional<ApartmentServiceConfig> findByApartmentIdAndServiceCodeAndDeletedFalse(UUID apartmentId, String serviceCode);
}
