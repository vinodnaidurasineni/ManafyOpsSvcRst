package com.manafy.ops.compat.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * Helper-tag lead (ManafyOps compatibility).
 *
 * When a resident "tags" an existing household helper (maid/cook/etc.) from the
 * consumer app, ops captures the lead here so an ops user can follow up and
 * onboard that helper into the managed workforce. This is the ops-side backing
 * for the ported admin "Helper Tags" screen ({@code /admin/helper-tags}).
 *
 * Deliberately simple and self-contained (opaque customer/apartment references,
 * never cross-DB FKs) — matching the ops_complaint / manual_assignment_request
 * source-reference convention.
 */
@Entity
@Table(name = "helper_tag")
public class HelperTag extends BaseEntity {

    @Column(name = "helper_name", nullable = false, length = 150)
    private String helperName;

    @Column(name = "helper_mobile", length = 20)
    private String helperMobile;

    @Column(name = "helper_type", length = 40)
    private String helperType;

    @Column(name = "flat_number", length = 60)
    private String flatNumber;

    @Column(name = "apartment_name", length = 200)
    private String apartmentName;

    /** Opaque references (never FKs across services). */
    @Column(name = "community_apartment_id", length = 64)
    private String communityApartmentId;

    @Column(name = "community_customer_id", length = 64)
    private String communityCustomerId;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_mobile", length = 20)
    private String customerMobile;

    /** PENDING | CONTACTED | ONBOARDED | DISMISSED. */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(length = 500)
    private String notes;

    public String getHelperName() { return helperName; }
    public void setHelperName(String helperName) { this.helperName = helperName; }
    public String getHelperMobile() { return helperMobile; }
    public void setHelperMobile(String helperMobile) { this.helperMobile = helperMobile; }
    public String getHelperType() { return helperType; }
    public void setHelperType(String helperType) { this.helperType = helperType; }
    public String getFlatNumber() { return flatNumber; }
    public void setFlatNumber(String flatNumber) { this.flatNumber = flatNumber; }
    public String getApartmentName() { return apartmentName; }
    public void setApartmentName(String apartmentName) { this.apartmentName = apartmentName; }
    public String getCommunityApartmentId() { return communityApartmentId; }
    public void setCommunityApartmentId(String communityApartmentId) { this.communityApartmentId = communityApartmentId; }
    public String getCommunityCustomerId() { return communityCustomerId; }
    public void setCommunityCustomerId(String communityCustomerId) { this.communityCustomerId = communityCustomerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerMobile() { return customerMobile; }
    public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
