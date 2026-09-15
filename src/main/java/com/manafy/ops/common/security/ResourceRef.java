package com.manafy.ops.common.security;

import java.util.UUID;

/**
 * A server-resolved reference to a resource being authorized, carrying its scope
 * anchors (region/area/apartment/vendor) and owner where relevant.
 *
 * CRITICAL (IDOR defense): these anchors are resolved SERVER-SIDE from the
 * resource's persisted columns — never taken from client input. The authorization
 * layer compares them to the caller's grants; a client cannot gain access by
 * supplying a different UUID.
 *
 * All fields are nullable; only the anchors relevant to the resource are set.
 */
public class ResourceRef {

    private final String resourceType;
    private final UUID resourceId;
    private UUID regionId;
    private UUID areaId;
    private UUID apartmentId;
    private UUID vendorId;
    private UUID ownerUserId;   // for SELF / OWNER predicates
    private UUID assigneeUserId; // for ASSIGNED predicate (later phases)

    public ResourceRef(String resourceType, UUID resourceId) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public static ResourceRef of(String type, UUID id) {
        return new ResourceRef(type, id);
    }

    public ResourceRef region(UUID regionId) { this.regionId = regionId; return this; }
    public ResourceRef area(UUID areaId) { this.areaId = areaId; return this; }
    public ResourceRef apartment(UUID apartmentId) { this.apartmentId = apartmentId; return this; }
    public ResourceRef vendor(UUID vendorId) { this.vendorId = vendorId; return this; }
    public ResourceRef owner(UUID ownerUserId) { this.ownerUserId = ownerUserId; return this; }
    public ResourceRef assignee(UUID assigneeUserId) { this.assigneeUserId = assigneeUserId; return this; }

    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public UUID getRegionId() { return regionId; }
    public UUID getAreaId() { return areaId; }
    public UUID getApartmentId() { return apartmentId; }
    public UUID getVendorId() { return vendorId; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public UUID getAssigneeUserId() { return assigneeUserId; }
}
