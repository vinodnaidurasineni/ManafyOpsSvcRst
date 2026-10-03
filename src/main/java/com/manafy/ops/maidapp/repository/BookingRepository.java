package com.manafy.ops.maidapp.repository;

import com.manafy.ops.maidapp.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdAndDeletedFalse(UUID id);
}
