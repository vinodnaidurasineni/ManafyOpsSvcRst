package com.manafy.ops;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.*;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.org.repository.AreaRepository;
import com.manafy.ops.org.repository.RegionRepository;
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
 * Foundation authorization tests: permission gate, each scope type, IDOR denial,
 * cross-region/area denial, sensitive-permission assignability, FIELD_OFFICER
 * area resolution.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthorizationTest {

    @Autowired AuthzFixtures fx;
    @Autowired PermissionService permissionService;
    @Autowired ScopeService scopeService;
    @Autowired AuthorizationService authz;
    @Autowired ResourceScopeResolver resourceResolver;
    @Autowired RegionRepository regionRepo;
    @Autowired AreaRepository areaRepo;
    @Autowired com.manafy.ops.org.repository.AreaFieldOfficerRepository afoRepo;

    private Region region(String code) {
        Region r = new Region(); r.setCode(code); r.setName(code); return regionRepo.save(r);
    }
    private Area area(String code, UUID regionId) {
        Area a = new Area(); a.setCode(code); a.setName(code); a.setRegionId(regionId); return areaRepo.save(a);
    }

    // ─── Permission gate ─────────────────────────────────────────────

    @Test
    void permissionAllowedAndDenied() {
        OpsUser admin = fx.createActiveUser("admin");
        fx.assignRole(admin.getId(), "MANAFY_ADMIN");
        // MANAFY_ADMIN holds AREA_VIEW (foundation mapping).
        assertThat(permissionService.hasPermission(admin.getId(), "AREA_VIEW")).isTrue();
        // MANAFY_ADMIN does NOT hold PAYOUT_APPROVE.
        assertThat(permissionService.hasPermission(admin.getId(), "PAYOUT_APPROVE")).isFalse();
        assertThatThrownBy(() -> authz.requirePermission(admin.getId(), "PAYOUT_APPROVE"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("FORBIDDEN");
    }

    @Test
    void userWithNoRolesHasNoPermissions() {
        OpsUser nobody = fx.createActiveUser("nobody");
        assertThat(permissionService.effectivePermissionCodes(nobody.getId())).isEmpty();
        assertThatThrownBy(() -> authz.requirePermission(nobody.getId(), "AREA_VIEW"))
                .isInstanceOf(BusinessException.class);
    }

    // ─── Scope types ─────────────────────────────────────────────────

    @Test
    void globalScopeCoversEverything() {
        OpsUser u = fx.createActiveUser("g");
        fx.grantGlobalScope(u.getId());
        Region r = region("R-G"); Area a = area("A-G", r.getId());
        assertThat(scopeService.covers(u.getId(), resourceResolver.area(a.getId()))).isTrue();
    }

    @Test
    void areaScopeCoversOwnAreaOnly() {
        OpsUser u = fx.createActiveUser("a");
        Region r = region("R-A");
        Area own = area("A-OWN", r.getId());
        Area other = area("A-OTHER", r.getId());
        fx.grantAreaScope(u.getId(), own.getId());
        assertThat(scopeService.covers(u.getId(), resourceResolver.area(own.getId()))).isTrue();
        assertThat(scopeService.covers(u.getId(), resourceResolver.area(other.getId()))).isFalse();
    }

    @Test
    void regionScopeInheritsToAreasInThatRegionOnly() {
        OpsUser u = fx.createActiveUser("r");
        Region rx = region("R-X"); Region ry = region("R-Y");
        Area ax = area("A-X", rx.getId());
        Area ay = area("A-Y", ry.getId());
        fx.grantRegionScope(u.getId(), rx.getId());
        // area in granted region → allowed
        assertThat(scopeService.covers(u.getId(), resourceResolver.area(ax.getId()))).isTrue();
        // area in a different region → denied
        assertThat(scopeService.covers(u.getId(), resourceResolver.area(ay.getId()))).isFalse();
    }

    // ─── IDOR / cross-scope ──────────────────────────────────────────

    @Test
    void idorChangingResourceUuidToOutOfScopeIsDenied() {
        OpsUser fo = fx.createActiveUser("fo");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        Region r = region("R-IDOR");
        Area mine = area("A-MINE", r.getId());
        Area notMine = area("A-NOTMINE", r.getId());
        fx.grantAreaScope(fo.getId(), mine.getId());

        // Full gate: permission + scope over the resource resolved server-side.
        authz.authorize(fo.getId(), "AREA_VIEW", resourceResolver.area(mine.getId()));
        // Swapping to a UUID outside scope must be denied (SCOPE_DENIED), not allowed.
        assertThatThrownBy(() ->
                authz.authorize(fo.getId(), "AREA_VIEW", resourceResolver.area(notMine.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SCOPE_DENIED");
    }

    // ─── FIELD_OFFICER area resolution (C-1 / DD-20) ─────────────────

    @Test
    void fieldOfficerAreaResolutionFromAssignment() {
        OpsUser fo = fx.createActiveUser("fo2");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        Region r = region("R-FO");
        Area a = area("A-FO", r.getId());

        // No scope yet → not covered.
        assertThat(scopeService.covers(fo.getId(), resourceResolver.area(a.getId()))).isFalse();

        // Assign as current Field Officer of the area (authoritative source).
        var afo = new com.manafy.ops.org.entity.AreaFieldOfficer();
        afo.setAreaId(a.getId());
        afo.setFieldOfficerId(fo.getId());
        afo.setDesignation("PRIMARY");
        afo.setEffectiveFrom(java.time.LocalDateTime.now());
        afoRepo.save(afo);

        // Now the FO's AREA scope is derived from area_field_officer → covered.
        assertThat(scopeService.grantedAreaIds(fo.getId())).contains(a.getId());
        assertThat(scopeService.covers(fo.getId(), resourceResolver.area(a.getId()))).isTrue();
    }

    // ─── Sensitive permission: role assignability (§119) ─────────────

    @Test
    void nonSuperAdminCannotAssignSuperAdmin() {
        OpsUser admin = fx.createActiveUser("adminA");
        fx.assignRole(admin.getId(), "MANAFY_ADMIN");
        assertThatThrownBy(() -> authz.requireCanAssignRole(admin.getId(), "SUPER_ADMIN"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ROLE_NOT_ASSIGNABLE");
    }

    @Test
    void superAdminCanAssignAnyRole() {
        OpsUser su = fx.createActiveUser("su");
        fx.assignRole(su.getId(), "SUPER_ADMIN");
        // Should not throw.
        authz.requireCanAssignRole(su.getId(), "SUPER_ADMIN");
        authz.requireCanAssignRole(su.getId(), "FIELD_OFFICER");
    }

    @Test
    void adminCanAssignNonSuperRole() {
        OpsUser admin = fx.createActiveUser("adminB");
        fx.assignRole(admin.getId(), "MANAFY_ADMIN");
        // MANAFY_ADMIN is the min-role for FIELD_OFFICER → allowed.
        authz.requireCanAssignRole(admin.getId(), "FIELD_OFFICER");
    }
}
