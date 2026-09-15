package com.manafy.ops.common.authz.repository;

import com.manafy.ops.common.authz.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    List<UserRole> findByUserIdAndStatusAndDeletedFalse(UUID userId, String status);
    Optional<UserRole> findByUserIdAndRoleIdAndDeletedFalse(UUID userId, UUID roleId);
}
