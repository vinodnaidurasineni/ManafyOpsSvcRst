package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.WorkforceSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkforceSkillRepository extends JpaRepository<WorkforceSkill, UUID> {
    List<WorkforceSkill> findByTechnicianIdAndDeletedFalse(UUID technicianId);
    List<WorkforceSkill> findByHelperIdAndDeletedFalse(UUID helperId);
    Optional<WorkforceSkill> findByTechnicianIdAndSkillIdAndDeletedFalse(UUID technicianId, UUID skillId);
    Optional<WorkforceSkill> findByHelperIdAndSkillIdAndDeletedFalse(UUID helperId, UUID skillId);
    Optional<WorkforceSkill> findByIdAndDeletedFalse(UUID id);
}
