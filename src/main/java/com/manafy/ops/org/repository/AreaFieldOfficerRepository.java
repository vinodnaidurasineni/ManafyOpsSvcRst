package com.manafy.ops.org.repository;

import com.manafy.ops.org.entity.AreaFieldOfficer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AreaFieldOfficerRepository extends JpaRepository<AreaFieldOfficer, UUID> {

    /** Current (active) assignments for an area. */
    List<AreaFieldOfficer> findByAreaIdAndEffectiveToIsNullAndDeletedFalse(UUID areaId);

    /** Full history (current + past) for an area. */
    List<AreaFieldOfficer> findByAreaIdAndDeletedFalseOrderByEffectiveFromDesc(UUID areaId);

    /** Current areas a field officer is responsible for (drives AREA_RESPONSIBLE scope). */
    List<AreaFieldOfficer> findByFieldOfficerIdAndEffectiveToIsNullAndDeletedFalse(UUID fieldOfficerId);

    /** Current row for a specific (area, officer) pair, if any. */
    Optional<AreaFieldOfficer> findByAreaIdAndFieldOfficerIdAndEffectiveToIsNullAndDeletedFalse(
            UUID areaId, UUID fieldOfficerId);
}
