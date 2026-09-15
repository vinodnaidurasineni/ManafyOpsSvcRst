package com.manafy.ops.workforce.service;

import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.workforce.domain.WorkforceStateMachine;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.Vendor;
import com.manafy.ops.workforce.entity.VendorStaff;
import com.manafy.ops.workforce.repository.HelperRepository;
import com.manafy.ops.workforce.repository.TechnicianRepository;
import com.manafy.ops.workforce.repository.VendorRepository;
import com.manafy.ops.workforce.repository.VendorStaffRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Vendor + vendor staff management (Phase 3 §9, §10). Permission-gated (VENDOR_*). */
@Service
public class VendorService {

    private final VendorRepository vendorRepo;
    private final VendorStaffRepository staffRepo;
    private final TechnicianRepository technicianRepo;
    private final HelperRepository helperRepo;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final WorkforceLifecycleService lifecycle;
    private final AuditService audit;
    private final com.manafy.ops.common.idempotency.IdempotencyService idempotency;

    private static final String KIND = "VENDOR";

    public VendorService(VendorRepository vendorRepo, VendorStaffRepository staffRepo,
                         TechnicianRepository technicianRepo, HelperRepository helperRepo,
                         AuthorizationService authz, PermissionService permissionService,
                         WorkforceLifecycleService lifecycle, AuditService audit,
                         com.manafy.ops.common.idempotency.IdempotencyService idempotency) {
        this.vendorRepo = vendorRepo;
        this.staffRepo = staffRepo;
        this.technicianRepo = technicianRepo;
        this.helperRepo = helperRepo;
        this.authz = authz;
        this.permissionService = permissionService;
        this.lifecycle = lifecycle;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    private Vendor load(UUID id) {
        return vendorRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));
    }
    private String role(UUID u) { return permissionService.effectiveRoleCodes(u).stream().sorted().findFirst().orElse(null); }
    private void version(long actual, Long expected) {
        if (expected != null && expected != actual)
            throw new BusinessException("CONCURRENCY_CONFLICT", "Vendor modified concurrently", HttpStatus.CONFLICT);
    }

    @Transactional
    public Vendor create(UUID actor, VendorCreateRequest req, String idemKey) {
        authz.requirePermission(actor, "VENDOR_CREATE");
        idempotency.register(idemKey, "POST /vendors", actor);
        if (vendorRepo.existsByCode(req.code())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Vendor code already exists", HttpStatus.CONFLICT);
        }
        Vendor v = new Vendor();
        v.setCode(req.code());
        v.setLegalName(req.legalName());
        v.setDisplayName(req.displayName());
        v.setRegistrationNo(req.registrationNo());
        v.setEmail(req.email());
        v.setPhone(req.phone());
        v.setAddress(req.address());
        v.setStatus("DRAFT");
        Vendor saved = vendorRepo.save(v);
        audit.audit(actor, role(actor), "VENDOR_CREATED", KIND, saved.getId(), null, null, "Vendor created: " + req.code());
        return saved;
    }

    @Transactional(readOnly = true)
    public Vendor get(UUID actor, UUID id) {
        authz.requirePermission(actor, "VENDOR_VIEW");
        return load(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<VendorListItemResponse> list(UUID actor, Integer page, Integer pageSize) {
        authz.requirePermission(actor, "VENDOR_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        var result = vendorRepo.findByDeletedFalse(PageRequest.of(p - 1, ps));
        List<VendorListItemResponse> data = result.getContent().stream()
                .map(v -> new VendorListItemResponse(v.getId(), v.getCode(), v.getDisplayName(), v.getStatus()))
                .toList();
        return PageResponse.of(data, p, ps, result.getTotalElements());
    }

    @Transactional
    public Vendor update(UUID actor, UUID id, VendorUpdateRequest req) {
        authz.requirePermission(actor, "VENDOR_UPDATE");
        Vendor v = load(id);
        version(v.getVersion(), req.version());
        if (req.legalName() != null) v.setLegalName(req.legalName());
        if (req.displayName() != null) v.setDisplayName(req.displayName());
        if (req.registrationNo() != null) v.setRegistrationNo(req.registrationNo());
        if (req.email() != null) v.setEmail(req.email());
        if (req.phone() != null) v.setPhone(req.phone());
        if (req.address() != null) v.setAddress(req.address());
        Vendor saved = vendorRepo.save(v);
        audit.audit(actor, role(actor), "VENDOR_UPDATED", KIND, saved.getId(), null, null, "Vendor updated");
        return saved;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────

    @Transactional
    public Vendor changeStatus(UUID actor, UUID id, String permission, String to, String reason, String idemKey) {
        authz.requirePermission(actor, permission);
        idempotency.register(idemKey, "POST /vendors/{id}/" + to, actor);
        Vendor v = load(id);
        lifecycle.transition(KIND, v.getId(), v.getStatus(), to, actor, reason);
        v.setStatus(to);
        if ("TERMINATED".equals(to)) v.setDeletedAt(LocalDateTime.now());
        return vendorRepo.save(v);
    }

    // ─── Vendor staff ────────────────────────────────────────────────

    @Transactional
    public VendorStaff addStaff(UUID actor, UUID vendorId, VendorStaffRequest req) {
        authz.requirePermission(actor, "VENDOR_STAFF_MANAGE");
        Vendor v = load(vendorId);   // parent must exist (IDOR-safe: staff resolves through vendor)
        if (req.technicianId() != null) {
            technicianRepo.findByIdAndDeletedFalse(req.technicianId())
                    .orElseThrow(() -> BusinessException.validation("Technician not found"));
        }
        if (req.helperId() != null) {
            helperRepo.findByIdAndDeletedFalse(req.helperId())
                    .orElseThrow(() -> BusinessException.validation("Helper not found"));
        }
        VendorStaff s = new VendorStaff();
        s.setVendorId(v.getId());
        s.setTechnicianId(req.technicianId());
        s.setHelperId(req.helperId());
        s.setStaffName(req.staffName());
        s.setRoleTitle(req.roleTitle());
        s.setJoinedAt(LocalDateTime.now());
        s.setStatus("ACTIVE");
        VendorStaff saved = staffRepo.save(s);
        audit.audit(actor, role(actor), "VENDOR_STAFF_ADDED", "VENDOR_STAFF", saved.getId(), null, null,
                "Vendor staff added to vendor " + vendorId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<VendorStaff> listStaff(UUID actor, UUID vendorId) {
        authz.requirePermission(actor, "VENDOR_VIEW");
        load(vendorId);
        return staffRepo.findByVendorIdAndDeletedFalse(vendorId);
    }

    @Transactional(readOnly = true)
    public VendorStaff getStaff(UUID actor, UUID staffId) {
        authz.requirePermission(actor, "VENDOR_VIEW");
        VendorStaff s = staffRepo.findByIdAndDeletedFalse(staffId)
                .orElseThrow(() -> BusinessException.notFound("Vendor staff not found"));
        load(s.getVendorId()); // ensure parent vendor exists / not deleted
        return s;
    }

    @Transactional
    public VendorStaff updateStaff(UUID actor, UUID staffId, VendorStaffRequest req) {
        authz.requirePermission(actor, "VENDOR_STAFF_MANAGE");
        VendorStaff s = staffRepo.findByIdAndDeletedFalse(staffId)
                .orElseThrow(() -> BusinessException.notFound("Vendor staff not found"));
        load(s.getVendorId());
        if (req.staffName() != null) s.setStaffName(req.staffName());
        if (req.roleTitle() != null) s.setRoleTitle(req.roleTitle());
        VendorStaff saved = staffRepo.save(s);
        audit.audit(actor, role(actor), "VENDOR_STAFF_UPDATED", "VENDOR_STAFF", saved.getId(), null, null, "Vendor staff updated");
        return saved;
    }
}
