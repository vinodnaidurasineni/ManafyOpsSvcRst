package com.manafy.ops.workforce.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.workforce.dto.WorkforceDtos.*;
import com.manafy.ops.workforce.entity.WorkforceDocument;
import com.manafy.ops.workforce.service.WorkforceDocumentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Workforce document endpoints (Phase 3 §18). Technician documents nested under
 * technician; direct-by-id fetch authorizes by category via the parent (IDOR-safe).
 */
@RestController
@RequestMapping("/api/v1")
public class WorkforceDocumentController {

    private final WorkforceDocumentService service;
    private final AuthenticationContext auth;

    public WorkforceDocumentController(WorkforceDocumentService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    private WorkforceDocumentResponse map(WorkforceDocument d) {
        return new WorkforceDocumentResponse(d.getId(), d.getDocType(), d.getDocCategory(), d.getFileName(),
                d.getObjectKey(), d.getContentType(), d.getSizeBytes(), d.getStatus(), d.getVersion());
    }

    @GetMapping("/technicians/{technicianId}/documents")
    public ApiResponse<List<WorkforceDocumentResponse>> listTechDocs(@PathVariable UUID technicianId) {
        return ApiResponse.ok(service.listTechnicianDocuments(auth.currentUserId(), technicianId).stream()
                .map(this::map).toList());
    }

    @PostMapping("/technicians/{technicianId}/documents")
    public ApiResponse<WorkforceDocumentResponse> addTechDoc(@PathVariable UUID technicianId,
                                                             @Valid @RequestBody WorkforceDocumentCreateRequest req) {
        return ApiResponse.ok(map(service.addTechnicianDocument(auth.currentUserId(), technicianId, req)));
    }

    @GetMapping("/workforce-documents/{documentId}")
    public ApiResponse<WorkforceDocumentResponse> getDoc(@PathVariable UUID documentId) {
        return ApiResponse.ok(map(service.getDocument(auth.currentUserId(), documentId)));
    }

    @DeleteMapping("/workforce-documents/{documentId}")
    public ApiResponse<Void> deleteDoc(@PathVariable UUID documentId) {
        service.deleteDocument(auth.currentUserId(), documentId);
        return ApiResponse.ok(null);
    }
}
