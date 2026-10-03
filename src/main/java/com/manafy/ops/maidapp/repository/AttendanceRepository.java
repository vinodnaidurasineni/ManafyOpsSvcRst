package com.manafy.ops.maidapp.repository;

import com.manafy.ops.maidapp.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    Optional<Attendance> findByIdAndDeletedFalse(UUID id);
    Optional<Attendance> findByMaidIdAndAttendanceDateAndDeletedFalse(UUID maidId, LocalDate date);
}
