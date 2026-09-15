package com.manafy.ops;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.RelationshipPredicate;
import com.manafy.ops.common.security.RelationshipPredicateResolver;
import com.manafy.ops.common.security.ResourceRef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Idempotency (duplicate key → 409) and relationship predicates (SELF resolved;
 * deferred predicates fail closed).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IdempotencyAndPredicateTest {

    @Autowired IdempotencyService idempotency;
    @Autowired RelationshipPredicateResolver predicateResolver;

    @Test
    void firstKeySucceedsDuplicateRejected() {
        String key = "idem-" + UUID.randomUUID();
        UUID user = UUID.randomUUID();
        idempotency.register(key, "POST /users", user);
        assertThatThrownBy(() -> idempotency.register(key, "POST /users", user))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("DUPLICATE_REQUEST");
    }

    @Test
    void blankKeyIsNoOp() {
        assertThatCode(() -> idempotency.register(null, "POST /users", UUID.randomUUID()))
                .doesNotThrowAnyException();
        assertThatCode(() -> idempotency.register("  ", "POST /users", UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void selfPredicateResolvesForOwner() {
        UUID actor = UUID.randomUUID();
        ResourceRef ref = ResourceRef.of("OPS_USER", actor).owner(actor);
        org.assertj.core.api.Assertions.assertThat(
                predicateResolver.resolves(actor, RelationshipPredicate.SELF, ref)).isTrue();
    }

    @Test
    void selfPredicateDeniesForNonOwner() {
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ResourceRef ref = ResourceRef.of("OPS_USER", other).owner(other);
        org.assertj.core.api.Assertions.assertThat(
                predicateResolver.resolves(actor, RelationshipPredicate.SELF, ref)).isFalse();
    }

    @Test
    void deferredPredicatesFailClosed() {
        UUID actor = UUID.randomUUID();
        ResourceRef ref = ResourceRef.of("ASSIGNMENT", UUID.randomUUID());
        assertThatThrownBy(() -> predicateResolver.resolves(actor, RelationshipPredicate.ASSIGNED, ref))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PREDICATE_NOT_AVAILABLE");
    }
}
