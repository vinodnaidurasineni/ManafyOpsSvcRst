package com.manafy.ops;

import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service identity mapping (service-boundary doc §11/§17).
 *
 * The Cognito `sub` is the immutable cross-service identity anchor. The SAME `sub`
 * maps to a Community `customer` (owned by ManafyCommunitySvcRst) AND to an Ops
 * `ops_user` (owned here). ops_user optionally carries `community_customer_id` as a
 * PROJECTION REFERENCE — NOT a database foreign key — so no shared database is
 * required. Ops is never the source of truth for the person's identity.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthIdentityMappingTest {

    @Autowired OpsUserRepository userRepo;

    @Test
    void sameSubAnchorsOpsUserAndCanReferenceCommunityCustomer() {
        String sharedSub = "cognito-sub-" + UUID.randomUUID();
        // The Community customer.id for the same human (lives in the SEPARATE Community
        // DB — represented here only as a reference value, never fetched cross-DB).
        UUID communityCustomerId = UUID.randomUUID();

        OpsUser u = new OpsUser();
        u.setCognitoSub(sharedSub);
        u.setDisplayName("Ops projection of a Community person");
        u.setStatus("ACTIVE");
        u.setCommunityCustomerId(communityCustomerId);
        OpsUser saved = userRepo.save(u);

        OpsUser reloaded = userRepo.findByCognitoSubAndDeletedFalse(sharedSub).orElseThrow();
        // Identity anchor is the sub; the community link is a plain reference.
        assertThat(reloaded.getCognitoSub()).isEqualTo(sharedSub);
        assertThat(reloaded.getCommunityCustomerId()).isEqualTo(communityCustomerId);
    }

    @Test
    void communityCustomerIdIsOptional() {
        // ops_user must remain usable with NO community link (nullable, non-breaking).
        OpsUser u = new OpsUser();
        u.setCognitoSub("cognito-sub-" + UUID.randomUUID());
        u.setDisplayName("Ops-only user");
        u.setStatus("ACTIVE");
        OpsUser saved = userRepo.save(u);
        assertThat(saved.getCommunityCustomerId()).isNull();
    }
}
