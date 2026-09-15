package com.manafy.ops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the repeatable authorization seed is idempotent: re-executing the same
 * guarded INSERT pattern (NOT EXISTS on the natural key) inserts nothing new.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeedIdempotencyTest {

    @Autowired JdbcTemplate jdbc;

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void reRunningSeedInsertsNothingNew() {
        int rolesBefore = count("role");
        int permsBefore = count("permission");
        int rpBefore = count("role_permission");

        // Re-run representative guarded seed inserts (same pattern as R__ migration).
        jdbc.update("INSERT INTO role (id, deleted, version, code, name, description, is_system, assignable_by_min_role, status) " +
                "SELECT RANDOM_UUID(), FALSE, 0, 'SUPER_ADMIN', 'Super Admin', 'x', TRUE, 'SUPER_ADMIN', 'ACTIVE' " +
                "WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'SUPER_ADMIN')");
        jdbc.update("INSERT INTO permission (id, deleted, version, code, name, domain, resource, action, is_sensitive) " +
                "SELECT RANDOM_UUID(), FALSE, 0, 'USER_VIEW', 'View users', 'USER', 'USER', 'VIEW', FALSE " +
                "WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code = 'USER_VIEW')");
        // Re-run the SUPER_ADMIN → all-permissions mapping.
        jdbc.update("INSERT INTO role_permission (id, deleted, version, role_id, permission_id) " +
                "SELECT RANDOM_UUID(), FALSE, 0, r.id, p.id FROM role r CROSS JOIN permission p " +
                "WHERE r.code = 'SUPER_ADMIN' " +
                "AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id)");

        assertThat(count("role")).isEqualTo(rolesBefore);
        assertThat(count("permission")).isEqualTo(permsBefore);
        assertThat(count("role_permission")).isEqualTo(rpBefore);
    }

    @Test
    void superAdminHasAllPermissions() {
        int perms = count("permission");
        Integer superPerms = jdbc.queryForObject(
                "SELECT COUNT(*) FROM role_permission rp JOIN role r ON rp.role_id = r.id WHERE r.code = 'SUPER_ADMIN'",
                Integer.class);
        assertThat(superPerms).isEqualTo(perms);
    }
}
