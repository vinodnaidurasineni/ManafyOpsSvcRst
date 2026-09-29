package com.manafy.ops.maidapp.repository;

import com.manafy.ops.maidapp.entity.BookingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingAssignmentRepository extends JpaRepository<BookingAssignment, UUID> {
    Optional<BookingAssignment> findByIdAndDeletedFalse(UUID id);
    List<BookingAssignment> findByMaidIdAndDeletedFalse(UUID maidId);
}
