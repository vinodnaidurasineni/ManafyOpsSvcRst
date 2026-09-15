package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.ApartmentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApartmentDocumentRepository extends JpaRepository<ApartmentDocument, UUID> {
    Optional<ApartmentDocument> findByIdAndDeletedFalse(UUID id);
    List<ApartmentDocument> findByApartmentIdAndDeletedFalse(UUID apartmentId);
    long countByApartmentIdAndDeletedFalse(UUID apartmentId);
}
