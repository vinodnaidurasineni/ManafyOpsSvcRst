package com.manafy.ops.apartment.controller;

import com.manafy.ops.apartment.dto.ApartmentMapper;
import com.manafy.ops.apartment.dto.ChildResourceDtos.DocumentResponse;
import com.manafy.ops.apartment.service.ApartmentInfoService;
import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Direct document access by id (Phase 2 §16). Authorization resolves through the
 * document's PARENT apartment scope — changing the document id to another
 * apartment's document is denied (IDOR test §28.12).
 */
@RestController
@RequestMapping("/api/v1/documents")
public class ApartmentDocumentController {

    private final ApartmentInfoService service;
    private final AuthenticationContext auth;

    public ApartmentDocumentController(ApartmentInfoService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentResponse> get(@PathVariable UUID documentId) {
        return ApiResponse.ok(ApartmentMapper.toDocument(service.getDocument(auth.currentUserId(), documentId)));
    }
}
