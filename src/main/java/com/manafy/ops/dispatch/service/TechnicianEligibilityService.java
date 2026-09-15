package com.manafy.ops.dispatch.service;

import com.manafy.ops.dispatch.dto.DispatchDtos.EligibilityReason;
import com.manafy.ops.dispatch.dto.DispatchDtos.EligibleTechnicianResponse;
import com.manafy.ops.dispatch.repository.AssignmentRepository;
import com.manafy.ops.servicerequest.entity.ServiceRequest;
import com.manafy.ops.workforce.entity.Certification;
import com.manafy.ops.workforce.entity.Skill;
import com.manafy.ops.workforce.entity.Technician;
import com.manafy.ops.workforce.entity.WorkforceArea;
import com.manafy.ops.workforce.entity.WorkforceSkill;
import com.manafy.ops.workforce.repository.CertificationRepository;
import com.manafy.ops.workforce.repository.SkillRepository;
import com.manafy.ops.workforce.repository.TechnicianRepository;
import com.manafy.ops.workforce.repository.WorkforceAreaRepository;
import com.manafy.ops.workforce.repository.WorkforceSkillRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic MVP technician eligibility (Phase 4B §8). NOT a ranking/optimization
 * engine — a rule pipeline that decides whether a technician may be assigned to a
 * request:
 *
 *   Service Request → required skill (category-derived) →
 *   technicians covering the request's apartment area →
 *   active employment status → availability → required skill held →
 *   certification not expired (when the category requires one).
 *
 * Returns a SAFE operational DTO (no KYC / finance / PII).
 */
@Service
public class TechnicianEligibilityService {

    /**
     * Category → canonical required skill code. Since there is no Service catalog yet,
     * the request category deterministically maps to a skill code. GENERAL/OTHER have
     * no hard skill requirement (any active, in-area, available technician qualifies).
     */
    private static final Map<String, String> CATEGORY_SKILL = Map.of(
            "PLUMBING", "PLUMBING",
            "ELECTRICAL", "ELECTRICAL",
            "HVAC", "HVAC",
            "CLEANING", "CLEANING",
            "SECURITY", "SECURITY");

    /** Categories that additionally require a non-expired certification. */
    private static final Map<String, String> CATEGORY_CERT = Map.of(
            "ELECTRICAL", "ELECTRICAL",
            "HVAC", "HVAC");

    private final TechnicianRepository technicianRepo;
    private final WorkforceAreaRepository areaLinkRepo;
    private final WorkforceSkillRepository skillLinkRepo;
    private final CertificationRepository certRepo;
    private final SkillRepository skillRepo;
    private final AssignmentRepository assignmentRepo;

