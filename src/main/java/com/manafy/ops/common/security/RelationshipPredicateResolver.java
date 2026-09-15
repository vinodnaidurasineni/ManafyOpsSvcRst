package com.manafy.ops.common.security;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.org.repository.AreaFieldOfficerRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves relationship predicates server-side (Artifact #2 §1ter, DD-33).
 *
 * Foundation implements:
 *   - SELF: the resource's owner is the actor.
 *   - AREA_RESPONSIBLE: the actor is a CURRENT Field Officer of the resource's area
 *     (from area_field_officer — the authoritative source, C-1/DD-20).
 *
 * Deferred predicates (ASSIGNED, RESIDENT, OWNER, RESOURCE_MANAGER) throw an
 * explicit "not yet resolvable" 403 until their resources exist — fail closed,
 * never fail open.
 */
@Service
public class RelationshipPredicateResolver {

    private final AreaFieldOfficerRepository areaFieldOfficerRepo;

    public RelationshipPredicateResolver(AreaFieldOfficerRepository areaFieldOfficerRepo) {
        this.areaFieldOfficerRepo = areaFieldOfficerRepo;
    }

    /** True if the actor satisfies the predicate for this resource. Never trusts client input. */
    public boolean resolves(UUID actorUserId, RelationshipPredicate predicate, ResourceRef ref) {
        if (actorUserId == null || predicate == null || ref == null) return false;
        return switch (predicate) {
            case SELF -> ref.getOwnerUserId() != null && ref.getOwnerUserId().equals(actorUserId);
            case AREA_RESPONSIBLE -> ref.getAreaId() != null && areaFieldOfficerRepo
                    .findByAreaIdAndFieldOfficerIdAndEffectiveToIsNullAndDeletedFalse(ref.getAreaId(), actorUserId)
                    .isPresent();
            // Fail closed: these predicates cannot be satisfied until their resources exist.
            case ASSIGNED, RESIDENT, OWNER, RESOURCE_MANAGER ->
                    throw new BusinessException("PREDICATE_NOT_AVAILABLE",
                            "Relationship predicate not available in this phase: " + predicate,
                            org.springframework.http.HttpStatus.FORBIDDEN);
        };
    }
}
