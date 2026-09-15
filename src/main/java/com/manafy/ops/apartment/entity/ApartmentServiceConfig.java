package com.manafy.ops.apartment.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * Which catalog services an apartment enables (§26). Phase 2 = enablement only;
 * no service-request/dispatch lifecycle. Named ApartmentServiceConfig to avoid
 * clashing with the Spring stereotype "Service" naming in this domain.
 */
@Entity
@Table(name = "apartment_service",
        uniqueConstraints = @UniqueConstraint(name = "uk_apt_service", columnNames = {"apartment_id", "service_code"}))
public class ApartmentServiceConfig extends BaseEntity {

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(name = "service_code", nullable = false, length = 60)
    private String serviceCode;

    @Column(name = "service_name", length = 150)
    private String serviceName;

    /** ENABLED | DISABLED. */
    @Column(nullable = false, length = 20)
    private String state = "ENABLED";

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
}
