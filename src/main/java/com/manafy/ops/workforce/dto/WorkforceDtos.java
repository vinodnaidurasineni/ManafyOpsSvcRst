package com.manafy.ops.workforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Phase 3 workforce DTOs (§21, §25). Entities are never returned directly. */
public final class WorkforceDtos {

    private WorkforceDtos() {}

    // ─── Skill ───────────────────────────────────────────────────────
    public record SkillCreateRequest(@NotBlank String code, @NotBlank String name, String category) {}
    public record SkillUpdateRequest(String name, String category, String status, Long version) {}
    public record SkillResponse(UUID id, String code, String name, String category, String status, long version) {}

    // ─── Vendor ──────────────────────────────────────────────────────
    public record VendorCreateRequest(@NotBlank String code, @NotBlank String legalName,
                                      @NotBlank String displayName, String registrationNo,
                                      String email, String phone, String address) {}
    public record VendorUpdateRequest(String legalName, String displayName, String registrationNo,
                                      String email, String phone, String address, Long version) {}
    public record VendorListItemResponse(UUID id, String code, String displayName, String status) {}
    public record VendorDetailResponse(UUID id, String code, String legalName, String displayName,
                                       String registrationNo, String email, String phone, String address,
                                       String status, long version) {}

    // ─── Vendor staff ────────────────────────────────────────────────
    public record VendorStaffRequest(String staffName, String roleTitle, UUID technicianId, UUID helperId) {}
    public record VendorStaffResponse(UUID id, UUID vendorId, UUID technicianId, UUID helperId,
                                      String staffName, String roleTitle, String status, long version) {}

    // ─── Technician ──────────────────────────────────────────────────
    public record TechnicianCreateRequest(@NotBlank String code, @NotBlank String name,
                                          UUID vendorId, String phone, String email,
                                          UUID regionId, BigDecimal serviceRadiusKm,
                                          Integer maxConcurrentJobs, Integer maxDailyJobs) {}
    public record TechnicianUpdateRequest(String name, UUID vendorId, String phone, String email,
                                          UUID regionId, BigDecimal serviceRadiusKm,
                                          Integer maxConcurrentJobs, Integer maxDailyJobs, Long version) {}
    public record TechnicianListItemResponse(UUID id, String code, String name, UUID vendorId,
                                             String status, String availabilityStatus) {}
    /** Detail: PII (phone/email/DOB) masked unless caller holds TECHNICIAN_PII_VIEW. */
    public record TechnicianDetailResponse(UUID id, String code, String name, UUID vendorId,
                                           String phone, String email, String dateOfBirth,
                                           UUID regionId, BigDecimal serviceRadiusKm,
                                           int maxConcurrentJobs, Integer maxDailyJobs,
                                           String status, String availabilityStatus,
                                           boolean piiMasked, long version) {}
    public record AvailabilityUpdateRequest(@NotBlank String availabilityStatus) {}

    // ─── Helper ──────────────────────────────────────────────────────
    public record HelperCreateRequest(@NotBlank String code, @NotBlank String name, String phone,
                                      @NotNull String relationship, UUID technicianId, UUID vendorId) {}
    public record HelperUpdateRequest(String name, String phone, String relationship,
                                      UUID technicianId, UUID vendorId, Long version) {}
    public record HelperListItemResponse(UUID id, String code, String name, String relationship, String status) {}
    public record HelperDetailResponse(UUID id, String code, String name, String phone, String relationship,
                                       UUID technicianId, UUID vendorId, String status, long version) {}

    // ─── Employee / Field Officer ────────────────────────────────────
    public record EmployeeCreateRequest(@NotBlank String employeeCode, @NotBlank String name,
                                        String email, String phone, String designation,
                                        String employeeType, UUID userId) {}
    public record EmployeeUpdateRequest(String name, String email, String phone, String designation, Long version) {}
    public record EmployeeResponse(UUID id, String employeeCode, String name, String email, String phone,
                                   String designation, String employeeType, UUID userId, String status, long version) {}

    // ─── Lifecycle / generic ─────────────────────────────────────────
    public record ReasonRequest(String reason) {}

    // ─── Skills / areas / certs / availability / documents ───────────
    public record AssignSkillRequest(@NotNull UUID skillId, String skillLevel) {}
    public record WorkforceSkillResponse(UUID id, UUID skillId, String skillLevel) {}

    public record AssignAreaRequest(@NotNull UUID areaId) {}
    public record WorkforceAreaResponse(UUID id, UUID areaId) {}

    public record CertificationRequest(@NotBlank String certType, String issuingAuthority,
                                       String referenceNo, String issuedDate, String expiryDate) {}
    public record CertificationResponse(UUID id, String certType, String issuingAuthority, String referenceNo,
                                        String issuedDate, String expiryDate, String status, boolean expired,
                                        boolean expiringSoon, long version) {}

    public record AvailabilityRequest(Short dayOfWeek, String specificDate, String startTime,
                                      String endTime, boolean leave, String note) {}
    public record AvailabilityResponse(UUID id, Short dayOfWeek, String specificDate, String startTime,
                                       String endTime, boolean leave, String note, long version) {}

    public record WorkforceDocumentCreateRequest(@NotBlank String docType, String docCategory,
                                                 String fileName, String objectKey, String contentType,
                                                 Long sizeBytes, String referenceNo,
                                                 String issuedDate, String expiryDate) {}
    public record WorkforceDocumentResponse(UUID id, String docType, String docCategory, String fileName,
                                            String objectKey, String contentType, Long sizeBytes,
                                            String status, long version) {}

    public record StatusHistoryResponse(String fromStatus, String toStatus, UUID changedBy,
                                        String reason, String changedAt) {}
}
