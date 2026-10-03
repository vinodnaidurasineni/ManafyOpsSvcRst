package com.manafy.ops.maidapp.repository;

import com.manafy.ops.maidapp.entity.HelperTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface HelperTagRepository extends JpaRepository<HelperTag, UUID> {
    List<HelperTag> findByDeletedFalseOrderByCreatedAtDesc();
    List<HelperTag> findByCreatedAtAfterAndDeletedFalseOrderByCreatedAtDesc(LocalDateTime since);
}
