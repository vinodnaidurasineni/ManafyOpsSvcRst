package com.manafy.ops.org.repository;

import com.manafy.ops.org.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AreaRepository extends JpaRepository<Area, UUID> {
    Optional<Area> findByIdAndDeletedFalse(UUID id);
    List<Area> findByDeletedFalse();
    List<Area> findByRegionIdAndDeletedFalse(UUID regionId);
    boolean existsByCode(String code);
}
