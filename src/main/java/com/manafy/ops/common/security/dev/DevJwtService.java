package com.manafy.ops.common.security.dev;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * DEV-ONLY HS256 token mint/verify. Produces tokens shaped like a Cognito access
 * token ({@code sub} + {@code token_use=access}) so the existing
 * {@code CurrentUserService} resolves them exactly as it would a Cognito token —
 * no change to the authorization path.
 *
 * <p>Not wired unless {@link DevAuthProperties#isEnabled()} is true.
 */
@Service
public class DevJwtService {

    private static final String ISSUER = "manafy-ops-dev";

    private final DevAuthProperties props;

    public DevJwtService(DevAuthProperties props) {
        this.props = props;
    }

    /** Mint a signed access token for the given Cognito subject. */
    public String accessToken(String sub) {
        return sign(sub, "access", props.getAccessTokenMinutes());
    }

    /** Mint a signed refresh token for the given Cognito subject. */
    public String refreshToken(String sub) {
        return sign(sub, "refresh", props.getRefreshTokenMinutes());
    }

    private String sign(String sub, String tokenUse, long minutes) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(sub)
                    .issuer(ISSUER)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(minutes * 60)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("token_use", tokenUse)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secretBytes()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint dev token", e);
        }
    }

    /**
     * Verify a dev token's signature + expiry and return its subject, or null if
     * the token is invalid/expired or not a dev token.
     */
    public String verifyAndGetSubject(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secretBytes());
            if (!jwt.verify(verifier)) return null;
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())) return null;
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date())) return null;
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Mint a short-lived OTP reference token that embeds the target mobile. */
    public String otpReference(String mobile) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(mobile)
                    .issuer(ISSUER)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(300))) // 5 min
                    .jwtID(UUID.randomUUID().toString())
                    .claim("token_use", "otpref")
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secretBytes()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint dev otp reference", e);
        }
    }

    /** Verify an OTP reference token and return the embedded mobile, or null. */
    public String verifyOtpReferenceAndGetMobile(String ref) {
        try {
            SignedJWT jwt = SignedJWT.parse(ref);
            if (!jwt.verify(new MACVerifier(secretBytes()))) return null;
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())) return null;
            if (!"otpref".equals(claims.getStringClaim("token_use"))) return null;
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date())) return null;
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Verify a refresh token specifically (must carry token_use=refresh). */
    public String verifyRefreshAndGetSubject(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secretBytes()))) return null;
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())) return null;
            if (!"refresh".equals(claims.getStringClaim("token_use"))) return null;
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date())) return null;
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] secretBytes() {
        // HS256 requires >= 256-bit key; pad if a short secret was configured.
        byte[] raw = props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) return raw;
        byte[] padded = new byte[32];
        System.arraycopy(raw, 0, padded, 0, raw.length);
        for (int i = raw.length; i < 32; i++) padded[i] = (byte) (i * 7 + 13);
        return padded;
    }
}
