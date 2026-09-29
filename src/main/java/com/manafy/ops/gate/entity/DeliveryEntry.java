package com.manafy.ops.gate.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/** A gate delivery entry (ported from ManafySvcRst gate domain). */
@Entity
@Table(name = "gate_delivery", indexes = {
        @Index(name = "idx_gdelivery_apartment_status", columnList = "apartment_id,status")
})
public class DeliveryEntry extends BaseEntity {

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(name = "delivery_company", nullable = false, length = 100)
    private String deliveryCompany;

    @Column(name = "delivery_person_name", length = 150)
    private String deliveryPersonName;

    @Column(name = "delivery_person_mobile", length = 30)
    private String deliveryPersonMobile;

    @Column(name = "package_description", length = 255)
    private String packageDescription;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(nullable = false, length = 20)
    private String status = "WAITING"; // WAITING, DELIVERED, RETURNED

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public UUID getFlatId() { return flatId; }
    public void setFlatId(UUID flatId) { this.flatId = flatId; }
    public String getDeliveryCompany() { return deliveryCompany; }
    public void setDeliveryCompany(String deliveryCompany) { this.deliveryCompany = deliveryCompany; }
    public String getDeliveryPersonName() { return deliveryPersonName; }
    public void setDeliveryPersonName(String deliveryPersonName) { this.deliveryPersonName = deliveryPersonName; }
    public String getDeliveryPersonMobile() { return deliveryPersonMobile; }
    public void setDeliveryPersonMobile(String deliveryPersonMobile) { this.deliveryPersonMobile = deliveryPersonMobile; }
    public String getPackageDescription() { return packageDescription; }
    public void setPackageDescription(String packageDescription) { this.packageDescription = packageDescription; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
