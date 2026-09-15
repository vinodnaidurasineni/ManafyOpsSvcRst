package com.manafy.ops.apartment.service;

import com.manafy.ops.apartment.dto.ChildResourceDtos.*;
import com.manafy.ops.apartment.entity.*;
import com.manafy.ops.apartment.repository.*;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Apartment contacts (PII), documents (metadata), and service enablement. All
 * operations authorize via the parent apartment scope. Contact PII (phone/email)
 * is MASKED in responses unless the caller holds APARTMENT_CONTACT_VIEW.
 */
@Service
public class ApartmentInfoService {

    private final ApartmentRepository apartmentRepo;
    private final ApartmentContactRepository contactRepo;
    private final ApartmentDocumentRepository documentRepo;
    private final ApartmentServiceConfigRepository serviceRepo;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final ResourceScopeResolver resolver;
    private final AuditService audit;

    public ApartmentInfoService(ApartmentRepository apartmentRepo, ApartmentContactRepository contactRepo,
                                ApartmentDocumentRepository documentRepo, ApartmentServiceConfigRepository serviceRepo,
                                AuthorizationService authz, PermissionService permissionService,
                                ResourceScopeResolver resolver, AuditService audit) {
        this.apartmentRepo = apartmentRepo;
        this.contactRepo = contactRepo;
        this.documentRepo = documentRepo;
        this.serviceRepo = serviceRepo;
        this.authz = authz;
        this.permissionService = permissionService;
        this.resolver = resolver;
        this.audit = audit;
    }

