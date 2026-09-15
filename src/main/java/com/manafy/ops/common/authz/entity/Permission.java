package com.manafy.ops.common.authz.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * A fine-grained permission in the format DOMAIN_RESOURCE_ACTION (Artifact #2 §8).
 * Stored in the DB and resolved at runtime — never hard-coded into business logic.
 */
@Entity
@Table(name = "permission", uniqueConstraints = @UniqueConstraint(name = "uk_permission_code", columnNames = "code"))
public class Permission extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** Grouping domain, e.g. APARTMENT / TECHNICIAN / ASSIGNMENT (for UI grouping §120). */
    @Column(length = 50)
    private String domain;

    @Column(length = 50)
    private String resource;

    @Column(length = 50)
    private String action;

    @Column(length = 255)
    private String description;

    /** Drives extra audit + confirmation (design DD-09, spec §114). */
    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive = false;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }
}
