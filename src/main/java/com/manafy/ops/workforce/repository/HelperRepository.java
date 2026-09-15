package com.manafy.ops.workforce.repository;

import com.manafy.ops.workforce.entity.Helper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HelperRepository extends JpaRepository<Helper, UUID> {
    Optional<Helper> findByIdAndDeletedFalse(UUID id);
    boolean existsByCode(String code);
    Page<Helper> findByDeletedFalse(Pageable pageable);
}
