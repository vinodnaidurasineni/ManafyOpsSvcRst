package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/** Skill assigned to a workforce member (technician or helper). */
@Entity
@Table(name = "workforce_skill")
public class WorkforceSkill extends BaseEntity {

    @Column(name = "workforce_kind", nullable = false, length = 20)
    private String workforceKind;   // TECHNICIAN | HELPER

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "helper_id")
    private UUID helperId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Column(name = "skill_level", nullable = false, length = 20)
    private String skillLevel = "INTERMEDIATE";

    public String getWorkforceKind() { return workforceKind; }
    public void setWorkforceKind(String workforceKind) { this.workforceKind = workforceKind; }
    public UUID getTechnicianId() { return technicianId; }
    public void setTechnicianId(UUID technicianId) { this.technicianId = technicianId; }
    public UUID getHelperId() { return helperId; }
    public void setHelperId(UUID helperId) { this.helperId = helperId; }
    public UUID getSkillId() { return skillId; }
    public void setSkillId(UUID skillId) { this.skillId = skillId; }
    public String getSkillLevel() { return skillLevel; }
    public void setSkillLevel(String skillLevel) { this.skillLevel = skillLevel; }
}
