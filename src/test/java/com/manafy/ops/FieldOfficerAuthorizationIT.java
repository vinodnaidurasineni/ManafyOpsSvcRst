package com.manafy.ops;

import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.AreaFieldOfficer;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.org.repository.AreaRepository;
import com.manafy.ops.org.repository.RegionRepository;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Field Officer authorization resolution through HTTP, verifying that access
 * changes IMMEDIATELY with the authoritative area_field_officer relationship
 * (C-1/DD-20) — no second source of truth.
 *
 * Chain: user → FO identity → active area assignment → area → resources in area.
 */
class FieldOfficerAuthorizationIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;
    @Autowired RegionRepository regionRepo;
    @Autowired AreaRepository areaRepo;

    private Region region() {
        Region r = new Region(); r.setCode("R" + UUID.randomUUID()); r.setName("r"); return regionRepo.save(r);
    }
    private Area area(UUID regionId) {
        Area a = new Area(); a.setCode("A" + UUID.randomUUID()); a.setName("a"); a.setRegionId(regionId); return areaRepo.save(a);
    }

    private int getAreaStatus(String sub, UUID areaId) throws Exception {
        return mockMvc.perform(get("/api/v1/areas/" + areaId).with(asCognito(sub)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void currentAssignmentGrantsAccessExpiredRemovesIt() throws Exception {
        String sub = "fo-current-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", sub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        Area a = area(region().getId());

        // No assignment yet → denied.
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(sub, a.getId())).isEqualTo(403);

        // Current assignment → allowed immediately (derived from area_field_officer).
        AreaFieldOfficer afo = fx.assignFieldOfficer(a.getId(), fo.getId(), "PRIMARY");
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(sub, a.getId())).isEqualTo(200);

        // Expire the assignment → access removed immediately.
        fx.endFieldOfficerAssignment(afo.getId());
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(sub, a.getId())).isEqualTo(403);
    }

    @Test
    void secondaryDesignationAlsoGrantsAccess() throws Exception {
        String sub = "fo-secondary-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", sub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        Area a = area(region().getId());
        fx.assignFieldOfficer(a.getId(), fo.getId(), "SECONDARY");
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(sub, a.getId())).isEqualTo(200);
    }

    @Test
    void twoFieldOfficersOnSameAreaBothHaveAccess() throws Exception {
        String subA = "fo-two-a-" + UUID.randomUUID();
        String subB = "fo-two-b-" + UUID.randomUUID();
        OpsUser foA = fx.createUserWithSub("foA", subA, "ACTIVE");
        OpsUser foB = fx.createUserWithSub("foB", subB, "ACTIVE");
        fx.assignRole(foA.getId(), "FIELD_OFFICER");
        fx.assignRole(foB.getId(), "FIELD_OFFICER");
        Area a = area(region().getId());
        fx.assignFieldOfficer(a.getId(), foA.getId(), "PRIMARY");
        fx.assignFieldOfficer(a.getId(), foB.getId(), "SECONDARY");
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(subA, a.getId())).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(subB, a.getId())).isEqualTo(200);
    }

    @Test
    void reassignmentMovesAccessFromOldToNewOfficer() throws Exception {
        String subOld = "fo-old-" + UUID.randomUUID();
        String subNew = "fo-new-" + UUID.randomUUID();
        OpsUser foOld = fx.createUserWithSub("foOld", subOld, "ACTIVE");
        OpsUser foNew = fx.createUserWithSub("foNew", subNew, "ACTIVE");
        fx.assignRole(foOld.getId(), "FIELD_OFFICER");
        fx.assignRole(foNew.getId(), "FIELD_OFFICER");
        Area a = area(region().getId());

        AreaFieldOfficer oldAssignment = fx.assignFieldOfficer(a.getId(), foOld.getId(), "PRIMARY");
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(subOld, a.getId())).isEqualTo(200);

        // Reassign: end old, start new.
        fx.endFieldOfficerAssignment(oldAssignment.getId());
        fx.assignFieldOfficer(a.getId(), foNew.getId(), "PRIMARY");

        org.assertj.core.api.Assertions.assertThat(getAreaStatus(subOld, a.getId())).isEqualTo(403); // lost access
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(subNew, a.getId())).isEqualTo(200); // gained access
    }

    @Test
    void removedFieldOfficerLosesAccessImmediately() throws Exception {
        String sub = "fo-removed-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", sub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        Area a = area(region().getId());
        AreaFieldOfficer afo = fx.assignFieldOfficer(a.getId(), fo.getId(), "PRIMARY");
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(sub, a.getId())).isEqualTo(200);
        fx.endFieldOfficerAssignment(afo.getId());
        org.assertj.core.api.Assertions.assertThat(getAreaStatus(sub, a.getId())).isEqualTo(403);
    }
}
