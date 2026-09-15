package com.manafy.ops.support;

import com.manafy.ops.apartment.entity.Apartment;
import com.manafy.ops.apartment.repository.ApartmentRepository;
import com.manafy.ops.servicerequest.entity.ServiceRequest;
import com.manafy.ops.servicerequest.repository.ServiceRequestRepository;
import com.manafy.ops.workforce.entity.Skill;
import com.manafy.ops.workforce.entity.Technician;
import com.manafy.ops.workforce.entity.WorkforceArea;
import com.manafy.ops.workforce.entity.WorkforceSkill;
import com.manafy.ops.workforce.repository.SkillRepository;
import com.manafy.ops.workforce.repository.TechnicianRepository;
import com.manafy.ops.workforce.repository.WorkforceAreaRepository;
import com.manafy.ops.workforce.repository.WorkforceSkillRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Phase 4B test helper: builds the apartment / technician / skill / area-coverage /
 * service-request graph directly via repositories so dispatch tests can focus on the
 * assignment lifecycle. (Uses persisted rows exactly as production reads them.)
 */
@Component
public class DispatchFixtures {

    private final ApartmentRepository apartmentRepo;
    private final ServiceRequestRepository srRepo;
    private final TechnicianRepository technicianRepo;
    private final SkillRepository skillRepo;
    private final WorkforceAreaRepository areaLinkRepo;
    private final WorkforceSkillRepository skillLinkRepo;

    public DispatchFixtures(ApartmentRepository apartmentRepo, ServiceRequestRepository srRepo,
                            TechnicianRepository technicianRepo, SkillRepository skillRepo,
                            WorkforceAreaRepository areaLinkRepo, WorkforceSkillRepository skillLinkRepo) {
        this.apartmentRepo = apartmentRepo;
        this.srRepo = srRepo;
        this.technicianRepo = technicianRepo;
        this.skillRepo = skillRepo;
        this.areaLinkRepo = areaLinkRepo;
        this.skillLinkRepo = skillLinkRepo;
    }

    public Apartment apartment(UUID regionId, UUID areaId) {
        Apartment a = new Apartment();
        a.setCode("APT-" + UUID.randomUUID());
        a.setName("Apt");
        a.setRegionId(regionId);
        a.setAreaId(areaId);
        a.setStatus("ACTIVE");
        return apartmentRepo.save(a);
    }

    /** A service request in NEW status for the given apartment/area/region + category. */
    public ServiceRequest serviceRequest(UUID apartmentId, UUID areaId, UUID regionId,
                                         UUID requesterId, String category) {
        ServiceRequest sr = new ServiceRequest();
        sr.setReferenceNo("SR-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        sr.setApartmentId(apartmentId);
        sr.setAreaId(areaId);
        sr.setRegionId(regionId);
        sr.setRequesterUserId(requesterId);
        sr.setCategory(category);
        sr.setPriority("MEDIUM");
        sr.setDescription("Test request " + category);
        sr.setStatus("NEW");
        return srRepo.save(sr);
    }

    /** Get-or-create an ACTIVE skill by code. */
    public Skill skill(String code) {
        if (skillRepo.existsByCode(code)) {
            return skillRepo.findByDeletedFalse().stream()
                    .filter(s -> code.equals(s.getCode())).findFirst().orElseThrow();
        }
        Skill s = new Skill();
        s.setCode(code);
        s.setName(code + " skill");
        s.setStatus("ACTIVE");
        return skillRepo.save(s);
    }

    /**
     * A technician that is fully eligible for the given area + skill code:
     * ACTIVE employment, AVAILABLE, covers the area, holds the (active) skill.
     */
    public Technician eligibleTechnician(UUID areaId, String skillCode) {
        Technician t = technician("ACTIVE", "AVAILABLE");
        coverArea(t.getId(), areaId);
        if (skillCode != null) grantSkill(t.getId(), skill(skillCode).getId());
        return t;
    }

    public Technician technician(String status, String availability) {
        Technician t = new Technician();
        t.setCode("T-" + UUID.randomUUID());
        t.setName("Tech");
        t.setStatus(status);
        t.setAvailabilityStatus(availability);
        return technicianRepo.save(t);
    }

    public WorkforceArea coverArea(UUID technicianId, UUID areaId) {
        WorkforceArea wa = new WorkforceArea();
        wa.setWorkforceKind("TECHNICIAN");
        wa.setTechnicianId(technicianId);
        wa.setAreaId(areaId);
        return areaLinkRepo.save(wa);
    }

    public WorkforceSkill grantSkill(UUID technicianId, UUID skillId) {
        WorkforceSkill ws = new WorkforceSkill();
        ws.setWorkforceKind("TECHNICIAN");
        ws.setTechnicianId(technicianId);
        ws.setSkillId(skillId);
        return skillLinkRepo.save(ws);
    }
}
