package com.manafy.ops.apartment.repository;

import com.manafy.ops.apartment.entity.Apartment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApartmentRepository extends JpaRepository<Apartment, UUID>, JpaSpecificationExecutor<Apartment> {
    Optional<Apartment> findByIdAndDeletedFalse(UUID id);
    boolean existsByCode(String code);
    Page<Apartment> findByDeletedFalse(Pageable pageable);
}
