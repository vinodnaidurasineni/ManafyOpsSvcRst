package com.manafy.ops.common.authz.repository;

import com.manafy.ops.common.authz.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCodeAndDeletedFalse(String code);
    List<Role> findByDeletedFalse();
    boolean existsByCode(String code);
}
