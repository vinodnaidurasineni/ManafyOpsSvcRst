package com.manafy.ops.apartment.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/** Apartment contact. phone/email are PII — protected + masked by authorization. */
@Entity
@Table(name = "apartment_contact")
public class ApartmentContact extends BaseEntity {

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    /** MANAGEMENT | EMERGENCY | BILLING | OTHER. */
    @Column(name = "contact_type", nullable = false, length = 30)
    private String contactType;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "role_title", length = 100)
    private String roleTitle;

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public String getContactType() { return contactType; }
    public void setContactType(String contactType) { this.contactType = contactType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRoleTitle() { return roleTitle; }
    public void setRoleTitle(String roleTitle) { this.roleTitle = roleTitle; }
}
