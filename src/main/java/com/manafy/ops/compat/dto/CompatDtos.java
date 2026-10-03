package com.manafy.ops.compat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response/request shapes for the ManafyOps mobile compatibility layer.
 *
 * These mirror the field names the ported ManafyOps admin/maid screens read
 * (e.g. maidId/firstName/mobileNumber, activeMaids/todayBookings), adapting the
 * new backend's entities (Helper, Employee, OpsComplaint) to the old contract
 * WITHOUT changing those entities or the canonical controllers.
 */
public final class CompatDtos {
    private CompatDtos() {}

    /** Admin dashboard tiles consumed by AdminHomeScreen. */
    public record AdminDashboard(
            long activeMaids,
            long todayBookings,
            Long totalCustomers,
            long totalBookings,
            long openComplaints) {}

    /** A maid row for ManageMaids / ReplaceMaid / CustomerSearch (Helper category=MAID). */
    public record MaidItem(
            UUID id,
            UUID maidId,
            String employeeCode,
            String firstName,
            String lastName,
            String mobileNumber,
            String category,
            String status,
            String availabilityStatus,
            long version) {}

    public record MaidCreateRequest(
            String firstName,
            String lastName,
            String mobileNumber,
            String category) {}

    /** Employee row for ManageMaids ({role:'HELPER'}) + TagApartments. */
    public record EmployeeItem(
            UUID id,
            String employeeCode,
            String firstName,
            String lastName,
            String mobileNumber,
            String email,
            String designation,
            String employeeType,
            String status,
            java.util.List<String> apartmentIds,
            long version) {}

    /**
     * Complaint row for the admin Complaints screen. Field names match the ported
     * ManafyOps screen exactly: complaintStatus, maidName/maidMobile (the assignee),
     * bookingNumber. Duplicate aliases (status/assignee*) are included for safety.
     */
    public record ComplaintItem(
            UUID id,
            String referenceNo,
            String complaintType,
            String complaintStatus,
            String status,
            String priority,
            String description,
            String customerName,
            String customerMobile,
            String maidName,
            String maidMobile,
            String assigneeName,
            String assigneeMobile,
            String bookingNumber,
            String bookingReference,
            String adminNotes,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            LocalDateTime closedAt,
            long version) {}

    /** Helper-tag lead row for the admin Helper Tags screen. */
    public record HelperTagItem(
            UUID id,
            String helperName,
            String helperMobile,
            String helperType,
            String flatNumber,
            String apartmentName,
            String customerName,
            String customerMobile,
            String status,
            String notes,
            LocalDateTime createdAt) {}
}
