package com.manafy.ops.dispatch.repository;

import com.manafy.ops.dispatch.entity.AssignmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssignmentStatusHistoryRepository extends JpaRepository<AssignmentStatusHistory, UUID> {
    List<AssignmentStatusHistory> findByAssignmentIdOrderByCreatedAtAsc(UUID assignmentId);
}
