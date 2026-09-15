package com.manafy.ops.workforce.service;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.WorkforceDocument;
import com.manafy.ops.workforce.repository.HelperRepository;
import com.manafy.ops.workforce.repository.TechnicianRepository;
import com.manafy.ops.workforce.repository.WorkforceDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Workforce documents (Phase 3 §18, §19). Bytes in object storage; metadata only.
 *
 * KYC vs FINANCE separation: doc_category determines the required VIEW permission —
 *   GENERAL → WORKFORCE_DOC_VIEW
 *   KYC     → TECHNICIAN_KYC_VIEW
 *   FINANCE → TECHNICIAN_FINANCE_VIEW
 * Management requires WORKFORCE_DOC_MANAGE. Documents resolve through their parent
 * workforce record (IDOR-safe). Raw sensitive data is never logged.
 */
@Service
public class WorkforceDocumentService {

    private final WorkforceDocumentRepository docRepo;
    private final TechnicianRepository technicianRepo;
    private final HelperRepository helperRepo;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final AuditService audit;

    public WorkforceDocumentService(WorkforceDocumentRepository docRepo, TechnicianRepository technicianRepo,
                                    HelperRepository helperRepo, AuthorizationService authz,
                                    PermissionService permissionService, AuditService audit) {
        this.docRepo = docRepo;
        this.technicianRepo = technicianRepo;
        this.helperRepo = helperRepo;
        this.authz = authz;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    private String role(UUID u) { return permissionService.effectiveRoleCodes(u).stream().sorted().findFirst().orElse(null); }

    private void requireTechnicianExists(UUID technicianId) {
        technicianRepo.findByIdAndDeletedFalse(technicianId)
                .orElseThrow(() -> BusinessException.notFound("Technician not found"));
    }

    /** VIEW permission required for a given document category (KYC ≠ Finance). */
    private String viewPermissionForCategory(String category) {
        return switch (category == null ? "GENERAL" : category) {
            case "KYC" -> "TECHNICIAN_KYC_VIEW";
            case "FINANCE" -> "TECHNICIAN_FINANCE_VIEW";
            default -> "WORKFORCE_DOC_VIEW";
        };
    }

    @Transactional
    public WorkforceDocument addTechnicianDocument(UUID actor, UUID technicianId, WorkforceDocumentCreateRequest req) {
        authz.requirePermission(actor, "WORKFORCE_DOC_MANAGE");
        requireTechnicianExists(technicianId);
        String category = req.docCategory() == null ? "GENERAL" : req.docCategory();
        if (!List.of("GENERAL", "KYC", "FINANCE").contains(category)) {
            throw BusinessException.validation("Invalid docCategory: " + category);
        }
        WorkforceDocument d = new WorkforceDocument();
        d.setWorkforceKind("TECHNICIAN");
        d.setTechnicianId(technicianId);
        d.setDocType(req.docType());
        d.setDocCategory(category);
        d.setFileName(req.fileName());
        d.setObjectKey(req.objectKey());
        d.setContentType(req.contentType());
        d.setSizeBytes(req.sizeBytes());
        d.setReferenceNo(req.referenceNo());
        d.setIssuedDate(parse(req.issuedDate()));
        d.setExpiryDate(parse(req.expiryDate()));
        d.setStatus("UPLOADED");
        WorkforceDocument saved = docRepo.save(d);
        // Audit records the action + category, NEVER the raw document content/reference.
        audit.audit(actor, role(actor), "WORKFORCE_DOC_ADDED", "WORKFORCE_DOCUMENT", saved.getId(), null, null,
                "Document added (" + category + "/" + req.docType() + ") to technician " + technicianId);
        return saved;
    }

    /** List technician documents the caller is authorized to see (category-filtered). */
    @Transactional(readOnly = true)
    public List<WorkforceDocument> listTechnicianDocuments(UUID actor, UUID technicianId) {
        requireTechnicianExists(technicianId);
        // Baseline: caller must at least hold WORKFORCE_DOC_VIEW or a sensitive view perm.
        boolean canGeneral = permissionService.hasPermission(actor, "WORKFORCE_DOC_VIEW");
        boolean canKyc = permissionService.hasPermission(actor, "TECHNICIAN_KYC_VIEW");
        boolean canFinance = permissionService.hasPermission(actor, "TECHNICIAN_FINANCE_VIEW");
        if (!canGeneral && !canKyc && !canFinance) {
            throw BusinessException.forbidden("Missing workforce document view permission");
        }
        return docRepo.findByTechnicianIdAndDeletedFalse(technicianId).stream()
                .filter(d -> switch (d.getDocCategory()) {
                    case "KYC" -> canKyc;
                    case "FINANCE" -> canFinance;
                    default -> canGeneral;
                })
                .toList();
    }

    /** Fetch one document by id, authorizing by its category via the parent workforce (IDOR-safe). */
    @Transactional(readOnly = true)
    public WorkforceDocument getDocument(UUID actor, UUID documentId) {
        WorkforceDocument d = docRepo.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> BusinessException.notFound("Document not found"));
        // Parent existence (IDOR: cannot fetch a doc whose parent is gone/other).
        if (d.getTechnicianId() != null) requireTechnicianExists(d.getTechnicianId());
        authz.requirePermission(actor, viewPermissionForCategory(d.getDocCategory()));
        return d;
    }

    @Transactional
    public void deleteDocument(UUID actor, UUID documentId) {
        authz.requirePermission(actor, "WORKFORCE_DOC_MANAGE");
        WorkforceDocument d = docRepo.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> BusinessException.notFound("Document not found"));
        d.setDeleted(true);
        docRepo.save(d);
        audit.audit(actor, role(actor), "WORKFORCE_DOC_DELETED", "WORKFORCE_DOCUMENT", d.getId(), null, null, "Document deleted");
    }

    private LocalDate parse(String s) { return (s == null || s.isBlank()) ? null : LocalDate.parse(s); }
}
