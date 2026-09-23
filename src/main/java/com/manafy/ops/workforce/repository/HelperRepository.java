package com.manafy.ops.workforce.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.manafy.ops.workforce.entity.Helper;

@Repository
public interface HelperRepository extends JpaRepository<Helper, UUID> {
    Optional<Helper> findByIdAndDeletedFalse(UUID id);
    boolean existsByCode(String code);
    Page<Helper> findByDeletedFalse(Pageable pageable);

    /** All non-deleted helpers in a service category (any status). */
    List<Helper> findByCategoryAndDeletedFalse(String category);

    /** Eligible pool: a category's ACTIVE + AVAILABLE helpers (assignment candidates). */
    List<Helper> findByCategoryAndStatusAndAvailabilityStatusAndDeletedFalse(
            String category, String status, String availabilityStatus);

    /** All ACTIVE + AVAILABLE helpers (used when no category filter applies). */
    List<Helper> findByStatusAndAvailabilityStatusAndDeletedFalse(String status, String availabilityStatus);
}
