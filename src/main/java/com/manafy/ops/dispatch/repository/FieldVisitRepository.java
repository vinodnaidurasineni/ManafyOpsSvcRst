package com.manafy.ops.dispatch.repository;

import com.manafy.ops.dispatch.entity.FieldVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FieldVisitRepository extends JpaRepository<FieldVisit, UUID> {
    Optional<FieldVisit> findByIdAndDeletedFalse(UUID id);
    List<FieldVisit> findByAssignmentIdAndDeletedFalse(UUID assignmentId);
    List<FieldVisit> findByServiceRequestIdAndDeletedFalse(UUID serviceRequestId);
}
