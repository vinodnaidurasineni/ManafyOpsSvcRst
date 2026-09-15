package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {
    Optional<Skill> findByIdAndDeletedFalse(UUID id);
    List<Skill> findByDeletedFalse();
    boolean existsByCode(String code);
}
