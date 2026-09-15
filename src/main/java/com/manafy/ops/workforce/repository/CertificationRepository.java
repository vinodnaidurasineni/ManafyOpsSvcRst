package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificationRepository extends JpaRepository<Certification, UUID> {
    Optional<Certification> findByIdAndDeletedFalse(UUID id);
    List<Certification> findByTechnicianIdAndDeletedFalse(UUID technicianId);
    List<Certification> findByHelperIdAndDeletedFalse(UUID helperId);
}
