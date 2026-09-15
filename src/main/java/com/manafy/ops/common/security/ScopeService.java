package com.manafy.ops.common.security;

import com.manafy.ops.common.authz.entity.UserScope;
import com.manafy.ops.common.authz.repository.UserScopeRepository;
import com.manafy.ops.org.entity.AreaFieldOfficer;
import com.manafy.ops.org.repository.AreaFieldOfficerRepository;
import com.manafy.ops.org.repository.AreaRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates whether a user's SCOPE grants cover a given resource. This is the
 * second independent gate (after permission). Never trusts client input: the
 * resource's anchors come from {@link ResourceRef} which was resolved server-side.
 *
 * Scope grants come from {@code user_scope}. Additionally, a user's AREA scope is
 * augmented by the areas they are a CURRENT Field Officer of (C-1/DD-20,
 * AREA_RESPONSIBLE), derived from {@code area_field_officer}.
 */
@Service
public class ScopeService {

    private final UserScopeRepository userScopeRepo;
    private final AreaFieldOfficerRepository areaFieldOfficerRepo;
    private final AreaRepository areaRepo;

    public ScopeService(UserScopeRepository userScopeRepo,
                        AreaFieldOfficerRepository areaFieldOfficerRepo,
                        AreaRepository areaRepo) {
        this.userScopeRepo = userScopeRepo;
        this.areaFieldOfficerRepo = areaFieldOfficerRepo;
        this.areaRepo = areaRepo;
    }

    /** True if the user holds a GLOBAL scope grant. */
    public boolean hasGlobal(UUID userId) {
        return userScopeRepo.findByUserIdAndDeletedFalse(userId).stream()
                .anyMatch(s -> ScopeType.GLOBAL.name().equals(s.getScopeType()));
    }

    /** Region ids the user is scoped to. */
    public Set<UUID> grantedRegionIds(UUID userId) {
        Set<UUID> ids = new HashSet<>();
        for (UserScope s : userScopeRepo.findByUserIdAndDeletedFalse(userId)) {
            if (ScopeType.REGION.name().equals(s.getScopeType()) && s.getRegionId() != null) {
                ids.add(s.getRegionId());
            }
        }
        return ids;
    }

    /**
     * Area ids the user is scoped to. Combines explicit AREA grants in user_scope
     * with the areas the user currently owns as a Field Officer (DD-20). This is
     * the deterministic Field Officer area resolution.
     */
    public Set<UUID> grantedAreaIds(UUID userId) {
        Set<UUID> ids = new HashSet<>();
        for (UserScope s : userScopeRepo.findByUserIdAndDeletedFalse(userId)) {
            if (ScopeType.AREA.name().equals(s.getScopeType()) && s.getAreaId() != null) {
                ids.add(s.getAreaId());
            }
        }
        for (AreaFieldOfficer afo :
                areaFieldOfficerRepo.findByFieldOfficerIdAndEffectiveToIsNullAndDeletedFalse(userId)) {
            ids.add(afo.getAreaId());
        }
        return ids;
    }

    /** Apartment ids the user is scoped to (explicit grants only in foundation). */
    public Set<UUID> grantedApartmentIds(UUID userId) {
        Set<UUID> ids = new HashSet<>();
        for (UserScope s : userScopeRepo.findByUserIdAndDeletedFalse(userId)) {
            if (ScopeType.APARTMENT.name().equals(s.getScopeType()) && s.getApartmentId() != null) {
                ids.add(s.getApartmentId());
            }
        }
        return ids;
    }

    /** Vendor ids the user is scoped to. */
    public Set<UUID> grantedVendorIds(UUID userId) {
        Set<UUID> ids = new HashSet<>();
        for (UserScope s : userScopeRepo.findByUserIdAndDeletedFalse(userId)) {
            if (ScopeType.VENDOR.name().equals(s.getScopeType()) && s.getVendorId() != null) {
                ids.add(s.getVendorId());
            }
        }
        return ids;
    }

    /**
     * Core scope check: does this user's scope cover the given resource?
     *
     * GLOBAL always passes. Otherwise the resource's anchors (resolved server-side)
     * must intersect the user's grants. Region membership also covers an area whose
     * region the user is granted (Region → Area inheritance).
     *
     * A resource with no anchors and no covering grant fails closed.
     */
    public boolean covers(UUID userId, ResourceRef ref) {
        if (ref == null) return false;
        if (hasGlobal(userId)) return true;

        // APARTMENT anchor
        if (ref.getApartmentId() != null && grantedApartmentIds(userId).contains(ref.getApartmentId())) {
            return true;
        }
        // VENDOR anchor
        if (ref.getVendorId() != null && grantedVendorIds(userId).contains(ref.getVendorId())) {
            return true;
        }
        // AREA anchor (direct area grant or FO-owned area)
        if (ref.getAreaId() != null) {
            if (grantedAreaIds(userId).contains(ref.getAreaId())) return true;
            // Region → Area inheritance: area within a granted region.
            Set<UUID> regions = grantedRegionIds(userId);
            if (!regions.isEmpty()) {
                var area = areaRepo.findByIdAndDeletedFalse(ref.getAreaId());
                if (area.isPresent() && regions.contains(area.get().getRegionId())) return true;
            }
        }
        // REGION anchor
        if (ref.getRegionId() != null && grantedRegionIds(userId).contains(ref.getRegionId())) {
            return true;
        }
        return false;
    }

    /** For a list-scope pre-filter: all area ids visible to the user (FO + explicit + region-derived). */
    public Set<UUID> visibleAreaIds(UUID userId) {
        Set<UUID> ids = new HashSet<>(grantedAreaIds(userId));
        Set<UUID> regions = grantedRegionIds(userId);
        if (!regions.isEmpty()) {
            for (var area : areaRepo.findByDeletedFalse()) {
                if (regions.contains(area.getRegionId())) ids.add(area.getId());
            }
        }
        return ids;
    }

    public List<UserScope> scopesOf(UUID userId) {
        return userScopeRepo.findByUserIdAndDeletedFalse(userId);
    }
}