    private Apartment apartment(UUID id) {
        return apartmentRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("Apartment not found"));
    }
    private ResourceRef ref(Apartment a) { return resolver.apartment(a.getId(), a.getAreaId(), a.getRegionId()); }
    private String role(UUID u) { return permissionService.effectiveRoleCodes(u).stream().sorted().findFirst().orElse(null); }

    // ─── Contacts (PII masking) ──────────────────────────────────────

    @Transactional
    public ApartmentContact createContact(UUID actor, UUID apartmentId, ContactRequest req) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_CONTACT_MANAGE", ref(a));
        ApartmentContact c = new ApartmentContact();
        c.setApartmentId(apartmentId);
        c.setContactType(req.contactType());
        c.setName(req.name());
        c.setPhone(req.phone());
        c.setEmail(req.email());
        c.setRoleTitle(req.roleTitle());
        ApartmentContact saved = contactRepo.save(c);
        // Audit records the action, NOT the raw PII values.
        audit.audit(actor, role(actor), "CONTACT_CREATED", "APARTMENT_CONTACT", saved.getId(), null, null,
                "Contact created (" + req.contactType() + ")");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> listContacts(UUID actor, UUID apartmentId) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_VIEW", ref(a));
        boolean canSeePii = permissionService.hasPermission(actor, "APARTMENT_CONTACT_VIEW");
        return contactRepo.findByApartmentIdAndDeletedFalse(apartmentId).stream()
                .map(c -> toContactResponse(c, canSeePii)).toList();
    }

    @Transactional
    public ApartmentContact updateContact(UUID actor, UUID apartmentId, UUID contactId, ContactRequest req) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_CONTACT_MANAGE", ref(a));
        ApartmentContact c = contactRepo.findByIdAndDeletedFalse(contactId)
                .orElseThrow(() -> BusinessException.notFound("Contact not found"));
        if (!c.getApartmentId().equals(apartmentId)) throw BusinessException.notFound("Contact not found for this apartment");
        if (req.contactType() != null) c.setContactType(req.contactType());
        if (req.name() != null) c.setName(req.name());
        if (req.phone() != null) c.setPhone(req.phone());
        if (req.email() != null) c.setEmail(req.email());
        if (req.roleTitle() != null) c.setRoleTitle(req.roleTitle());
        ApartmentContact saved = contactRepo.save(c);
        audit.audit(actor, role(actor), "CONTACT_UPDATED", "APARTMENT_CONTACT", saved.getId(), null, null, "Contact updated");
        return saved;
    }

    @Transactional
    public void deleteContact(UUID actor, UUID apartmentId, UUID contactId) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_CONTACT_MANAGE", ref(a));
        ApartmentContact c = contactRepo.findByIdAndDeletedFalse(contactId)
                .orElseThrow(() -> BusinessException.notFound("Contact not found"));
        if (!c.getApartmentId().equals(apartmentId)) throw BusinessException.notFound("Contact not found for this apartment");
        c.setDeleted(true);
        contactRepo.save(c);
        audit.audit(actor, role(actor), "CONTACT_DELETED", "APARTMENT_CONTACT", c.getId(), null, null, "Contact deleted");
    }

    public ContactResponse toContactResponse(ApartmentContact c, boolean canSeePii) {
        String phone = canSeePii ? c.getPhone() : mask(c.getPhone());
        String email = canSeePii ? c.getEmail() : maskEmail(c.getEmail());
        return new ContactResponse(c.getId(), c.getApartmentId(), c.getContactType(), c.getName(),
                phone, email, c.getRoleTitle(), !canSeePii, c.getVersion());
    }

    private String mask(String v) {
        if (v == null || v.isBlank()) return v;
        int keep = Math.min(2, v.length());
        return "*".repeat(Math.max(0, v.length() - keep)) + v.substring(v.length() - keep);
    }
    private String maskEmail(String v) {
        if (v == null || !v.contains("@")) return v == null ? null : "***";
        String[] parts = v.split("@", 2);
        String local = parts[0];
        String maskedLocal = local.isEmpty() ? "" : local.charAt(0) + "***";
        return maskedLocal + "@" + parts[1];
    }

    // ─── Documents (metadata) ────────────────────────────────────────

    @Transactional
    public ApartmentDocument createDocument(UUID actor, UUID apartmentId, DocumentCreateRequest req) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_DOCUMENT_MANAGE", ref(a));
        ApartmentDocument d = new ApartmentDocument();
        d.setApartmentId(apartmentId);
        d.setDocType(req.docType());
        d.setFileName(req.fileName());
        d.setObjectKey(req.objectKey());
        d.setContentType(req.contentType());
        d.setSizeBytes(req.sizeBytes());
        d.setStatus("UPLOADED");
        ApartmentDocument saved = documentRepo.save(d);
        audit.audit(actor, role(actor), "DOCUMENT_CREATED", "APARTMENT_DOCUMENT", saved.getId(), null, null,
                "Document added (" + req.docType() + ")");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ApartmentDocument> listDocuments(UUID actor, UUID apartmentId) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_DOCUMENT_VIEW", ref(a));
        return documentRepo.findByApartmentIdAndDeletedFalse(apartmentId);
    }

    /** Fetch one document, authorizing via its PARENT apartment (IDOR-safe). */
    @Transactional(readOnly = true)
    public ApartmentDocument getDocument(UUID actor, UUID documentId) {
        ApartmentDocument d = documentRepo.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> BusinessException.notFound("Document not found"));
        Apartment a = apartment(d.getApartmentId());
        authz.authorize(actor, "APARTMENT_DOCUMENT_VIEW", ref(a));
        return d;
    }

    @Transactional
    public void deleteDocument(UUID actor, UUID documentId) {
        ApartmentDocument d = documentRepo.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> BusinessException.notFound("Document not found"));
        Apartment a = apartment(d.getApartmentId());
        authz.authorize(actor, "APARTMENT_DOCUMENT_MANAGE", ref(a));
        d.setDeleted(true);
        documentRepo.save(d);
        audit.audit(actor, role(actor), "DOCUMENT_DELETED", "APARTMENT_DOCUMENT", d.getId(), null, null, "Document deleted");
    }

    // ─── Service enablement (Phase 2 = enablement only) ──────────────

    @Transactional
    public ApartmentServiceConfig enableService(UUID actor, UUID apartmentId, ApartmentServiceRequest req) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_SERVICE_CONFIGURE", ref(a));
        ApartmentServiceConfig s = serviceRepo
                .findByApartmentIdAndServiceCodeAndDeletedFalse(apartmentId, req.serviceCode())
                .orElseGet(ApartmentServiceConfig::new);
        s.setApartmentId(apartmentId);
        s.setServiceCode(req.serviceCode());
        s.setServiceName(req.serviceName());
        s.setState(req.state() == null ? "ENABLED" : req.state());
        ApartmentServiceConfig saved = serviceRepo.save(s);
        audit.audit(actor, role(actor), "APARTMENT_SERVICE_CONFIGURED", "APARTMENT_SERVICE", saved.getId(), null,
                saved.getState(), "Service " + req.serviceCode() + " → " + saved.getState());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ApartmentServiceConfig> listServices(UUID actor, UUID apartmentId) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_SERVICE_VIEW", ref(a));
        return serviceRepo.findByApartmentIdAndDeletedFalse(apartmentId);
    }

    @Transactional
    public void disableService(UUID actor, UUID apartmentId, UUID serviceId) {
        Apartment a = apartment(apartmentId);
        authz.authorize(actor, "APARTMENT_SERVICE_CONFIGURE", ref(a));
        ApartmentServiceConfig s = serviceRepo.findByIdAndDeletedFalse(serviceId)
                .orElseThrow(() -> BusinessException.notFound("Apartment service not found"));
        if (!s.getApartmentId().equals(apartmentId)) throw BusinessException.notFound("Service not found for this apartment");
        s.setState("DISABLED");
        serviceRepo.save(s);
        audit.audit(actor, role(actor), "APARTMENT_SERVICE_DISABLED", "APARTMENT_SERVICE", s.getId(), null, "DISABLED",
                "Service disabled");
    }
}
