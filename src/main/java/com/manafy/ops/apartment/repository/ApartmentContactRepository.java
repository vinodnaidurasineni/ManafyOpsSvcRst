package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.ApartmentContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApartmentContactRepository extends JpaRepository<ApartmentContact, UUID> {
    Optional<ApartmentContact> findByIdAndDeletedFalse(UUID id);
    List<ApartmentContact> findByApartmentIdAndDeletedFalse(UUID apartmentId);
    long countByApartmentIdAndDeletedFalse(UUID apartmentId);
}
