package com.manafy.ops.manualrequest.repository;

import com.manafy.ops.manualrequest.entity.ManualAssignmentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManualAssignmentRequestRepository extends JpaRepository<ManualAssignmentRequest, UUID> {

    Optional<ManualAssignmentRequest> findByIdAndDeletedFalse(UUID id);

    /** Second-layer dedupe backstop for cross-boundary intake (with the idempotency key). */
    Optional<ManualAssignmentRequest> findBySourceSystemAndSourceTypeAndSourceIdAndDeletedFalse(
            String sourceSystem, String sourceType, String sourceId);

    Page<ManualAssignmentRequest> findByDeletedFalse(Pageable pageable);

    /**
     * Daily workload for a helper: the number of active (non-terminal) manual
     * requests assigned to the given assignee whose scheduled day is {@code day}.
     * This is DERIVED from the request rows (no counter to drift) and is the
     * balancing signal for lowest-daily-workload assignment.
     *
     * Cancelled requests do not count; completed ones for the day still count as
     * that day's workload (the helper did the work). Assignee is the opaque
     * assigneeRef (the helper's id as a string).
     */
    @Query("SELECT COUNT(r) FROM ManualAssignmentRequest r " +
           "WHERE r.deleted = false AND r.assigneeRef = :assigneeRef " +
           "AND r.startDate = :day AND r.status <> 'CANCELLED'")
    long countDailyWorkload(@Param("assigneeRef") String assigneeRef, @Param("day") LocalDate day);
}
