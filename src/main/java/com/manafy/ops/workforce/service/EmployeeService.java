package com.manafy.ops.workforce.service;

import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.identity.repository.OpsUserRepository;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.Employee;
import com.manafy.ops.workforce.repository.EmployeeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Employee / Field Officer master management (Phase 3 §15). A Field Officer is an
 * employee of type FIELD_OFFICER linked to an ops_user. IMPORTANT: area↔FO
 * ownership is NOT managed here — it remains solely in area_field_officer, exposed
 * by the Phase-1 AreaController (POST/DELETE /areas/{id}/field-officers). This
 * service does not create a second area↔FO source of truth.
 */
@Service
public class EmployeeService {

    private static final String KIND = "EMPLOYEE";

    private final EmployeeRepository employeeRepo;
    private final OpsUserRepository userRepo;
    private final WorkforceLifecycleService lifecycle;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final IdempotencyService idempotency;

    public EmployeeService(EmployeeRepository employeeRepo, OpsUserRepository userRepo,
                           WorkforceLifecycleService lifecycle, AuthorizationService authz,
                           PermissionService permissionService, AuditService audit,
                           IdempotencyService idempotency) {
        this.employeeRepo = employeeRepo;
        this.userRepo = userRepo;
        this.lifecycle = lifecycle;
        this.authz = authz;
        this.permissionService = permissionService;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    private Employee load(UUID id) {
        return employeeRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Employee not found"));
    }
    private String role(UUID u) { return permissionService.effectiveRoleCodes(u).stream().sorted().findFirst().orElse(null); }

    @Transactional
    public Employee create(UUID actor, EmployeeCreateRequest req, String idemKey) {
        authz.requirePermission(actor, "EMPLOYEE_CREATE");
        idempotency.register(idemKey, "POST /employees", actor);
        if (employeeRepo.existsByEmployeeCode(req.employeeCode())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Employee code already exists", HttpStatus.CONFLICT);
        }
        String type = req.employeeType() == null ? "STAFF" : req.employeeType();
        if (!List.of("STAFF", "FIELD_OFFICER", "MANAGER").contains(type)) {
            throw BusinessException.validation("Invalid employeeType: " + type);
        }
        if (req.userId() != null) {
            userRepo.findByIdAndDeletedFalse(req.userId())
                    .orElseThrow(() -> BusinessException.validation("Linked user not found"));
        }
        Employee e = new Employee();
        e.setEmployeeCode(req.employeeCode());
        e.setName(req.name());
        e.setEmail(req.email());
        e.setPhone(req.phone());
        e.setDesignation(req.designation());
        e.setEmployeeType(type);
        e.setUserId(req.userId());
        e.setStatus("DRAFT");
        Employee saved = employeeRepo.save(e);
        audit.audit(actor, role(actor), "EMPLOYEE_CREATED", KIND, saved.getId(), null, null,
                "Employee created: " + req.employeeCode() + " (" + type + ")");
        return saved;
    }

    @Transactional(readOnly = true)
    public Employee get(UUID actor, UUID id) {
        authz.requirePermission(actor, "EMPLOYEE_VIEW");
        return load(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(UUID actor, Integer page, Integer pageSize) {
        authz.requirePermission(actor, "EMPLOYEE_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        var result = employeeRepo.findByDeletedFalse(PageRequest.of(p - 1, ps));
        List<EmployeeResponse> data = result.getContent().stream().map(this::map).toList();
        return PageResponse.of(data, p, ps, result.getTotalElements());
    }

    @Transactional
    public Employee update(UUID actor, UUID id, EmployeeUpdateRequest req) {
        authz.requirePermission(actor, "EMPLOYEE_UPDATE");
        Employee e = load(id);
        if (req.version() != null && req.version() != e.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT", "Employee modified concurrently", HttpStatus.CONFLICT);
        }
        if (req.name() != null) e.setName(req.name());
        if (req.email() != null) e.setEmail(req.email());
        if (req.phone() != null) e.setPhone(req.phone());
        if (req.designation() != null) e.setDesignation(req.designation());
        Employee saved = employeeRepo.save(e);
        audit.audit(actor, role(actor), "EMPLOYEE_UPDATED", KIND, saved.getId(), null, null, "Employee updated");
        return saved;
    }

    @Transactional
    public Employee changeStatus(UUID actor, UUID id, String to, String reason) {
        authz.requirePermission(actor, "EMPLOYEE_UPDATE");
        Employee e = load(id);
        lifecycle.transition(KIND, e.getId(), e.getStatus(), to, actor, reason);
        e.setStatus(to);
        if ("TERMINATED".equals(to)) e.setDeletedAt(LocalDateTime.now());
        return employeeRepo.save(e);
    }

    @Transactional(readOnly = true)
    public List<Employee> listFieldOfficers(UUID actor) {
        authz.requirePermission(actor, "EMPLOYEE_VIEW");
        return employeeRepo.findByEmployeeTypeAndDeletedFalse("FIELD_OFFICER");
    }

    public EmployeeResponse map(Employee e) {
        return new EmployeeResponse(e.getId(), e.getEmployeeCode(), e.getName(), e.getEmail(), e.getPhone(),
                e.getDesignation(), e.getEmployeeType(), e.getUserId(), e.getStatus(), e.getVersion());
    }
}
