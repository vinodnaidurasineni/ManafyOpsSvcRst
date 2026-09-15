package com.manafy.ops.common.authz.repository;

import com.manafy.ops.common.authz.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCodeAndDeletedFalse(String code);
    List<Permission> findByDeletedFalseOrderByDomainAscCodeAsc();
}
