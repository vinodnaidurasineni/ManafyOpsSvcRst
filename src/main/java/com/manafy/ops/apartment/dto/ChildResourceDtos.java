package com.manafy.ops.apartment.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** DTOs for apartment child resources (buildings/units/facilities/contacts/documents/services). */
public final class ChildResourceDtos {

    private ChildResourceDtos() {}

    // Buildings
    public record BuildingCreateRequest(@NotBlank String name, Integer floors) {}
    public record BuildingUpdateRequest(String name, Integer floors, String status, Long version) {}
    public record BuildingResponse(UUID id, UUID apartmentId, String name, Integer floors, String status, long version) {}

    // Units
    public record UnitCreateRequest(@NotBlank String unitNumber, Integer floor, String unitType) {}
    public record UnitUpdateRequest(String unitNumber, Integer floor, String unitType, String status, Long version) {}
    public record UnitResponse(UUID id, UUID buildingId, UUID apartmentId, String unitNumber,
                               Integer floor, String unitType, String status, long version) {}

    // Facilities
    public record FacilityRequest(@NotBlank String name, String facilityScope, UUID buildingId, String metadata) {}
    public record FacilityResponse(UUID id, UUID apartmentId, UUID buildingId, String facilityScope,
                                   String name, String metadata, String status, long version) {}

    // Contacts (phone/email masked in responses unless APARTMENT_CONTACT_VIEW)
    public record ContactRequest(@NotBlank String contactType, @NotBlank String name,
                                 String phone, String email, String roleTitle) {}
    public record ContactResponse(UUID id, UUID apartmentId, String contactType, String name,
                                  String phone, String email, String roleTitle, boolean masked, long version) {}

    // Documents (metadata; bytes in object storage)
    public record DocumentCreateRequest(@NotBlank String docType, String fileName,
                                        String objectKey, String contentType, Long sizeBytes) {}
    public record DocumentResponse(UUID id, UUID apartmentId, String docType, String fileName,
                                   String objectKey, String contentType, Long sizeBytes,
                                   String status, long version) {}

    // Apartment services (enablement only)
    public record ApartmentServiceRequest(@NotBlank String serviceCode, String serviceName, String state) {}
    public record ApartmentServiceResponse(UUID id, UUID apartmentId, String serviceCode,
                                           String serviceName, String state, long version) {}
}
