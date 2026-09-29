package com.manafy.ops.gate.repository;

import com.manafy.ops.gate.entity.VisitorEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitorEntryRepository extends JpaRepository<VisitorEntry, UUID> {
    Optional<VisitorEntry> findByIdAndDeletedFalse(UUID id);
    List<VisitorEntry> findByApartmentIdAndStatusAndDeletedFalse(UUID apartmentId, String status);
    List<VisitorEntry> findByApartmentIdAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtDesc(UUID apartmentId, LocalDateTime since);
    Optional<VisitorEntry> findByApartmentIdAndOtpCodeAndDeletedFalse(UUID apartmentId, String otpCode);
}
