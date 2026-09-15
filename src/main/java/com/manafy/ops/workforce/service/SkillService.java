package com.manafy.ops.workforce.service;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.Skill;
import com.manafy.ops.workforce.repository.SkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Skill master data (Phase 3 §11). GLOBAL master data; SKILL_MANAGE to mutate, SKILL_VIEW to read. */
@Service
public class SkillService {

    private final SkillRepository skillRepo;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final AuditService audit;

    public SkillService(SkillRepository skillRepo, AuthorizationService authz,
                        PermissionService permissionService, AuditService audit) {
        this.skillRepo = skillRepo;
        this.authz = authz;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    private String role(UUID u) { return permissionService.effectiveRoleCodes(u).stream().sorted().findFirst().orElse(null); }

    @Transactional(readOnly = true)
    public List<Skill> list(UUID actor) {
        authz.requirePermission(actor, "SKILL_VIEW");
        return skillRepo.findByDeletedFalse();
    }

    @Transactional
    public Skill create(UUID actor, SkillCreateRequest req) {
        authz.requirePermission(actor, "SKILL_MANAGE");
        if (skillRepo.existsByCode(req.code())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Skill code already exists", HttpStatus.CONFLICT);
        }
        Skill s = new Skill();
        s.setCode(req.code());
        s.setName(req.name());
        s.setCategory(req.category());
        Skill saved = skillRepo.save(s);
        audit.audit(actor, role(actor), "SKILL_CREATED", "SKILL", saved.getId(), null, null, "Skill created: " + req.code());
        return saved;
    }

    @Transactional
    public Skill update(UUID actor, UUID id, SkillUpdateRequest req) {
        authz.requirePermission(actor, "SKILL_MANAGE");
        Skill s = skillRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Skill not found"));
        if (req.version() != null && req.version() != s.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT", "Skill modified concurrently", HttpStatus.CONFLICT);
        }
        if (req.name() != null) s.setName(req.name());
        if (req.category() != null) s.setCategory(req.category());
        if (req.status() != null) s.setStatus(req.status());
        Skill saved = skillRepo.save(s);
        audit.audit(actor, role(actor), "SKILL_UPDATED", "SKILL", saved.getId(), null, null, "Skill updated");
        return saved;
    }

    @Transactional
    public void delete(UUID actor, UUID id) {
        authz.requirePermission(actor, "SKILL_MANAGE");
        Skill s = skillRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Skill not found"));
        s.setDeleted(true);
        skillRepo.save(s);
        audit.audit(actor, role(actor), "SKILL_DELETED", "SKILL", s.getId(), null, null, "Skill deleted");
    }

    public Skill requireActiveSkill(UUID skillId) {
        Skill s = skillRepo.findByIdAndDeletedFalse(skillId)
                .orElseThrow(() -> BusinessException.notFound("Skill not found: " + skillId));
        if (!"ACTIVE".equals(s.getStatus())) {
            throw BusinessException.validation("Skill is not ACTIVE: " + skillId);
        }
        return s;
    }
}
