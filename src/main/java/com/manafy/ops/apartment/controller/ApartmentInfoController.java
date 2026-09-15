package com.manafy.ops.apartment.controller;

import com.manafy.ops.apartment.dto.ApartmentMapper;
import com.manafy.ops.apartment.dto.ChildResourceDtos.*;
import com.manafy.ops.apartment.service.ApartmentInfoService;
import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Apartment contacts (PII masked), documents (metadata), and service enablement
 * (Phase 2 §15, §16, §17). Thin controller delegating to ApartmentInfoService.
 */
@RestController
@RequestMapping("/api/v1/apartments/{apartmentId}")
public class ApartmentInfoController {

    private final ApartmentInfoService service;
    private final AuthenticationContext auth;

    public ApartmentInfoController(ApartmentInfoService service, AuthenticationContext auth) {
        this.service = service;
        this.auth = auth;
    }

    // ─── Contacts ────────────────────────────────────────────────────

    @GetMapping("/contacts")
    public ApiResponse<List<ContactResponse>> listContacts(@PathVariable UUID apartmentId) {
        return ApiResponse.ok(service.listContacts(auth.currentUserId(), apartmentId));
    }

    @PostMapping("/contacts")
    public ApiResponse<ContactResponse> createContact(@PathVariable UUID apartmentId, @Valid @RequestBody ContactRequest req) {
        // Returned as unmasked to a manager who just created it? Return masked-by-permission for consistency.
        var c = service.createContact(auth.currentUserId(), apartmentId, req);
        boolean canSeePii = true; // creator holds APARTMENT_CONTACT_MANAGE; show what they submitted
        return ApiResponse.ok(service.toContactResponse(c, canSeePii));
    }

    @PutMapping("/contacts/{contactId}")
    public ApiResponse<ContactResponse> updateContact(@PathVariable UUID apartmentId, @PathVariable UUID contactId,
                                                      @Valid @RequestBody ContactRequest req) {
        var c = service.updateContact(auth.currentUserId(), apartmentId, contactId, req);
        return ApiResponse.ok(service.toContactResponse(c, true));
    }

    @DeleteMapping("/contacts/{contactId}")
    public ApiResponse<Void> deleteContact(@PathVariable UUID apartmentId, @PathVariable UUID contactId) {
        service.deleteContact(auth.currentUserId(), apartmentId, contactId);
        return ApiResponse.ok(null);
    }

    // ─── Documents ───────────────────────────────────────────────────

    @GetMapping("/documents")
    public ApiResponse<List<DocumentResponse>> listDocuments(@PathVariable UUID apartmentId) {
        return ApiResponse.ok(service.listDocuments(auth.currentUserId(), apartmentId).stream()
                .map(ApartmentMapper::toDocument).toList());
    }

    @PostMapping("/documents")
    public ApiResponse<DocumentResponse> createDocument(@PathVariable UUID apartmentId,
                                                        @Valid @RequestBody DocumentCreateRequest req) {
        return ApiResponse.ok(ApartmentMapper.toDocument(service.createDocument(auth.currentUserId(), apartmentId, req)));
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID apartmentId, @PathVariable UUID documentId) {
        service.deleteDocument(auth.currentUserId(), documentId);
        return ApiResponse.ok(null);
    }

    // ─── Service enablement ──────────────────────────────────────────

    @GetMapping("/services")
    public ApiResponse<List<ApartmentServiceResponse>> listServices(@PathVariable UUID apartmentId) {
        return ApiResponse.ok(service.listServices(auth.currentUserId(), apartmentId).stream()
                .map(ApartmentMapper::toService).toList());
    }

    @PostMapping("/services")
    public ApiResponse<ApartmentServiceResponse> enableService(@PathVariable UUID apartmentId,
                                                               @Valid @RequestBody ApartmentServiceRequest req) {
        return ApiResponse.ok(ApartmentMapper.toService(service.enableService(auth.currentUserId(), apartmentId, req)));
    }

    @DeleteMapping("/services/{serviceId}")
    public ApiResponse<Void> disableService(@PathVariable UUID apartmentId, @PathVariable UUID serviceId) {
        service.disableService(auth.currentUserId(), apartmentId, serviceId);
        return ApiResponse.ok(null);
    }
}
