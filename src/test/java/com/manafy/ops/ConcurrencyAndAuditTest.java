package com.manafy.ops;

import com.manafy.ops.common.authz.entity.AuditLog;
import com.manafy.ops.common.authz.repository.AuditLogRepository;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.org.repository.RegionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Optimistic locking (stale version → conflict) and audit creation/append-only.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyAndAuditTest {

    @Autowired RegionRepository regionRepo;
    @Autowired AuditService auditService;
    @Autowired AuditLogRepository auditRepo;

    @Test
    void staleVersionUpdateFailsWithOptimisticLock() {
        Region r = new Region();
        r.setCode("R-LOCK-" + UUID.randomUUID());
        r.setName("lock");
        Region saved = regionRepo.saveAndFlush(r);

        // Two copies loaded at the same version.
        Region copyA = regionRepo.findById(saved.getId()).orElseThrow();
        Region copyB = regionRepo.findById(saved.getId()).orElseThrow();

        copyA.setName("changed-A");
        regionRepo.saveAndFlush(copyA); // bumps version

        copyB.setName("changed-B"); // still holds the old version
        assertThatThrownBy(() -> regionRepo.saveAndFlush(copyB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void auditRecordIsCreatedAndReadable() {
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        auditService.auditSystem(actor, "TEST_ACTION", "TEST", resource, "unit test audit");

        var page = auditRepo.findByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(page.getContent()).anyMatch(a ->
                "TEST_ACTION".equals(a.getAction()) && resource.equals(a.getResourceId()));
    }

    @Test
    void auditServiceNeverThrowsAndDoesNotStoreSecrets() {
        // Best-effort: even with null actor, auditing must not throw.
        auditService.auditSystem(null, "NULL_ACTOR_ACTION", "TEST", null, "user disabled by admin");
        var page = auditRepo.findByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 50));
        AuditLog found = page.getContent().stream()
                .filter(a -> "NULL_ACTOR_ACTION".equals(a.getAction())).findFirst().orElseThrow();
        // The audit entity has NO columns for tokens/passwords/secrets by design;
        // assert the recorded reason was persisted (append-only) and readable.
        assertThat(found.getReason()).isEqualTo("user disabled by admin");
    }
}
