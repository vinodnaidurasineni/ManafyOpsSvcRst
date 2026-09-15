package com.manafy.ops.dispatch.repository;

import com.manafy.ops.dispatch.entity.Assignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssignmentRepository
        extends JpaRepository<Assignment, UUID>, JpaSpecificationExecutor<Assignment> {

    Optional<Assignment> findByIdAndDeletedFalse(UUID id);

    List<Assignment> findByServiceRequestIdAndDeletedFalseOrderByCreatedAtAsc(UUID serviceRequestId);

    Optional<Assignment> findByServiceRequestIdAndActiveTrueAndDeletedFalse(UUID serviceRequestId);

    long countByTechnicianIdAndActiveTrueAndDeletedFalse(UUID technicianId);

    Page<Assignment> findByDeletedFalse(Pageable pageable);

    boolean existsByReferenceNo(String referenceNo);
}
