package com.manafy.ops.common.authz.repository;

import com.manafy.ops.common.authz.entity.UserScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserScopeRepository extends JpaRepository<UserScope, UUID> {
    List<UserScope> findByUserIdAndDeletedFalse(UUID userId);
}
