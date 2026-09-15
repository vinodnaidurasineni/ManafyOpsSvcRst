package com.manafy.ops.org.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

/** A geographic region (Artifact #1 §2). Region → Area → (later) Apartment. */
@Entity
@Table(name = "region", uniqueConstraints = @UniqueConstraint(name = "uk_region_code", columnNames = "code"))
public class Region extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(nullable = false, length = 80)
    private String country = "India";

    @Column(nullable = false, length = 60)
    private String timezone = "Asia/Kolkata";

    /** ACTIVE | INACTIVE. */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
