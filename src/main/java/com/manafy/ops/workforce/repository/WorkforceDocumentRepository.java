package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.WorkforceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkforceDocumentRepository extends JpaRepository<WorkforceDocument, UUID> {
    Optional<WorkforceDocument> findByIdAndDeletedFalse(UUID id);
    List<WorkforceDocument> findByTechnicianIdAndDeletedFalse(UUID technicianId);
    List<WorkforceDocument> findByHelperIdAndDeletedFalse(UUID helperId);
    List<WorkforceDocument> findByVendorIdAndDeletedFalse(UUID vendorId);
}
