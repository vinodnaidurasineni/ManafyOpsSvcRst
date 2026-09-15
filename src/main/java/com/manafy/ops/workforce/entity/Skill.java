package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

/** Reusable skill master data (Phase 3 §11). */
@Entity
@Table(name = "skill", uniqueConstraints = @UniqueConstraint(name = "uk_skill_code", columnNames = "code"))
public class Skill extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 80)
    private String category;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
