package com.manafy.ops.common.security;

import com.manafy.ops.org.repository.AreaRepository;
import com.manafy.ops.org.repository.RegionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves a resource id to its scope anchors ({@link ResourceRef}) using the
 * resource's PERSISTED columns — the server-side source of truth for scope.
 *
 * Foundation implements region/area resolution. Domain phases register their own
 * resolvers (apartments, service requests, assignments, vendors) as those tables
 * land; the authorization layer stays unchanged.
 */
@Service
public class ResourceScopeResolver {

    private final RegionRepository regionRepo;
    private final AreaRepository areaRepo;

    public ResourceScopeResolver(RegionRepository regionRepo, AreaRepository areaRepo) {
        this.regionRepo = regionRepo;
        this.areaRepo = areaRepo;
    }

    /** Resolve a region resource → ref anchored on itself. */
    public ResourceRef region(UUID regionId) {
        // Existence is validated by the caller/controller; anchor is the region itself.
        return ResourceRef.of("REGION", regionId).region(regionId);
    }

    /** Resolve an area resource → ref anchored on the area AND its parent region. */
    public ResourceRef area(UUID areaId) {
        ResourceRef ref = ResourceRef.of("AREA", areaId).area(areaId);
        areaRepo.findByIdAndDeletedFalse(areaId).ifPresent(a -> ref.region(a.getRegionId()));
        return ref;
    }

    /**
     * Resolve an apartment to its scope anchors (Phase 2, DD-46). Anchored on the
     * apartment id AND its area AND the area's region — so ScopeService.covers()
     * grants access via APARTMENT scope, AREA scope (incl. Field Officer
     * area-ownership), or REGION→AREA inheritance, all resolved server-side.
     *
     * @param apartmentId the apartment id
     * @param areaId      the apartment's persisted area_id (server-resolved, never client)
     * @param regionId    the apartment's persisted region_id
     */
    public ResourceRef apartment(UUID apartmentId, UUID areaId, UUID regionId) {
        ResourceRef ref = ResourceRef.of("APARTMENT", apartmentId).apartment(apartmentId);
        if (areaId != null) ref.area(areaId);
        if (regionId != null) {
            ref.region(regionId);
        } else if (areaId != null) {
            areaRepo.findByIdAndDeletedFalse(areaId).ifPresent(a -> ref.region(a.getRegionId()));
        }
        return ref;
    }

    /**
     * Resolve a service request to its scope anchors (Phase 4A). Anchored on the
     * request's apartment AND area AND region — all persisted on the service_request
     * row (denormalized from the apartment at create time). ScopeService.covers()
     * therefore grants access via APARTMENT scope, AREA scope (incl. Field Officer
     * area-ownership), or REGION→AREA inheritance — the same server-side model as
     * apartments, so a caller cannot reach another request by changing the id.
     *
     * @param serviceRequestId the service request id (resource identity)
     * @param apartmentId      the request's persisted apartment_id
     * @param areaId           the request's persisted area_id (server-resolved, never client)
     * @param regionId         the request's persisted region_id
     */
    public ResourceRef serviceRequest(UUID serviceRequestId, UUID apartmentId, UUID areaId, UUID regionId) {
        ResourceRef ref = ResourceRef.of("SERVICE_REQUEST", serviceRequestId);
        if (apartmentId != null) ref.apartment(apartmentId);
        if (areaId != null) ref.area(areaId);
        if (regionId != null) {
            ref.region(regionId);
        } else if (areaId != null) {
            areaRepo.findByIdAndDeletedFalse(areaId).ifPresent(a -> ref.region(a.getRegionId()));
        }
        return ref;
    }

    /**
     * Resolve an assignment to its scope anchors (Phase 4B). An assignment is scoped
     * through its parent service request's apartment/area/region (the request's
     * persisted columns), so a Field Officer / coordinator can only reach an
     * assignment for a request in their authorized area — resolved server-side, so an
     * assignment id cannot be used to reach a request outside scope.
     *
     * @param assignmentId the assignment id (resource identity)
     * @param apartmentId  the parent request's apartment_id
     * @param areaId       the parent request's area_id
     * @param regionId     the parent request's region_id
     */
    public ResourceRef assignment(UUID assignmentId, UUID apartmentId, UUID areaId, UUID regionId) {
        ResourceRef ref = ResourceRef.of("ASSIGNMENT", assignmentId);
        if (apartmentId != null) ref.apartment(apartmentId);
        if (areaId != null) ref.area(areaId);
        if (regionId != null) {
            ref.region(regionId);
        } else if (areaId != null) {
            areaRepo.findByIdAndDeletedFalse(areaId).ifPresent(a -> ref.region(a.getRegionId()));
        }
        return ref;
    }
}
