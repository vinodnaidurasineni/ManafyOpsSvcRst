package com.manafy.ops.complaint.repository;

import com.manafy.ops.complaint.entity.OpsComplaint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OpsComplaintRepository extends JpaRepository<OpsComplaint, UUID> {

    Optional<OpsComplaint> findByIdAndDeletedFalse(UUID id);

    Page<OpsComplaint> findByDeletedFalse(Pageable pageable);

    /** Dedupe backstop for any cross-service intake (mirrors manual_assignment_request). */
    Optional<OpsComplaint> findBySourceSystemAndSourceTypeAndSourceIdAndDeletedFalse(
            String sourceSystem, String sourceType, String sourceId);

    long countByStatusAndDeletedFalse(String status);
}
