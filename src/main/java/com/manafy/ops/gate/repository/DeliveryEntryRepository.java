package com.manafy.ops.gate.repository;

import com.manafy.ops.gate.entity.DeliveryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryEntryRepository extends JpaRepository<DeliveryEntry, UUID> {
    List<DeliveryEntry> findByApartmentIdAndStatusAndDeletedFalse(UUID apartmentId, String status);
}
