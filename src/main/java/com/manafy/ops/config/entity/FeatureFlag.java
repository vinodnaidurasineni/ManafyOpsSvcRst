package com.manafy.ops.config.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * A feature flag (Artifact #1 §8, spec §92). Flags are NOT an authorization
 * mechanism (design DD-28); they gate feature availability only.
 */
@Entity
@Table(name = "feature_flag",
        uniqueConstraints = @UniqueConstraint(name = "uk_feature_flag_key", columnNames = "flag_key"))
public class FeatureFlag extends BaseEntity {

    @Column(name = "flag_key", nullable = false, length = 100)
    private String flagKey;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = false;

    @Column(length = 500)
    private String description;

    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
