package com.manafy.ops;

import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.org.repository.AreaRepository;
import com.manafy.ops.org.repository.RegionRepository;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-level authorization integration tests through the real controller +
 * security + authorization stack (MockMvc).
 */
class AuthorizationHttpIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;
    @Autowired RegionRepository regionRepo;
    @Autowired AreaRepository areaRepo;

    private Region region(String code) {
        Region r = new Region(); r.setCode(code + UUID.randomUUID()); r.setName(code); return regionRepo.save(r);
    }
    private Area area(String code, UUID regionId) {
        Area a = new Area(); a.setCode(code + UUID.randomUUID()); a.setName(code); a.setRegionId(regionId); return areaRepo.save(a);
    }

    // ─── Permission ──────────────────────────────────────────────────

    @Test
    void permissionAllowed() throws Exception {
        String sub = "http-perm-allow-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("admin", sub, "ACTIVE");
        fx.assignRole(u.getId(), "MANAFY_ADMIN");   // holds AREA_VIEW
        fx.grantGlobalScope(u.getId());
        mockMvc.perform(get("/api/v1/areas").with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void permissionDeniedReturns403() throws Exception {
        String sub = "http-perm-deny-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("nobody", sub, "ACTIVE");
        // No roles → no AREA_VIEW.
        mockMvc.perform(get("/api/v1/areas").with(asCognito(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ─── Area scope ──────────────────────────────────────────────────

    @Test
    void fieldOfficerAreaAllowedForOwnArea() throws Exception {
        String sub = "http-area-allow-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", sub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        Region r = region("RA"); Area own = area("AOWN", r.getId());
        fx.grantAreaScope(fo.getId(), own.getId());
        mockMvc.perform(get("/api/v1/areas/" + own.getId()).with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(own.getId().toString()));
    }

    @Test
    void fieldOfficerAreaDeniedForOtherArea() throws Exception {
        String sub = "http-area-deny-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", sub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        Region r = region("RA2");
        Area own = area("AOWN2", r.getId());
        Area other = area("AOTHER2", r.getId());
        fx.grantAreaScope(fo.getId(), own.getId());
        mockMvc.perform(get("/api/v1/areas/" + other.getId()).with(asCognito(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"));
    }

    // ─── Region scope ────────────────────────────────────────────────

    @Test
    void regionScopeAllowsAreaInThatRegion() throws Exception {
        String sub = "http-region-allow-" + UUID.randomUUID();
        OpsUser mgr = fx.createUserWithSub("mgr", sub, "ACTIVE");
        fx.assignRole(mgr.getId(), "AREA_OPERATIONS_MANAGER");
        Region rx = region("RX"); Area ax = area("AX", rx.getId());
        fx.grantRegionScope(mgr.getId(), rx.getId());
        mockMvc.perform(get("/api/v1/areas/" + ax.getId()).with(asCognito(sub)))
                .andExpect(status().isOk());
    }

    @Test
    void regionScopeDeniesAreaInDifferentRegion() throws Exception {
        String sub = "http-region-deny-" + UUID.randomUUID();
        OpsUser mgr = fx.createUserWithSub("mgr", sub, "ACTIVE");
        fx.assignRole(mgr.getId(), "AREA_OPERATIONS_MANAGER");
        Region rx = region("RX2"); Region ry = region("RY2");
        Area ay = area("AY2", ry.getId());
        fx.grantRegionScope(mgr.getId(), rx.getId());
        mockMvc.perform(get("/api/v1/areas/" + ay.getId()).with(asCognito(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"));
    }

    // ─── IDOR ────────────────────────────────────────────────────────

    @Test
    void idorSwappingUuidToOtherResourceIsDeniedAndBodyNotLeaked() throws Exception {
        String sub = "http-idor-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", sub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        Region r = region("RIDOR");
        Area mine = area("AMINE", r.getId());
        Area victim = area("AVICTIM", r.getId());
        fx.grantAreaScope(fo.getId(), mine.getId());

        // Authorized for own area.
        mockMvc.perform(get("/api/v1/areas/" + mine.getId()).with(asCognito(sub)))
                .andExpect(status().isOk());
        // Swapping the UUID to a resource outside scope must be denied and must
        // NOT return the protected resource body.
        mockMvc.perform(get("/api/v1/areas/" + victim.getId()).with(asCognito(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ─── Disabled user ───────────────────────────────────────────────

    @Test
    void disabledUserIsDenied() throws Exception {
        String sub = "http-disabled-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("disabled", sub, "DISABLED");
        fx.assignRole(u.getId(), "MANAFY_ADMIN");
        fx.grantGlobalScope(u.getId());
        mockMvc.perform(get("/api/v1/areas").with(asCognito(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_DISABLED"));
    }

    // ─── Unknown user (auto-provisioned, unprivileged) ───────────────

    @Test
    void unknownCognitoUserGetsNoPrivilegedAccess() throws Exception {
        String sub = "http-unknown-" + UUID.randomUUID();
        // No pre-seeded user; first request provisions an unprivileged OpsUser.
        // /auth/me is allowed (SELF) but a permission-gated endpoint must be 403.
        mockMvc.perform(get("/api/v1/auth/me").with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isEmpty());
        mockMvc.perform(get("/api/v1/areas").with(asCognito(sub)))
                .andExpect(status().isForbidden());
    }
}
