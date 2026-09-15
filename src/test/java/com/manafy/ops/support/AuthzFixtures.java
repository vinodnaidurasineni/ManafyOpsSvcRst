package com.manafy.ops.support;

import com.manafy.ops.common.authz.entity.UserRole;
import com.manafy.ops.common.authz.entity.UserScope;
import com.manafy.ops.common.authz.repository.RoleRepository;
import com.manafy.ops.common.authz.repository.UserRoleRepository;
import com.manafy.ops.common.authz.repository.UserScopeRepository;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test helper to seed users, role assignments and scope grants against the
 * (already Flyway-seeded) role/permission catalog.
 */
@Component
public class AuthzFixtures {

    private final OpsUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final UserScopeRepository userScopeRepo;

    public AuthzFixtures(OpsUserRepository userRepo, RoleRepository roleRepo,
                         UserRoleRepository userRoleRepo, UserScopeRepository userScopeRepo,
                         com.manafy.ops.org.repository.AreaFieldOfficerRepository afoRepo) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.userScopeRepo = userScopeRepo;
        this.afoRepo = afoRepo;
    }

    public OpsUser createUser(String name, String status) {
        OpsUser u = new OpsUser();
        u.setDisplayName(name);
        u.setCognitoSub("sub-" + UUID.randomUUID());
        u.setStatus(status);
        return userRepo.save(u);
    }

    /** Create a user with an explicit Cognito subject (for HTTP integration tests). */
    public OpsUser createUserWithSub(String name, String sub, String status) {
        OpsUser u = new OpsUser();
        u.setDisplayName(name);
        u.setCognitoSub(sub);
        u.setStatus(status);
        return userRepo.save(u);
    }

    public OpsUser createActiveUser(String name) {
        return createUser(name, "ACTIVE");
    }

    public void assignRole(UUID userId, String roleCode) {
        var role = roleRepo.findByCodeAndDeletedFalse(roleCode).orElseThrow();
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        ur.setAssignedAt(LocalDateTime.now());
        ur.setStatus("ACTIVE");
        userRoleRepo.save(ur);
    }

    public void grantGlobalScope(UUID userId) {
        UserScope s = new UserScope();
        s.setUserId(userId);
        s.setScopeType("GLOBAL");
        userScopeRepo.save(s);
    }

    public void grantRegionScope(UUID userId, UUID regionId) {
        UserScope s = new UserScope();
        s.setUserId(userId);
        s.setScopeType("REGION");
        s.setRegionId(regionId);
        userScopeRepo.save(s);
    }

    public void grantAreaScope(UUID userId, UUID areaId) {
        UserScope s = new UserScope();
        s.setUserId(userId);
        s.setScopeType("AREA");
        s.setAreaId(areaId);
        userScopeRepo.save(s);
    }

    // ─── Area ↔ Field Officer (authoritative source) ────────────────

    private final com.manafy.ops.org.repository.AreaFieldOfficerRepository afoRepo;

    public com.manafy.ops.org.entity.AreaFieldOfficer assignFieldOfficer(
            UUID areaId, UUID fieldOfficerId, String designation) {
        var afo = new com.manafy.ops.org.entity.AreaFieldOfficer();
        afo.setAreaId(areaId);
        afo.setFieldOfficerId(fieldOfficerId);
        afo.setDesignation(designation == null ? "PRIMARY" : designation);
        afo.setEffectiveFrom(LocalDateTime.now());
        return afoRepo.save(afo);
    }

    public void endFieldOfficerAssignment(UUID assignmentId) {
        afoRepo.findById(assignmentId).ifPresent(a -> {
            a.setEffectiveTo(LocalDateTime.now());
            afoRepo.save(a);
        });
    }

    // ─── Region / Area (Phase 2 fixtures) ───────────────────────────

    public com.manafy.ops.org.entity.Region region(String label) {
        com.manafy.ops.org.entity.Region r = new com.manafy.ops.org.entity.Region();
        r.setCode("R-" + UUID.randomUUID());
        r.setName(label);
        return regionRepoField.save(r);
    }

    public com.manafy.ops.org.entity.Area area(String label, UUID regionId) {
        com.manafy.ops.org.entity.Area a = new com.manafy.ops.org.entity.Area();
        a.setCode("A-" + UUID.randomUUID());
        a.setName(label);
        a.setRegionId(regionId);
        return areaRepoField.save(a);
    }

    // Wired lazily via setter-injection-free field assignment in constructor overload.
    private com.manafy.ops.org.repository.RegionRepository regionRepoField;
    private com.manafy.ops.org.repository.AreaRepository areaRepoField;

    @org.springframework.beans.factory.annotation.Autowired
    public void setOrgRepos(com.manafy.ops.org.repository.RegionRepository regionRepo,
                            com.manafy.ops.org.repository.AreaRepository areaRepo) {
        this.regionRepoField = regionRepo;
        this.areaRepoField = areaRepo;
    }
}
