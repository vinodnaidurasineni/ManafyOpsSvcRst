package com.manafy.ops.maidapp.repository;

import com.manafy.ops.maidapp.entity.BookingSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingScheduleRepository extends JpaRepository<BookingSchedule, UUID> {
    Optional<BookingSchedule> findByIdAndDeletedFalse(UUID id);
    List<BookingSchedule> findByBookingIdAndDeletedFalse(UUID bookingId);
    long countByBookingIdAndDeletedFalse(UUID bookingId);
}
