package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.WorkforceArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkforceAreaRepository extends JpaRepository<WorkforceArea, UUID> {
    List<WorkforceArea> findByTechnicianIdAndDeletedFalse(UUID technicianId);
    List<WorkforceArea> findByVendorIdAndDeletedFalse(UUID vendorId);
    List<WorkforceArea> findByHelperIdAndDeletedFalse(UUID helperId);
    Optional<WorkforceArea> findByTechnicianIdAndAreaIdAndDeletedFalse(UUID technicianId, UUID areaId);
    Optional<WorkforceArea> findByVendorIdAndAreaIdAndDeletedFalse(UUID vendorId, UUID areaId);
    Optional<WorkforceArea> findByIdAndDeletedFalse(UUID id);
    List<WorkforceArea> findByAreaIdAndDeletedFalse(UUID areaId);
}
