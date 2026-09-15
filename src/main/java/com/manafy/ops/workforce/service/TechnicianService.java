package com.manafy.ops.workforce.service;

import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.*;
import com.manafy.ops.org.repository.AreaRepository;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.*;
import com.manafy.ops.workforce.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Technician management (Phase 3 §6, §13, §14): CRUD, lifecycle, skills, areas,
 * certifications, availability. Documents are handled by WorkforceDocumentService.
 *
 * Scope: a technician is anchored by the areas it covers (workforce_area) — a user
 * authorized for that area (permission + scope) may view it. All child-resource
 * operations resolve through the parent technician (IDOR-safe).
 */
@Service
public class TechnicianService {

    private static final String KIND = "TECHNICIAN";

    private final TechnicianRepository technicianRepo;
    private final VendorRepository vendorRepo;
    private final WorkforceSkillRepository skillRepo;
    private final WorkforceAreaRepository areaLinkRepo;
    private final CertificationRepository certRepo;
    private final WorkforceAvailabilityRepository availRepo;
    private final AreaRepository areaRepo;
    private final SkillService skillService;
    private final WorkforceLifecycleService lifecycle;
    private final AuthorizationService authz;
    private final ScopeService scopeService;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final IdempotencyService idempotency;

    public TechnicianService(TechnicianRepository technicianRepo, VendorRepository vendorRepo,
                             WorkforceSkillRepository skillRepo, WorkforceAreaRepository areaLinkRepo,
                             CertificationRepository certRepo, WorkforceAvailabilityRepository availRepo,
                             AreaRepository areaRepo, SkillService skillService,
                             WorkforceLifecycleService lifecycle, AuthorizationService authz,
                             ScopeService scopeService, PermissionService permissionService,
                             AuditService audit, IdempotencyService idempotency) {
        this.technicianRepo = technicianRepo;
        this.vendorRepo = vendorRepo;
        this.skillRepo = skillRepo;
        this.areaLinkRepo = areaLinkRepo;
        this.certRepo = certRepo;
        this.availRepo = availRepo;
        this.areaRepo = areaRepo;
        this.skillService = skillService;
        this.lifecycle = lifecycle;
        this.authz = authz;
        this.scopeService = scopeService;
        this.permissionService = permissionService;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    private Technician load(UUID id) {
        return technicianRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Technician not found"));
    }
    private String role(UUID u) { return permissionService.effectiveRoleCodes(u).stream().sorted().findFirst().orElse(null); }
    private void version(long actual, Long expected) {
        if (expected != null && expected != actual)
            throw new BusinessException("CONCURRENCY_CONFLICT", "Technician modified concurrently", HttpStatus.CONFLICT);
    }

    /**
     * Authorize a permission over a technician, resolving scope from the areas it
     * covers. If the technician has no area yet, GLOBAL/permission holders pass;
     * area/region-scoped users are denied (fail-closed) unless they cover an area.
     */
    private void authorizeTechnician(UUID actor, String permission, Technician t) {
        // Permission gate always applies.
        authz.requirePermission(actor, permission);
        if (scopeService.hasGlobal(actor)) return;
        // Scope gate: does the actor cover any area this technician serves?
        var techAreas = areaLinkRepo.findByTechnicianIdAndDeletedFalse(t.getId());
        if (techAreas.isEmpty()) {
            // No area anchor: only GLOBAL-scoped users may access (already returned above).
            // A region/area-scoped user cannot see an unanchored technician → deny.
            throw BusinessException.scopeDenied();
        }
        var visible = scopeService.visibleAreaIds(actor);
        boolean covered = techAreas.stream().anyMatch(wa -> visible.contains(wa.getAreaId()));
        if (!covered) throw BusinessException.scopeDenied();
    }

    // ─── CRUD ────────────────────────────────────────────────────────

    @Transactional
    public Technician create(UUID actor, TechnicianCreateRequest req, String idemKey) {
        authz.requirePermission(actor, "TECHNICIAN_CREATE");
        idempotency.register(idemKey, "POST /technicians", actor);
        if (technicianRepo.existsByCode(req.code())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Technician code already exists", HttpStatus.CONFLICT);
        }
        if (req.vendorId() != null) {
            vendorRepo.findByIdAndDeletedFalse(req.vendorId())
                    .orElseThrow(() -> BusinessException.validation("Vendor not found"));
        }
        Technician t = new Technician();
        t.setCode(req.code());
        t.setName(req.name());
        t.setVendorId(req.vendorId());
        t.setPhone(req.phone());
        t.setEmail(req.email());
        t.setRegionId(req.regionId());
        t.setServiceRadiusKm(req.serviceRadiusKm());
        if (req.maxConcurrentJobs() != null) t.setMaxConcurrentJobs(req.maxConcurrentJobs());
        t.setMaxDailyJobs(req.maxDailyJobs());
        t.setStatus("DRAFT");
        Technician saved = technicianRepo.save(t);
        audit.audit(actor, role(actor), "TECHNICIAN_CREATED", KIND, saved.getId(), null, null, "Technician created: " + req.code());
        return saved;
    }

    @Transactional(readOnly = true)
    public Technician get(UUID actor, UUID id) {
        Technician t = load(id);
        authorizeTechnician(actor, "TECHNICIAN_VIEW", t);
        return t;
    }

    @Transactional
    public Technician update(UUID actor, UUID id, TechnicianUpdateRequest req) {
        Technician t = load(id);
        authorizeTechnician(actor, "TECHNICIAN_UPDATE", t);
        version(t.getVersion(), req.version());
        if (req.name() != null) t.setName(req.name());
        if (req.vendorId() != null) {
            vendorRepo.findByIdAndDeletedFalse(req.vendorId())
                    .orElseThrow(() -> BusinessException.validation("Vendor not found"));
            t.setVendorId(req.vendorId());
        }
        if (req.phone() != null) t.setPhone(req.phone());
        if (req.email() != null) t.setEmail(req.email());
        if (req.regionId() != null) t.setRegionId(req.regionId());
        if (req.serviceRadiusKm() != null) t.setServiceRadiusKm(req.serviceRadiusKm());
        if (req.maxConcurrentJobs() != null) t.setMaxConcurrentJobs(req.maxConcurrentJobs());
        if (req.maxDailyJobs() != null) t.setMaxDailyJobs(req.maxDailyJobs());
        Technician saved = technicianRepo.save(t);
        audit.audit(actor, role(actor), "TECHNICIAN_UPDATED", KIND, saved.getId(), null, null, "Technician updated");
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<TechnicianListItemResponse> list(UUID actor, Integer page, Integer pageSize,
                                                         String status, UUID vendorId) {
        authz.requirePermission(actor, "TECHNICIAN_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        boolean global = scopeService.hasGlobal(actor);
        var visible = scopeService.visibleAreaIds(actor);
        // Filter by scope: technician visible if GLOBAL or covers a visible area.
        var all = technicianRepo.findByDeletedFalse(PageRequest.of(0, Integer.MAX_VALUE)).getContent().stream()
                .filter(t -> {
                    if (global) return true;
                    var areas = areaLinkRepo.findByTechnicianIdAndDeletedFalse(t.getId());
                    return areas.stream().anyMatch(wa -> visible.contains(wa.getAreaId()));
                })
                .filter(t -> status == null || status.equalsIgnoreCase(t.getStatus()))
                .filter(t -> vendorId == null || vendorId.equals(t.getVendorId()))
                .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
                .toList();
        long total = all.size();
        int from = Math.min((p - 1) * ps, all.size());
        int to = Math.min(from + ps, all.size());
        List<TechnicianListItemResponse> data = all.subList(from, to).stream()
                .map(t -> new TechnicianListItemResponse(t.getId(), t.getCode(), t.getName(),
                        t.getVendorId(), t.getStatus(), t.getAvailabilityStatus()))
                .toList();
        return PageResponse.of(data, p, ps, total);
    }

    // ─── Lifecycle ───────────────────────────────────────────────────

    @Transactional
    public Technician changeStatus(UUID actor, UUID id, String permission, String to, String reason, String idemKey) {
        authz.requirePermission(actor, permission);
        idempotency.register(idemKey, "POST /technicians/{id}/" + to, actor);
        Technician t = load(id);
        lifecycle.transition(KIND, t.getId(), t.getStatus(), to, actor, reason);
        t.setStatus(to);
        if ("TERMINATED".equals(to)) t.setDeletedAt(LocalDateTime.now());
        return technicianRepo.save(t);
    }

    @Transactional
    public Technician updateAvailability(UUID actor, UUID id, String availabilityStatus) {
        Technician t = load(id);
        authorizeTechnician(actor, "WORKFORCE_AVAILABILITY_MANAGE", t);
        if (!List.of("AVAILABLE", "BUSY", "OFFLINE", "ON_LEAVE").contains(availabilityStatus)) {
            throw BusinessException.validation("Invalid availability status");
        }
        t.setAvailabilityStatus(availabilityStatus);
        Technician saved = technicianRepo.save(t);
        audit.audit(actor, role(actor), "TECHNICIAN_AVAILABILITY_CHANGED", KIND, saved.getId(), null,
                availabilityStatus, "Availability → " + availabilityStatus);
        return saved;
    }

    // ─── Skills ──────────────────────────────────────────────────────

    @Transactional
    public WorkforceSkill addSkill(UUID actor, UUID techId, UUID skillId, String level) {
        Technician t = load(techId);
        authorizeTechnician(actor, "WORKFORCE_SKILL_MANAGE", t);
        skillService.requireActiveSkill(skillId);
        if (skillRepo.findByTechnicianIdAndSkillIdAndDeletedFalse(techId, skillId).isPresent()) {
            throw new BusinessException("RESOURCE_CONFLICT", "Skill already assigned", HttpStatus.CONFLICT);
        }
        WorkforceSkill ws = new WorkforceSkill();
        ws.setWorkforceKind(KIND);
        ws.setTechnicianId(techId);
        ws.setSkillId(skillId);
        if (level != null) ws.setSkillLevel(level);
        WorkforceSkill saved = skillRepo.save(ws);
        audit.audit(actor, role(actor), "TECHNICIAN_SKILL_ADDED", KIND, techId, null, null, "Skill added: " + skillId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<WorkforceSkill> listSkills(UUID actor, UUID techId) {
        Technician t = load(techId);
        authorizeTechnician(actor, "TECHNICIAN_VIEW", t);
        return skillRepo.findByTechnicianIdAndDeletedFalse(techId);
    }

    @Transactional
    public void removeSkill(UUID actor, UUID techId, UUID skillId) {
        Technician t = load(techId);
        authorizeTechnician(actor, "WORKFORCE_SKILL_MANAGE", t);
        WorkforceSkill ws = skillRepo.findByTechnicianIdAndSkillIdAndDeletedFalse(techId, skillId)
                .orElseThrow(() -> BusinessException.notFound("Skill assignment not found"));
        ws.setDeleted(true);
        skillRepo.save(ws);
        audit.audit(actor, role(actor), "TECHNICIAN_SKILL_REMOVED", KIND, techId, null, null, "Skill removed: " + skillId);
    }

    // ─── Areas ───────────────────────────────────────────────────────

    @Transactional
    public WorkforceArea addArea(UUID actor, UUID techId, UUID areaId) {
        Technician t = load(techId);
        authz.requirePermission(actor, "WORKFORCE_AREA_MANAGE");
        // Inactive/terminated workforce cannot receive operational area assignments (§14).
        if ("TERMINATED".equals(t.getStatus()) || "INACTIVE".equals(t.getStatus())) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot assign area to a " + t.getStatus() + " technician", HttpStatus.CONFLICT);
        }
        areaRepo.findByIdAndDeletedFalse(areaId)
                .orElseThrow(() -> BusinessException.validation("Area not found"));
        // The actor must be authorized for the area being assigned (scope).
        if (!scopeService.hasGlobal(actor) && !scopeService.visibleAreaIds(actor).contains(areaId)) {
            throw BusinessException.scopeDenied();
        }
        if (areaLinkRepo.findByTechnicianIdAndAreaIdAndDeletedFalse(techId, areaId).isPresent()) {
            throw new BusinessException("RESOURCE_CONFLICT", "Area already assigned", HttpStatus.CONFLICT);
        }
        WorkforceArea wa = new WorkforceArea();
        wa.setWorkforceKind(KIND);
        wa.setTechnicianId(techId);
        wa.setAreaId(areaId);
        WorkforceArea saved = areaLinkRepo.save(wa);
        audit.audit(actor, role(actor), "TECHNICIAN_AREA_ADDED", KIND, techId, null, null, "Area added: " + areaId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<WorkforceArea> listAreas(UUID actor, UUID techId) {
        Technician t = load(techId);
        authorizeTechnician(actor, "TECHNICIAN_VIEW", t);
        return areaLinkRepo.findByTechnicianIdAndDeletedFalse(techId);
    }

    @Transactional
    public void removeArea(UUID actor, UUID techId, UUID areaId) {
        Technician t = load(techId);
        authz.requirePermission(actor, "WORKFORCE_AREA_MANAGE");
        WorkforceArea wa = areaLinkRepo.findByTechnicianIdAndAreaIdAndDeletedFalse(techId, areaId)
                .orElseThrow(() -> BusinessException.notFound("Area assignment not found"));
        if (!scopeService.hasGlobal(actor) && !scopeService.visibleAreaIds(actor).contains(areaId)) {
            throw BusinessException.scopeDenied();
        }
        wa.setDeleted(true);
        areaLinkRepo.save(wa);
        audit.audit(actor, role(actor), "TECHNICIAN_AREA_REMOVED", KIND, techId, null, null, "Area removed: " + areaId);
    }

    // ─── Certifications ──────────────────────────────────────────────

    @Transactional
    public Certification addCertification(UUID actor, UUID techId, CertificationRequest req) {
        Technician t = load(techId);
        authorizeTechnician(actor, "WORKFORCE_CERTIFICATION_MANAGE", t);
        Certification c = new Certification();
        c.setWorkforceKind(KIND);
        c.setTechnicianId(techId);
        c.setCertType(req.certType());
        c.setIssuingAuthority(req.issuingAuthority());
        c.setReferenceNo(req.referenceNo());
        c.setIssuedDate(parseDate(req.issuedDate()));
        c.setExpiryDate(parseDate(req.expiryDate()));
        c.setStatus(computeCertStatus(c.getExpiryDate()));
        Certification saved = certRepo.save(c);
        audit.audit(actor, role(actor), "TECHNICIAN_CERT_ADDED", KIND, techId, null, null, "Certification added: " + req.certType());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Certification> listCertifications(UUID actor, UUID techId) {
        Technician t = load(techId);
        authorizeTechnician(actor, "TECHNICIAN_VIEW", t);
        return certRepo.findByTechnicianIdAndDeletedFalse(techId);
    }

    private String computeCertStatus(LocalDate expiry) {
        if (expiry != null && expiry.isBefore(LocalDate.now())) return "EXPIRED";
        return "ACTIVE";
    }
    private LocalDate parseDate(String s) { return (s == null || s.isBlank()) ? null : LocalDate.parse(s); }

    // ─── Availability windows ────────────────────────────────────────

    @Transactional
    public WorkforceAvailability addAvailability(UUID actor, UUID techId, AvailabilityRequest req) {
        Technician t = load(techId);
        authorizeTechnician(actor, "WORKFORCE_AVAILABILITY_MANAGE", t);
        WorkforceAvailability w = new WorkforceAvailability();
        w.setWorkforceKind(KIND);
        w.setTechnicianId(techId);
        w.setDayOfWeek(req.dayOfWeek());
        w.setSpecificDate(parseDate(req.specificDate()));
        w.setStartTime(req.startTime());
        w.setEndTime(req.endTime());
        w.setLeave(req.leave());
        w.setNote(req.note());
        WorkforceAvailability saved = availRepo.save(w);
        audit.audit(actor, role(actor), "TECHNICIAN_AVAILABILITY_WINDOW_ADDED", KIND, techId, null, null, "Availability window added");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<WorkforceAvailability> listAvailability(UUID actor, UUID techId) {
        Technician t = load(techId);
        authorizeTechnician(actor, "TECHNICIAN_VIEW", t);
        return availRepo.findByTechnicianIdAndDeletedFalse(techId);
    }

    // ─── History ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkforceStatusHistory> history(UUID actor, UUID techId) {
        Technician t = load(techId);
        authorizeTechnician(actor, "TECHNICIAN_VIEW", t);
        return lifecycle.history(KIND, techId);
    }

    /** PII visibility gate — used by the controller to decide masking. */
    public boolean canViewPii(UUID actor) {
        return permissionService.hasPermission(actor, "TECHNICIAN_PII_VIEW");
    }
}
