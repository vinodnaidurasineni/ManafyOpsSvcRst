package com.manafy.ops.common.security.dev;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DEV-ONLY local OTP login, mirroring the Community app's legacy OTP contract so
 * the Operations mobile app can sign in without a Cognito User Pool.
 *
 * <p>Registered ONLY when {@code manafy.dev-auth.enabled=true}. Endpoints:
 * <pre>
 *   POST /api/v1/auth/send-otp    { mobile }              -> { otpReferenceId, devOtp, message }
 *   POST /api/v1/auth/verify-otp  { otpReferenceId, otp } -> { accessToken, refreshToken, userId }
 *   POST /api/v1/auth/refresh     { refreshToken }        -> { accessToken, refreshToken }
 * </pre>
 *
 * The accepted mobiles are the ones provisioned by {@link DevUserSeeder}
 * (7000000001..7000000004) and the OTP is the fixed dev code.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "manafy.dev-auth.enabled", havingValue = "true")
public class DevAuthController {

    private final DevAuthProperties props;
    private final DevJwtService devJwt;
    private final OpsUserRepository userRepo;

    public DevAuthController(DevAuthProperties props, DevJwtService devJwt, OpsUserRepository userRepo) {
        this.props = props;
        this.devJwt = devJwt;
        this.userRepo = userRepo;
    }

    @PostMapping("/send-otp")
    public ApiResponse<Map<String, String>> sendOtp(@RequestBody Map<String, String> body) {
        String mobile = normalizeMobile(body.get("mobile"));
        if (mobile == null) {
            throw new BusinessException("AUTH_001", "Valid 10-digit mobile required", HttpStatus.BAD_REQUEST);
        }
        // The account MUST already be provisioned here (ops personas + dev accounts).
        // Rejecting unknown numbers lets the mobile app fall back to the Community
        // backend for helper/maid logins that live there instead.
        String sub = DevUserSeeder.subForMobile(mobile);
        userRepo.findByCognitoSubAndDeletedFalse(sub)
                .orElseThrow(() -> new BusinessException("AUTH_NO_ACCOUNT",
                        "No account found for this mobile number", HttpStatus.NOT_FOUND));
        // Random 6-digit OTP per request (matches the Community app). The OTP is
        // embedded in the signed reference so verification stays stateless.
        String otp = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        String ref = devJwt.otpReference(mobile, otp);
        Map<String, String> res = new LinkedHashMap<>();
        res.put("otpReferenceId", ref);
        res.put("message", "OTP sent to +91" + mobile);
        // Local-dev convenience: echo the OTP so the app can auto-fill it, exactly
        // like the Community backend's otp.expose-in-response behaviour.
        res.put("devOtp", otp);
        return ApiResponse.ok(res);
    }

    @PostMapping("/verify-otp")
    public ApiResponse<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> body) {
        String ref = body.get("otpReferenceId");
        String otp = body.get("otp");
        String mobile = ref == null ? null : devJwt.verifyOtpReferenceAndGetMobile(ref);
        if (mobile == null) {
            throw new BusinessException("AUTH_002", "Invalid or expired OTP reference", HttpStatus.BAD_REQUEST);
        }
        String expected = devJwt.otpFromReference(ref);
        if (otp == null || expected == null || !otp.equals(expected)) {
            throw new BusinessException("AUTH_006", "Invalid OTP", HttpStatus.UNAUTHORIZED);
        }

        String sub = DevUserSeeder.subForMobile(mobile);
        OpsUser user = userRepo.findByCognitoSubAndDeletedFalse(sub)
                .orElseThrow(() -> new BusinessException("AUTH_NO_ACCOUNT",
                        "No account found for this mobile number.", HttpStatus.UNAUTHORIZED));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("accessToken", devJwt.accessToken(sub));
        res.put("refreshToken", devJwt.refreshToken(sub));
        res.put("userId", user.getId().toString());
        res.put("firstName", firstName(user.getDisplayName()));
        return ApiResponse.ok(res);
    }

    private static String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "";
        int i = displayName.trim().indexOf(' ');
        return i < 0 ? displayName.trim() : displayName.trim().substring(0, i);
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        String rt = body.get("refreshToken");
        String sub = rt == null ? null : devJwt.verifyRefreshAndGetSubject(rt);
        if (sub == null) {
            throw new BusinessException("AUTH_007", "Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
        Map<String, String> res = new LinkedHashMap<>();
        res.put("accessToken", devJwt.accessToken(sub));
        res.put("refreshToken", devJwt.refreshToken(sub));
        return ApiResponse.ok(res);
    }

    /** Accepts a 10-digit number, optionally with +91 / spaces; returns 10 digits or null. */
    private String normalizeMobile(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        return digits.length() == 10 ? digits : null;
    }
}
