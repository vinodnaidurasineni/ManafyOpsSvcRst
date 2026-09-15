package com.manafy.ops.org.repository;

import com.manafy.ops.org.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegionRepository extends JpaRepository<Region, UUID> {
    Optional<Region> findByIdAndDeletedFalse(UUID id);
    List<Region> findByDeletedFalse();
    boolean existsByCode(String code);
}
