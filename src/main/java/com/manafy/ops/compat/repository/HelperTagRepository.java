package com.manafy.ops.compat.repository;

import com.manafy.ops.compat.entity.HelperTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface HelperTagRepository extends JpaRepository<HelperTag, UUID> {

    java.util.Optional<HelperTag> findByIdAndDeletedFalse(UUID id);

    /** All tags created on/after the cutoff, newest first (window filter for the admin screen). */
    List<HelperTag> findByCreatedAtGreaterThanEqualAndDeletedFalseOrderByCreatedAtDesc(LocalDateTime cutoff);

    /** Same, filtered by status. */
    List<HelperTag> findByStatusAndCreatedAtGreaterThanEqualAndDeletedFalseOrderByCreatedAtDesc(
            String status, LocalDateTime cutoff);
}
