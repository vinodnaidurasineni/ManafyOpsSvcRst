package com.manafy.ops.apartment.dto;

import com.manafy.ops.apartment.dto.ApartmentDtos.ApartmentResponse;
import com.manafy.ops.apartment.dto.ChildResourceDtos.*;
import com.manafy.ops.apartment.dto.OnboardingDtos.*;
import com.manafy.ops.apartment.entity.*;

import java.util.List;

/** Maps entities → DTOs. Entities are never returned directly from controllers. */
public final class ApartmentMapper {

    private ApartmentMapper() {}

    private static String ts(java.time.LocalDateTime t) { return t == null ? null : t.toString(); }

    public static ApartmentResponse toApartment(Apartment a) {
        return new ApartmentResponse(a.getId(), a.getCode(), a.getName(), a.getLegalName(),
                a.getRegionId(), a.getAreaId(), a.getAddressLine1(), a.getAddressLine2(),
                a.getCity(), a.getState(), a.getCountry(), a.getPincode(),
                a.getLatitude(), a.getLongitude(), a.getStatus(), a.getManagementCompany(),
                a.getTimezone(), a.getAssignedFieldOfficerId(), a.getVersion());
    }

    public static BuildingResponse toBuilding(ApartmentBuilding b) {
        return new BuildingResponse(b.getId(), b.getApartmentId(), b.getName(), b.getFloors(), b.getStatus(), b.getVersion());
    }

    public static UnitResponse toUnit(ApartmentUnit u) {
        return new UnitResponse(u.getId(), u.getBuildingId(), u.getApartmentId(), u.getUnitNumber(),
                u.getFloor(), u.getUnitType(), u.getStatus(), u.getVersion());
    }

    public static FacilityResponse toFacility(ApartmentFacility f) {
        return new FacilityResponse(f.getId(), f.getApartmentId(), f.getBuildingId(), f.getFacilityScope(),
                f.getName(), f.getMetadata(), f.getStatus(), f.getVersion());
    }

    public static DocumentResponse toDocument(ApartmentDocument d) {
        return new DocumentResponse(d.getId(), d.getApartmentId(), d.getDocType(), d.getFileName(),
                d.getObjectKey(), d.getContentType(), d.getSizeBytes(), d.getStatus(), d.getVersion());
    }

    public static ApartmentServiceResponse toService(ApartmentServiceConfig s) {
        return new ApartmentServiceResponse(s.getId(), s.getApartmentId(), s.getServiceCode(),
                s.getServiceName(), s.getState(), s.getVersion());
    }

    public static ChecklistItemResponse toChecklistItem(OnboardingChecklistItem i) {
        return new ChecklistItemResponse(i.getItemKey(), i.getLabel(), i.isMandatory(), i.isComplete(),
                i.getCompletedBy(), ts(i.getCompletedAt()));
    }

    public static OnboardingResponse toOnboarding(ApartmentOnboarding o, List<OnboardingChecklistItem> checklist) {
        List<ChecklistItemResponse> items = checklist == null ? List.of()
                : checklist.stream().map(ApartmentMapper::toChecklistItem).toList();
        return new OnboardingResponse(o.getId(), o.getApartmentId(), o.getStatus(),
                o.getStartedBy(), ts(o.getSubmittedAt()), o.getVerifiedBy(), ts(o.getVerifiedAt()),
                o.getRejectedBy(), ts(o.getRejectedAt()), o.getRejectionReason(), ts(o.getResubmittedAt()),
                o.getVersion(), items);
    }

    public static StatusHistoryResponse toHistory(OnboardingStatusHistory h) {
        return new StatusHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getChangedBy(),
                h.getReason(), ts(h.getCreatedAt()));
    }
}
