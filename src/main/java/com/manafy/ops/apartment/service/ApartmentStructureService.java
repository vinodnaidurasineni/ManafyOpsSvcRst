package com.manafy.ops.apartment.service;

import com.manafy.ops.apartment.dto.ChildResourceDtos.*;
import com.manafy.ops.apartment.entity.*;
import com.manafy.ops.apartment.repository.*;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Buildings / units / facilities management. All operations resolve the parent
 * apartment and authorize via its scope (parent-resource authorization) so a user
 * authorized for Apartment A cannot manipulate a building/unit of Apartment B by
 * changing ids (IDOR defense). Read requires APARTMENT_VIEW; mutations require the
 * relevant manage permission.
 */
@Service
public class ApartmentStructureService {

    private final ApartmentRepository apartmentRepo;
    private final ApartmentBuildingRepository buildingRepo;
    private final ApartmentUnitRepository unitRepo;
    private final ApartmentFacilityRepository facilityRepo;
    private final AuthorizationService authz;
    private final ResourceScopeResolver resolver;
    private final PermissionService permissionService;
    private final AuditService audit;

    public ApartmentStructureService(ApartmentRepository apartmentRepo, ApartmentBuildingRepository buildingRepo,
                                     ApartmentUnitRepository unitRepo, ApartmentFacilityRepository facilityRepo,
                                     AuthorizationService authz, ResourceScopeResolver resolver,
                                     PermissionService permissionService, AuditService audit) {
        this.apartmentRepo = apartmentRepo;
        this.buildingRepo = buildingRepo;
        this.unitRepo = unitRepo;
        this.facilityRepo = facilityRepo;
        this.authz = authz;
        this.resolver = resolver;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    private Apartment apartment(UUID id) {
        return apartmentRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Apartment not found"));
    }
    private ResourceRef ref(Apartment a) { return resolver.apartment(a.getId(), a.getAreaId(), a.getRegionId()); }
    private String role(UUID u) { return permissionService.effectiveRoleCodes(u).stream().sorted().findFirst().orElse(null); }
    private void version(long actual, Long expected) {
        if (expected != null && expected != actual)
            throw new BusinessException("CONCURRENCY_CONFLICT", "Modified concurrently", HttpStatus.CONFLICT);
    }

    // ─── Buildings ───────────────────────────────────────────────────

    @Transactional
    public ApartmentBuilding createBuilding(UUID actor, UUID apartmentId, BuildingCreateRequest req) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "BUILDING_MANAGE", ref(a));
        if (buildingRepo.existsByApartmentIdAndNameAndDeletedFalse(apartmentId, req.name())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Building name already exists", HttpStatus.CONFLICT);
        }
        ApartmentBuilding b = new ApartmentBuilding();
        b.setApartmentId(apartmentId);
        b.setName(req.name());
        b.setFloors(req.floors());
        ApartmentBuilding saved = buildingRepo.save(b);
        audit.audit(actor, role(actor), "BUILDING_CREATED", "APARTMENT_BUILDING", saved.getId(), null, null,
                "Building created for apartment " + apartmentId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ApartmentBuilding> listBuildings(UUID actor, UUID apartmentId) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_VIEW", ref(a));
        return buildingRepo.findByApartmentIdAndDeletedFalse(apartmentId);
    }

    /** Load a building and authorize via its PARENT apartment (IDOR-safe). */
    private ApartmentBuilding buildingAuthorized(UUID actor, UUID buildingId, String permission) {
        ApartmentBuilding b = buildingRepo.findByIdAndDeletedFalse(buildingId)
                .orElseThrow(() -> BusinessException.notFound("Building not found"));
        Apartment a = apartment(b.getApartmentId());
        authz.authorize(actor, permission, ref(a));
        return b;
    }

    @Transactional
    public ApartmentBuilding updateBuilding(UUID actor, UUID buildingId, BuildingUpdateRequest req) {
        ApartmentBuilding b = buildingAuthorized(actor, buildingId, "BUILDING_MANAGE");
        version(b.getVersion(), req.version());
        if (req.name() != null) b.setName(req.name());
        if (req.floors() != null) b.setFloors(req.floors());
        if (req.status() != null) b.setStatus(req.status());
        ApartmentBuilding saved = buildingRepo.save(b);
        audit.audit(actor, role(actor), "BUILDING_UPDATED", "APARTMENT_BUILDING", saved.getId(), null, null, "Building updated");
        return saved;
    }

    @Transactional
    public void deleteBuilding(UUID actor, UUID buildingId) {
        ApartmentBuilding b = buildingAuthorized(actor, buildingId, "BUILDING_MANAGE");
        b.setDeleted(true);
        buildingRepo.save(b);
        audit.audit(actor, role(actor), "BUILDING_DELETED", "APARTMENT_BUILDING", b.getId(), null, null, "Building deleted");
    }

    // ─── Units ───────────────────────────────────────────────────────

