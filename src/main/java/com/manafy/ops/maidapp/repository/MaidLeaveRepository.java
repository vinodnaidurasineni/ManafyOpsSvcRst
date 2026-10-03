package com.manafy.ops.maidapp.repository;

import com.manafy.ops.maidapp.entity.MaidLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaidLeaveRepository extends JpaRepository<MaidLeave, UUID> {
    List<MaidLeave> findByMaidIdAndDeletedFalse(UUID maidId);
}
