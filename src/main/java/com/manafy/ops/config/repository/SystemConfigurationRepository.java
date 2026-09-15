package com.manafy.ops.config.repository;

import com.manafy.ops.config.entity.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, UUID> {
    Optional<SystemConfiguration> findByConfigKeyAndDeletedFalse(String configKey);
    List<SystemConfiguration> findByDeletedFalse();
}