    public TechnicianEligibilityService(TechnicianRepository technicianRepo, WorkforceAreaRepository areaLinkRepo,
                                        WorkforceSkillRepository skillLinkRepo, CertificationRepository certRepo,
                                        SkillRepository skillRepo, AssignmentRepository assignmentRepo) {
        this.technicianRepo = technicianRepo;
        this.areaLinkRepo = areaLinkRepo;
        this.skillLinkRepo = skillLinkRepo;
        this.certRepo = certRepo;
        this.skillRepo = skillRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /** The skill code required by a request category, or null if none. */
    public String requiredSkillCode(String category) {
        return CATEGORY_SKILL.get(category == null ? "" : category.toUpperCase());
    }

    /** Candidate technicians covering the request's area (all statuses; caller evaluates). */
    private List<Technician> techniciansCoveringArea(UUID areaId) {
        List<WorkforceArea> links = areaLinkRepo.findByAreaIdAndDeletedFalse(areaId);
        List<Technician> techs = new ArrayList<>();
        for (WorkforceArea link : links) {
            if (link.getTechnicianId() == null) continue;
            technicianRepo.findByIdAndDeletedFalse(link.getTechnicianId()).ifPresent(techs::add);
        }
        return techs;
    }

    private boolean holdsSkill(UUID technicianId, String requiredSkillCode) {
        if (requiredSkillCode == null) return true;
        List<WorkforceSkill> held = skillLinkRepo.findByTechnicianIdAndDeletedFalse(technicianId);
        for (WorkforceSkill ws : held) {
            Optional<Skill> s = skillRepo.findByIdAndDeletedFalse(ws.getSkillId());
            if (s.isPresent() && requiredSkillCode.equalsIgnoreCase(s.get().getCode())
                    && "ACTIVE".equals(s.get().getStatus())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasValidCertification(UUID technicianId, String requiredCertType) {
        if (requiredCertType == null) return true;
        List<Certification> certs = certRepo.findByTechnicianIdAndDeletedFalse(technicianId);
        LocalDate today = LocalDate.now();
        for (Certification c : certs) {
            boolean typeMatch = requiredCertType.equalsIgnoreCase(c.getCertType());
            boolean notRevoked = !"REVOKED".equals(c.getStatus());
            boolean notExpired = c.getExpiryDate() == null || !c.getExpiryDate().isBefore(today);
            if (typeMatch && notRevoked && notExpired) return true;
        }
        return false;
    }

    /**
     * Evaluate a single technician against a request. Returns the reason it is
     * eligible/ineligible (deterministic, first failing rule wins).
     */
    public EligibilityReason evaluate(ServiceRequest sr, Technician t) {
        if (!"ACTIVE".equals(t.getStatus())) {
            return EligibilityReason.fail("Technician is not ACTIVE (status=" + t.getStatus() + ")");
        }
        // Area coverage.
        boolean coversArea = areaLinkRepo
                .findByTechnicianIdAndAreaIdAndDeletedFalse(t.getId(), sr.getAreaId()).isPresent();
        if (!coversArea) {
            return EligibilityReason.fail("Technician does not cover the request area");
        }
        // Availability (ON_LEAVE / OFFLINE are not eligible; BUSY still eligible for scheduling).
        if ("ON_LEAVE".equals(t.getAvailabilityStatus()) || "OFFLINE".equals(t.getAvailabilityStatus())) {
            return EligibilityReason.fail("Technician is " + t.getAvailabilityStatus());
        }
        // Required skill.
        String requiredSkill = requiredSkillCode(sr.getCategory());
        if (!holdsSkill(t.getId(), requiredSkill)) {
            return EligibilityReason.fail("Missing required skill: " + requiredSkill);
        }
        // Certification (where the category requires one).
        String requiredCert = CATEGORY_CERT.get(sr.getCategory() == null ? "" : sr.getCategory().toUpperCase());
        if (!hasValidCertification(t.getId(), requiredCert)) {
            return EligibilityReason.fail("Missing/expired certification: " + requiredCert);
        }
        return EligibilityReason.pass();
    }

    /** True when the technician satisfies every eligibility rule for the request. */
    public boolean isEligible(ServiceRequest sr, Technician t) {
        return evaluate(sr, t).eligible();
    }

    /** Operational list of eligible technicians for a request (safe DTO; no KYC/finance/PII). */
    public List<EligibleTechnicianResponse> eligibleTechnicians(ServiceRequest sr) {
        List<EligibleTechnicianResponse> out = new ArrayList<>();
        for (Technician t : techniciansCoveringArea(sr.getAreaId())) {
            EligibilityReason reason = evaluate(sr, t);
            if (!reason.eligible()) continue;
            long activeJobs = assignmentRepo.countByTechnicianIdAndActiveTrueAndDeletedFalse(t.getId());
            List<String> skills = new ArrayList<>();
            for (WorkforceSkill ws : skillLinkRepo.findByTechnicianIdAndDeletedFalse(t.getId())) {
                skillRepo.findByIdAndDeletedFalse(ws.getSkillId()).ifPresent(s -> skills.add(s.getCode()));
            }
            out.add(new EligibleTechnicianResponse(t.getId(), t.getCode(), t.getName(), t.getVendorId(),
                    t.getAvailabilityStatus(), sr.getAreaId(), skills, activeJobs, t.getMaxConcurrentJobs()));
        }
        out.sort((a, b) -> {
            int cmp = Long.compare(a.currentActiveJobs(), b.currentActiveJobs());
            return cmp != 0 ? cmp : a.code().compareToIgnoreCase(b.code());
        });
        return out;
    }
}
