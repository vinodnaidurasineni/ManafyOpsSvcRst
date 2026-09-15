package com.manafy.ops.apartment;

import com.manafy.ops.apartment.dto.ApartmentDtos.ApartmentCreateRequest;
import com.manafy.ops.apartment.entity.Apartment;
import com.manafy.ops.apartment.service.ApartmentAppService;
import com.manafy.ops.apartment.service.OnboardingAppService;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.support.AuthzFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-layer tests: region/area consistency, activation prerequisites, and
 * Field Officer assignment validation (business rules live in the service).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApartmentServiceTest {

    @Autowired AuthzFixtures fx;
    @Autowired ApartmentAppService apartmentService;
    @Autowired OnboardingAppService onboardingService;

    private UUID globalAdmin() {
        OpsUser u = fx.createActiveUser("admin");
        fx.assignRole(u.getId(), "MANAFY_ADMIN");
        fx.grantGlobalScope(u.getId());
        return u.getId();
    }

    private ApartmentCreateRequest req(String code, UUID regionId, UUID areaId) {
        return new ApartmentCreateRequest(code, "Apt " + code, null, regionId, areaId,
                "L1", null, "City", "State", "560001", null, null, null, null);
    }

    @Test
    void createRejectsAreaNotInRegion() {
        UUID admin = globalAdmin();
        Region r1 = fx.region("R1"); Region r2 = fx.region("R2");
        Area a2 = fx.area("A2", r2.getId());
        // region=r1 but area belongs to r2 → validation error.
        assertThatThrownBy(() -> apartmentService.create(admin, req("APT1", r1.getId(), a2.getId()), null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createSucceedsWithValidRegionArea() {
        UUID admin = globalAdmin();
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        Apartment apt = apartmentService.create(admin, req("APT-OK", r.getId(), a.getId()), null);
        assertThat(apt.getStatus()).isEqualTo("PROSPECT");
        assertThat(apt.getAreaId()).isEqualTo(a.getId());
    }

    @Test
    void activationRequiresVerifiedOnboarding() {
        UUID admin = globalAdmin();
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        Apartment apt = apartmentService.create(admin, req("APT-ACT", r.getId(), a.getId()), null);

        // No onboarding record → cannot activate.
        assertThatThrownBy(() -> apartmentService.activate(admin, apt.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ONBOARDING_INCOMPLETE");

        // Start onboarding (DRAFT) → still cannot activate (not VERIFIED).
        var onb = onboardingService.start(admin, apt.getId(), null);
        assertThatThrownBy(() -> apartmentService.activate(admin, apt.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ONBOARDING_INCOMPLETE");
    }

    @Test
    void verifyBlockedUntilMandatoryChecklistComplete() {
        UUID admin = globalAdmin();
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        Apartment apt = apartmentService.create(admin, req("APT-CHK", r.getId(), a.getId()), null);
        var onb = onboardingService.start(admin, apt.getId(), null);
        onboardingService.submit(admin, onb.getId(), null);
        // Mandatory checklist items are incomplete → verify blocked.
        assertThatThrownBy(() -> onboardingService.verify(admin, onb.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ONBOARDING_INCOMPLETE");
    }

    @Test
    void fullOnboardingToActivationHappyPath() {
        UUID admin = globalAdmin();
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        Apartment apt = apartmentService.create(admin, req("APT-HP", r.getId(), a.getId()), null);
        var onb = onboardingService.start(admin, apt.getId(), null);
        // Complete all mandatory checklist items (except VERIFICATION which is auto-completed).
        for (var item : onboardingService.checklist(onb.getId())) {
            if (item.isMandatory() && !"VERIFICATION".equals(item.getItemKey())) {
                onboardingService.updateChecklistItem(admin, onb.getId(), item.getItemKey(), true);
            }
        }
        onboardingService.submit(admin, onb.getId(), null);
        var verified = onboardingService.verify(admin, onb.getId(), null);
        assertThat(verified.getStatus()).isEqualTo("VERIFIED");
        Apartment active = apartmentService.activate(admin, apt.getId(), null);
        assertThat(active.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectRequiresReasonAndAllowsResubmit() {
        UUID admin = globalAdmin();
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        Apartment apt = apartmentService.create(admin, req("APT-RJ", r.getId(), a.getId()), null);
        var onb = onboardingService.start(admin, apt.getId(), null);
        onboardingService.submit(admin, onb.getId(), null);
        // Reject with a reason.
        var rejected = onboardingService.reject(admin, onb.getId(), "Missing documents", null);
        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getRejectionReason()).isEqualTo("Missing documents");
        // Resubmit retains rejection history.
        var resubmitted = onboardingService.resubmit(admin, onb.getId(), null);
        assertThat(resubmitted.getStatus()).isEqualTo("RESUBMITTED");
        assertThat(resubmitted.getRejectionReason()).isEqualTo("Missing documents");
    }

    @Test
    void fieldOfficerAssignmentRequiresAreaCoverage() {
        UUID admin = globalAdmin();
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        Apartment apt = apartmentService.create(admin, req("APT-FO", r.getId(), a.getId()), null);
        OpsUser fo = fx.createActiveUser("fo");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");

        // FO does not cover the area → assignment forbidden (no cross-area).
        assertThatThrownBy(() -> apartmentService.assignFieldOfficer(admin, apt.getId(), fo.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("FIELD_OFFICER_AREA_MISMATCH");

        // Make the FO a current officer of the area → assignment now allowed.
        fx.assignFieldOfficer(a.getId(), fo.getId(), "PRIMARY");
        Apartment assigned = apartmentService.assignFieldOfficer(admin, apt.getId(), fo.getId(), null);
        assertThat(assigned.getAssignedFieldOfficerId()).isEqualTo(fo.getId());
    }
}