    @Transactional
    public ApartmentUnit createUnit(UUID actor, UUID buildingId, UnitCreateRequest req) {
        ApartmentBuilding b = buildingAuthorized(actor, buildingId, "UNIT_MANAGE");
        if (unitRepo.existsByBuildingIdAndUnitNumberAndDeletedFalse(buildingId, req.unitNumber())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Unit number already exists", HttpStatus.CONFLICT);
        }
        ApartmentUnit u = new ApartmentUnit();
        u.setBuildingId(buildingId);
        u.setApartmentId(b.getApartmentId());
        u.setUnitNumber(req.unitNumber());
        u.setFloor(req.floor());
        u.setUnitType(req.unitType());
        ApartmentUnit saved = unitRepo.save(u);
        audit.audit(actor, role(actor), "UNIT_CREATED", "APARTMENT_UNIT", saved.getId(), null, null,
                "Unit created in building " + buildingId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ApartmentUnit> listUnits(UUID actor, UUID buildingId) {
        buildingAuthorized(actor, buildingId, "APARTMENT_VIEW");
        return unitRepo.findByBuildingIdAndDeletedFalse(buildingId);
    }

    private ApartmentUnit unitAuthorized(UUID actor, UUID unitId, String permission) {
        ApartmentUnit u = unitRepo.findByIdAndDeletedFalse(unitId)
                .orElseThrow(() -> BusinessException.notFound("Unit not found"));
        Apartment a = apartment(u.getApartmentId());
        authz.authorize(actor, permission, ref(a));
        return u;
    }

    @Transactional
    public ApartmentUnit updateUnit(UUID actor, UUID unitId, UnitUpdateRequest req) {
        ApartmentUnit u = unitAuthorized(actor, unitId, "UNIT_MANAGE");
        version(u.getVersion(), req.version());
        if (req.unitNumber() != null) u.setUnitNumber(req.unitNumber());
        if (req.floor() != null) u.setFloor(req.floor());
        if (req.unitType() != null) u.setUnitType(req.unitType());
        if (req.status() != null) u.setStatus(req.status());
        ApartmentUnit saved = unitRepo.save(u);
        audit.audit(actor, role(actor), "UNIT_UPDATED", "APARTMENT_UNIT", saved.getId(), null, null, "Unit updated");
        return saved;
    }

    @Transactional
    public void deleteUnit(UUID actor, UUID unitId) {
        ApartmentUnit u = unitAuthorized(actor, unitId, "UNIT_MANAGE");
        u.setDeleted(true);
        unitRepo.save(u);
        audit.audit(actor, role(actor), "UNIT_DELETED", "APARTMENT_UNIT", u.getId(), null, null, "Unit deleted");
    }

    // ─── Facilities ──────────────────────────────────────────────────

    @Transactional
    public ApartmentFacility createFacility(UUID actor, UUID apartmentId, FacilityRequest req) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "FACILITY_MANAGE", ref(a));
        ApartmentFacility f = new ApartmentFacility();
        f.setApartmentId(apartmentId);
        f.setName(req.name());
        if (req.facilityScope() != null) f.setFacilityScope(req.facilityScope());
        f.setBuildingId(req.buildingId());
        f.setMetadata(req.metadata());
        ApartmentFacility saved = facilityRepo.save(f);
        audit.audit(actor, role(actor), "FACILITY_CREATED", "APARTMENT_FACILITY", saved.getId(), null, null, "Facility created");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ApartmentFacility> listFacilities(UUID actor, UUID apartmentId) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_VIEW", ref(a));
        return facilityRepo.findByApartmentIdAndDeletedFalse(apartmentId);
    }

    @Transactional
    public ApartmentFacility updateFacility(UUID actor, UUID apartmentId, UUID facilityId, FacilityRequest req) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "FACILITY_MANAGE", ref(a));
        ApartmentFacility f = facilityRepo.findByIdAndDeletedFalse(facilityId)
                .orElseThrow(() -> BusinessException.notFound("Facility not found"));
        if (!f.getApartmentId().equals(apartmentId)) {
            throw BusinessException.notFound("Facility not found for this apartment"); // IDOR guard
        }
        if (req.name() != null) f.setName(req.name());
        if (req.facilityScope() != null) f.setFacilityScope(req.facilityScope());
        f.setBuildingId(req.buildingId());
        if (req.metadata() != null) f.setMetadata(req.metadata());
        ApartmentFacility saved = facilityRepo.save(f);
        audit.audit(actor, role(actor), "FACILITY_UPDATED", "APARTMENT_FACILITY", saved.getId(), null, null, "Facility updated");
        return saved;
    }

    @Transactional
    public void deleteFacility(UUID actor, UUID apartmentId, UUID facilityId) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "FACILITY_MANAGE", ref(a));
        ApartmentFacility f = facilityRepo.findByIdAndDeletedFalse(facilityId)
                .orElseThrow(() -> BusinessException.notFound("Facility not found"));
        if (!f.getApartmentId().equals(apartmentId)) {
            throw BusinessException.notFound("Facility not found for this apartment");
        }
        f.setDeleted(true);
        facilityRepo.save(f);
        audit.audit(actor, role(actor), "FACILITY_DELETED", "APARTMENT_FACILITY", f.getId(), null, null, "Facility deleted");
    }
}
