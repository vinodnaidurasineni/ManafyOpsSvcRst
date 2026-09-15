package com.manafy.ops.workforce.service;

import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.entity.WorkforceSkill;
import com.manafy.ops.workforce.entity.WorkforceStatusHistory;
import com.manafy.ops.workforce.repository.HelperRepository;
import com.manafy.ops.workforce.repository.TechnicianRepository;
import com.manafy.ops.workforce.repository.VendorRepository;
import com.manafy.ops.workforce.repository.WorkforceSkillRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Helper management (Phase 3 §8). Same lifecycle/authorization standards as
 * technicians; reuses WorkforceLifecycleService. Relationship (MANAFY/TECHNICIAN/
 * VENDOR) is explicit and validated.
 */
@Service
public class HelperService {

    private static final String KIND = "HELPER";

    private final HelperRepository helperRepo;
    private final TechnicianRepository technicianRepo;
    private final VendorRepository vendorRepo;
    private final WorkforceSkillRepository skillRepo;
    private final SkillService skillService;
    private final WorkforceLifecycleService lifecycle;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final IdempotencyService idempotency;

    public HelperService(HelperRepository helperRepo, TechnicianRepository technicianRepo,
                         VendorRepository vendorRepo, WorkforceSkillRepository skillRepo,
                         SkillService skillService, WorkforceLifecycleService lifecycle,
                         AuthorizationService authz, PermissionService permissionService,
                         AuditService audit, IdempotencyService idempotency) {
        this.helperRepo = helperRepo;
        this.technicianRepo = technicianRepo;
        this.vendorRepo = vendorRepo;
        this.skillRepo = skillRepo;
        this.skillService = skillService;
        this.lifecycle = lifecycle;
        this.authz = authz;
        this.permissionService = permissionService;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    private Helper load(UUID id) {
        return helperRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Helper not found"));
    }
    private String role(UUID u) { return permissionService.effectiveRoleCodes(u).stream().sorted().findFirst().orElse(null); }
    private void version(long actual, Long expected) {
        if (expected != null && expected != actual)
            throw new BusinessException("CONCURRENCY_CONFLICT", "Helper modified concurrently", HttpStatus.CONFLICT);
    }

    private void validateRelationship(String relationship, UUID technicianId, UUID vendorId) {
        if (!List.of("MANAFY", "TECHNICIAN", "VENDOR").contains(relationship)) {
            throw BusinessException.validation("Invalid relationship: " + relationship);
        }
        if ("TECHNICIAN".equals(relationship)) {
            if (technicianId == null) throw BusinessException.validation("technicianId required for TECHNICIAN relationship");
            technicianRepo.findByIdAndDeletedFalse(technicianId)
                    .orElseThrow(() -> BusinessException.validation("Technician not found"));
        }
        if ("VENDOR".equals(relationship)) {
            if (vendorId == null) throw BusinessException.validation("vendorId required for VENDOR relationship");
            vendorRepo.findByIdAndDeletedFalse(vendorId)
                    .orElseThrow(() -> BusinessException.validation("Vendor not found"));
        }
    }

    @Transactional
    public Helper create(UUID actor, HelperCreateRequest req, String idemKey) {
        authz.requirePermission(actor, "HELPER_CREATE");
        idempotency.register(idemKey, "POST /helpers", actor);
        if (helperRepo.existsByCode(req.code())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Helper code already exists", HttpStatus.CONFLICT);
        }
        validateRelationship(req.relationship(), req.technicianId(), req.vendorId());
        Helper h = new Helper();
        h.setCode(req.code());
        h.setName(req.name());
        h.setPhone(req.phone());
        h.setRelationship(req.relationship());
        h.setTechnicianId(req.technicianId());
        h.setVendorId(req.vendorId());
        h.setStatus("DRAFT");
        Helper saved = helperRepo.save(h);
        audit.audit(actor, role(actor), "HELPER_CREATED", KIND, saved.getId(), null, null, "Helper created: " + req.code());
        return saved;
    }

    @Transactional(readOnly = true)
    public Helper get(UUID actor, UUID id) {
        authz.requirePermission(actor, "HELPER_VIEW");
        return load(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<HelperListItemResponse> list(UUID actor, Integer page, Integer pageSize) {
        authz.requirePermission(actor, "HELPER_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        var result = helperRepo.findByDeletedFalse(PageRequest.of(p - 1, ps));
        List<HelperListItemResponse> data = result.getContent().stream()
                .map(h -> new HelperListItemResponse(h.getId(), h.getCode(), h.getName(), h.getRelationship(), h.getStatus()))
                .toList();
        return PageResponse.of(data, p, ps, result.getTotalElements());
    }

    @Transactional
    public Helper update(UUID actor, UUID id, HelperUpdateRequest req) {
        authz.requirePermission(actor, "HELPER_UPDATE");
        Helper h = load(id);
        version(h.getVersion(), req.version());
        if (req.name() != null) h.setName(req.name());
        if (req.phone() != null) h.setPhone(req.phone());
        if (req.relationship() != null) {
            validateRelationship(req.relationship(), req.technicianId(), req.vendorId());
            h.setRelationship(req.relationship());
            h.setTechnicianId(req.technicianId());
            h.setVendorId(req.vendorId());
        }
        Helper saved = helperRepo.save(h);
        audit.audit(actor, role(actor), "HELPER_UPDATED", KIND, saved.getId(), null, null, "Helper updated");
        return saved;
    }

    @Transactional
    public Helper changeStatus(UUID actor, UUID id, String permission, String to, String reason, String idemKey) {
        authz.requirePermission(actor, permission);
        idempotency.register(idemKey, "POST /helpers/{id}/" + to, actor);
        Helper h = load(id);
        lifecycle.transition(KIND, h.getId(), h.getStatus(), to, actor, reason);
        h.setStatus(to);
        if ("TERMINATED".equals(to)) h.setDeletedAt(LocalDateTime.now());
        return helperRepo.save(h);
    }

    // ─── Skills ──────────────────────────────────────────────────────

    @Transactional
    public WorkforceSkill addSkill(UUID actor, UUID helperId, UUID skillId, String level) {
        authz.requirePermission(actor, "WORKFORCE_SKILL_MANAGE");
        load(helperId);
        skillService.requireActiveSkill(skillId);
        if (skillRepo.findByHelperIdAndSkillIdAndDeletedFalse(helperId, skillId).isPresent()) {
            throw new BusinessException("RESOURCE_CONFLICT", "Skill already assigned", HttpStatus.CONFLICT);
        }
        WorkforceSkill ws = new WorkforceSkill();
        ws.setWorkforceKind(KIND);
        ws.setHelperId(helperId);
        ws.setSkillId(skillId);
        if (level != null) ws.setSkillLevel(level);
        WorkforceSkill saved = skillRepo.save(ws);
        audit.audit(actor, role(actor), "HELPER_SKILL_ADDED", KIND, helperId, null, null, "Skill added: " + skillId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<WorkforceSkill> listSkills(UUID actor, UUID helperId) {
        authz.requirePermission(actor, "HELPER_VIEW");
        load(helperId);
        return skillRepo.findByHelperIdAndDeletedFalse(helperId);
    }

    @Transactional(readOnly = true)
    public List<WorkforceStatusHistory> history(UUID actor, UUID helperId) {
        authz.requirePermission(actor, "HELPER_VIEW");
        load(helperId);
        return lifecycle.history(KIND, helperId);
    }
}
