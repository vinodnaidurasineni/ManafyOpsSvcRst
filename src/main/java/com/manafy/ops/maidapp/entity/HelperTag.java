package com.manafy.ops.maidapp.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * A resident-submitted "tag your helper" lead (ported from ManafySvcRst). A
 * resident reports an existing helper they use; ops follows up to onboard them.
 * Display fields denormalized for the admin list.
 */
@Entity
@Table(name = "maidapp_helper_tag", indexes = {
        @Index(name = "idx_mhelpertag_status", columnList = "status")
})
public class HelperTag extends BaseEntity {

    @Column(name = "helper_name", length = 150)
    private String helperName;

    @Column(name = "helper_mobile", length = 30)
    private String helperMobile;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_mobile", length = 30)
    private String customerMobile;

    @Column(name = "apartment_name", length = 200)
    private String apartmentName;

    @Column(name = "flat_number", length = 50)
    private String flatNumber;

    @Column(nullable = false, length = 20)
    private String status = "PENDING"; // PENDING, CONTACTED, ONBOARDED, REJECTED

    public String getHelperName() { return helperName; }
    public void setHelperName(String helperName) { this.helperName = helperName; }
    public String getHelperMobile() { return helperMobile; }
    public void setHelperMobile(String helperMobile) { this.helperMobile = helperMobile; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerMobile() { return customerMobile; }
    public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }
    public String getApartmentName() { return apartmentName; }
    public void setApartmentName(String apartmentName) { this.apartmentName = apartmentName; }
    public String getFlatNumber() { return flatNumber; }
    public void setFlatNumber(String flatNumber) { this.flatNumber = flatNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
