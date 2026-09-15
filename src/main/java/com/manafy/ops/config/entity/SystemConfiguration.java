package com.manafy.ops.config.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * A business/system configuration setting (Artifact #1 §8, spec §91). Sensitive
 * keys require Super Admin to change (enforced in the service layer).
 */
@Entity
@Table(name = "system_configuration",
        uniqueConstraints = @UniqueConstraint(name = "uk_sysconfig_key", columnNames = "config_key"))
public class SystemConfiguration extends BaseEntity {

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    /** JSON/text value. */
    @Column(name = "config_value", length = 4000)
    private String configValue;

    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive = false;

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }
}
