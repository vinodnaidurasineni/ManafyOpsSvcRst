package com.manafy.ops.common.security.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DEV-ONLY local authentication settings.
 *
 * <p>This exists purely so the mobile app can be exercised end-to-end on a laptop
 * without a real Amazon Cognito User Pool. It is DISABLED by default and must be
 * turned on explicitly via {@code manafy.dev-auth.enabled=true}. It is additionally
 * inert whenever Cognito is configured (a real issuer-uri is present), so it can
 * never weaken a real deployment.
 *
 * <p>NEVER enable this in a production profile.
 */
@Component
@ConfigurationProperties(prefix = "manafy.dev-auth")
public class DevAuthProperties {

    /** Master switch. Default false — no dev login unless explicitly enabled. */
    private boolean enabled = false;

    /** The fixed OTP accepted by the dev login (any of the seeded dev numbers). */
    private String otp = "123456";

    /** HS256 signing secret for dev-issued tokens (local only, not a real secret). */
    private String secret = "manafy-ops-dev-local-signing-secret-please-change-0123456789";

    /** Access-token lifetime in minutes. */
    private long accessTokenMinutes = 720; // 12h — convenient for a dev session

    /** Refresh-token lifetime in minutes. */
    private long refreshTokenMinutes = 20160; // 14 days

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getAccessTokenMinutes() { return accessTokenMinutes; }
    public void setAccessTokenMinutes(long accessTokenMinutes) { this.accessTokenMinutes = accessTokenMinutes; }

    public long getRefreshTokenMinutes() { return refreshTokenMinutes; }
    public void setRefreshTokenMinutes(long refreshTokenMinutes) { this.refreshTokenMinutes = refreshTokenMinutes; }
}
