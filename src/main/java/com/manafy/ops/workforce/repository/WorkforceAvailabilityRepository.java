package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.WorkforceAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkforceAvailabilityRepository extends JpaRepository<WorkforceAvailability, UUID> {
    Optional<WorkforceAvailability> findByIdAndDeletedFalse(UUID id);
    List<WorkforceAvailability> findByTechnicianIdAndDeletedFalse(UUID technicianId);
    List<WorkforceAvailability> findByHelperIdAndDeletedFalse(UUID helperId);
}
