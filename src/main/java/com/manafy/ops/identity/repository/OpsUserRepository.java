package com.manafy.ops.identity.repository;

import com.manafy.ops.identity.entity.OpsUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OpsUserRepository extends JpaRepository<OpsUser, UUID> {
    Optional<OpsUser> findByCognitoSubAndDeletedFalse(String cognitoSub);
    Optional<OpsUser> findByIdAndDeletedFalse(UUID id);
    Optional<OpsUser> findByEmailAndDeletedFalse(String email);
}
