package com.manafy.ops;

import com.manafy.ops.common.authz.repository.PermissionRepository;
import com.manafy.ops.common.authz.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: the Spring context loads and Flyway (V1–V6 + R__ seed) runs against
 * H2. Also asserts the 10 seed roles and the seeded permission catalog are present.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContextLoadTest {

    @Autowired RoleRepository roleRepo;
    @Autowired PermissionRepository permissionRepo;

    @Test
    void contextLoadsAndSeedApplied() {
        // 10 seed roles present.
        assertThat(roleRepo.findByDeletedFalse()).hasSize(10);
        assertThat(roleRepo.findByCodeAndDeletedFalse("SUPER_ADMIN")).isPresent();
        assertThat(roleRepo.findByCodeAndDeletedFalse("FIELD_OFFICER")).isPresent();

        // Foundation + full permission catalog seeded (exact codes).
        assertThat(permissionRepo.findByCodeAndDeletedFalse("USER_VIEW")).isPresent();
        assertThat(permissionRepo.findByCodeAndDeletedFalse("AREA_ASSIGN_OFFICER")).isPresent();
        assertThat(permissionRepo.findByCodeAndDeletedFalse("TECHNICIAN_KYC_VIEW")).isPresent();
        assertThat(permissionRepo.findByCodeAndDeletedFalse("ASSIGNMENT_EXECUTE")).isPresent();
    }
}
