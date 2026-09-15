package com.manafy.ops.dispatch;

import com.manafy.ops.dispatch.service.TechnicianEligibilityService;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.servicerequest.entity.ServiceRequest;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.DispatchFixtures;
import com.manafy.ops.workforce.entity.Technician;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic technician eligibility rules (Phase 4B §8). Exercises skill, area,
 * availability, active-status and certification matching against persisted data.
 */
@SpringBootTest
@ActiveProfiles("test")
class TechnicianEligibilityTest {

    @Autowired TechnicianEligibilityService eligibility;
    @Autowired AuthzFixtures fx;
    @Autowired DispatchFixtures df;
    @Autowired com.manafy.ops.workforce.repository.CertificationRepository certRepo;

    private ServiceRequest requestIn(Area area, Region region, String category) {
        var apt = df.apartment(region.getId(), area.getId());
        UUID requester = fx.createActiveUser("req").getId();
        return df.serviceRequest(apt.getId(), area.getId(), region.getId(), requester, category);
    }

    @Test
    void fullyEligibleTechnicianMatches() {
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        ServiceRequest sr = requestIn(a, r, "PLUMBING");
        Technician t = df.eligibleTechnician(a.getId(), "PLUMBING");
        assertThat(eligibility.isEligible(sr, t)).isTrue();
    }

    @Test
    void inactiveTechnicianIsIneligible() {
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        ServiceRequest sr = requestIn(a, r, "PLUMBING");
        Technician t = df.technician("SUSPENDED", "AVAILABLE");
        df.coverArea(t.getId(), a.getId());
        df.grantSkill(t.getId(), df.skill("PLUMBING").getId());
        var reason = eligibility.evaluate(sr, t);
        assertThat(reason.eligible()).isFalse();
        assertThat(reason.reason()).contains("not ACTIVE");
    }

    @Test
    void technicianOutsideAreaIsIneligible() {
        Region r = fx.region("R"); Area a = fx.area("A", r.getId()); Area other = fx.area("B", r.getId());
        ServiceRequest sr = requestIn(a, r, "PLUMBING");
        Technician t = df.technician("ACTIVE", "AVAILABLE");
        df.coverArea(t.getId(), other.getId()); // covers a DIFFERENT area
        df.grantSkill(t.getId(), df.skill("PLUMBING").getId());
        assertThat(eligibility.evaluate(sr, t).reason()).contains("area");
    }

    @Test
    void missingSkillIsIneligible() {
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        ServiceRequest sr = requestIn(a, r, "PLUMBING");
        Technician t = df.technician("ACTIVE", "AVAILABLE");
        df.coverArea(t.getId(), a.getId());
        df.grantSkill(t.getId(), df.skill("ELECTRICAL").getId()); // wrong skill
        assertThat(eligibility.evaluate(sr, t).reason()).contains("skill");
    }

    @Test
    void onLeaveTechnicianIsIneligible() {
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        ServiceRequest sr = requestIn(a, r, "CLEANING");
        Technician t = df.technician("ACTIVE", "ON_LEAVE");
        df.coverArea(t.getId(), a.getId());
        df.grantSkill(t.getId(), df.skill("CLEANING").getId());
        assertThat(eligibility.evaluate(sr, t).reason()).contains("ON_LEAVE");
    }

    @Test
    void generalCategoryNeedsNoSkill() {
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        ServiceRequest sr = requestIn(a, r, "GENERAL");
        Technician t = df.technician("ACTIVE", "AVAILABLE");
        df.coverArea(t.getId(), a.getId()); // no skills granted
        assertThat(eligibility.isEligible(sr, t)).isTrue();
    }

    @Test
    void electricalRequiresValidCertification() {
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        ServiceRequest sr = requestIn(a, r, "ELECTRICAL");
        Technician t = df.technician("ACTIVE", "AVAILABLE");
        df.coverArea(t.getId(), a.getId());
        df.grantSkill(t.getId(), df.skill("ELECTRICAL").getId());
        // No certification yet → ineligible.
        assertThat(eligibility.evaluate(sr, t).reason()).contains("certification");
        // Add a valid ELECTRICAL certification → eligible.
        var cert = new com.manafy.ops.workforce.entity.Certification();
        cert.setWorkforceKind("TECHNICIAN");
        cert.setTechnicianId(t.getId());
        cert.setCertType("ELECTRICAL");
        cert.setExpiryDate(LocalDate.now().plusYears(1));
        cert.setStatus("ACTIVE");
        certRepo.save(cert);
        assertThat(eligibility.isEligible(sr, t)).isTrue();
    }

    @Test
    void expiredCertificationIsIneligible() {
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        ServiceRequest sr = requestIn(a, r, "HVAC");
        Technician t = df.technician("ACTIVE", "AVAILABLE");
        df.coverArea(t.getId(), a.getId());
        df.grantSkill(t.getId(), df.skill("HVAC").getId());
        var cert = new com.manafy.ops.workforce.entity.Certification();
        cert.setWorkforceKind("TECHNICIAN");
        cert.setTechnicianId(t.getId());
        cert.setCertType("HVAC");
        cert.setExpiryDate(LocalDate.now().minusDays(1)); // expired
        cert.setStatus("ACTIVE");
        certRepo.save(cert);
        assertThat(eligibility.evaluate(sr, t).reason()).contains("certification");
    }
}
