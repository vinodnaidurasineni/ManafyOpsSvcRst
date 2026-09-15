package com.manafy.ops.identity.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * An internal Manafy Operations user (staff/admin persona). The Manafy DB — not
 * Cognito — is the source of truth for authorization; Cognito only authenticates.
 *
 * Table name {@code ops_user} (owner decision Q-F2) avoids the reserved word
 * "user" and keeps per-service naming clear.
 *
 * MVP (design DD-39): only internal Ops/Admin personas authenticate here.
 * Technician/Helper/Vendor-staff logins are deferred to a future app.
 */
@Entity
@Table(name = "ops_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ops_user_cognito_sub", columnNames = "cognito_sub"),
        @UniqueConstraint(name = "uk_ops_user_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_ops_user_mobile", columnNames = "mobile")
})
public class OpsUser extends BaseEntity {

    /** Cognito subject (identity link). Null until first Cognito login links it. */
    @Column(name = "cognito_sub", length = 255)
    private String cognitoSub;

    /**
     * Reference to the canonical person in ManafyCommunitySvcRst ({@code customer.id}),
     * when this Ops user is the same human as a Community customer. This is a
     * PROJECTION REFERENCE only — NOT a database foreign key (the two services own
     * separate databases; §15/§17 of the service-boundary doc). The authoritative
     * cross-service identity key remains the Cognito {@code sub}; this column is a
     * convenience link populated out-of-band (e.g. when the shared pool goes live and
     * subs align). Nullable and non-breaking. Ops is NEVER the source of truth for
     * customer identity.
     */
    @Column(name = "community_customer_id")
    private java.util.UUID communityCustomerId;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String mobile;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    /** ACTIVE | DISABLED. Fail-closed: anything else is denied at auth time. */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** Guard-rail flag; never the sole authorization check. */
    @Column(name = "is_super_admin", nullable = false)
    private boolean superAdmin = false;

    @Column(name = "last_login_at")
    private java.time.LocalDateTime lastLoginAt;

    public String getCognitoSub() { return cognitoSub; }
    public void setCognitoSub(String cognitoSub) { this.cognitoSub = cognitoSub; }
    public java.util.UUID getCommunityCustomerId() { return communityCustomerId; }
    public void setCommunityCustomerId(java.util.UUID communityCustomerId) { this.communityCustomerId = communityCustomerId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isSuperAdmin() { return superAdmin; }
    public void setSuperAdmin(boolean superAdmin) { this.superAdmin = superAdmin; }
    public java.time.LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(java.time.LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
