package com.manafy.ops.maidapp.repository;

import com.manafy.ops.maidapp.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {
    List<Complaint> findByDeletedFalseOrderByCreatedAtDesc();
}
