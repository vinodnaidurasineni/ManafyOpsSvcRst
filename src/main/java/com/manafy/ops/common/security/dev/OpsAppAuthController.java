package com.manafy.ops.common.security.dev;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import com.manafy.ops.workforce.repository.HelperRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DEV-ONLY app-shaped auth aliases for the ManafyOps mobile app.
 *
 * The ManafyOps app expects a maid OTP verify that returns {accessToken, role,
 * firstName} and an admin username/password login that returns {accessToken}.
 * The generic dev verify-otp (DevAuthController) returns {accessToken,
 * refreshToken, userId} only. This controller adds the exact aliases the app
 * calls, reusing DevJwtService to mint tokens and resolving role/firstName from
 * the seeded OpsUser + Helper. Registered only when dev-auth is enabled; Cognito
 * remains the production path (this is inert under a real pool).
 */
@RestController
@ConditionalOnProperty(name = "manafy.dev-auth.enabled", havingValue = "true")
public class OpsAppAuthController {

    private final DevAuthProperties props;
    private final DevJwtService devJwt;
    private final OpsUserRepository userRepo;
    private final HelperRepository helperRepo;

    public OpsAppAuthController(DevAuthProperties props, DevJwtService devJwt,
                               OpsUserRepository userRepo, HelperRepository helperRepo) {
        this.props = props;
        this.devJwt = devJwt;
        this.userRepo = userRepo;
        this.helperRepo = helperRepo;
    }

    /**
     * Maid OTP verify (app: authApi.verifyEmployeeOtp → /auth/maid/verify-otp).
     * Returns {accessToken, refreshToken, role, firstName}. Role is HELPER when
     * the account maps to a MAID helper, else derived from the account.
     */
    @PostMapping("/api/v1/auth/maid/verify-otp")
    public ApiResponse<Map<String, Object>> maidVerifyOtp(@RequestBody Map<String, String> body) {
        String ref = body.get("otpReferenceId");
        String otp = body.get("otp");
        String mobile = ref == null ? null : devJwt.verifyOtpReferenceAndGetMobile(ref);
        if (mobile == null) {
            throw new BusinessException("AUTH_002", "Invalid or expired OTP reference", HttpStatus.BAD_REQUEST);
        }
        if (otp == null || !otp.equals(props.getOtp())) {
            throw new BusinessException("AUTH_006", "Invalid OTP", HttpStatus.UNAUTHORIZED);
        }

        String sub = DevUserSeeder.subForMobile(mobile);
        OpsUser user = userRepo.findByCognitoSubAndDeletedFalse(sub)
                .orElseThrow(() -> new BusinessException("AUTH_NO_ACCOUNT",
                        "No account for this number.", HttpStatus.UNAUTHORIZED));

        // Role: HELPER if this mobile maps to a MAID helper; otherwise treat as
        // an ops staff role (the app routes non-HELPER employeeRoles to admin).
        String role = helperRepo.findByPhoneAndDeletedFalse(mobile).isPresent() ? "HELPER" : "AREA_MANAGER";
        String firstName = firstNameOf(user.getDisplayName());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("accessToken", devJwt.accessToken(sub));
        res.put("refreshToken", devJwt.refreshToken(sub));
        res.put("role", role);
        res.put("firstName", firstName);
        res.put("userId", user.getId().toString());
        return ApiResponse.ok(res);
    }

    /**
     * Admin username/password login (app: authApi.adminLogin → /admin/auth/login).
     * DEV: accepts the seeded Super Admin (username "admin", password "admin"),
     * or any dev mobile as username with the dev OTP as password.
     */
    @PostMapping("/api/v1/admin/auth/login")
    public ApiResponse<Map<String, Object>> adminLogin(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        // Dev credentials: admin/admin maps to the seeded SUPER_ADMIN (7000000004).
        String mobile;
        if ("admin".equalsIgnoreCase(username) && "admin".equals(password)) {
            mobile = "7000000004";
        } else if (username != null && username.matches("\\d{10}") && props.getOtp().equals(password)) {
            mobile = username;
        } else {
            throw new BusinessException("AUTH_006", "Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        String sub = DevUserSeeder.subForMobile(mobile);
        OpsUser user = userRepo.findByCognitoSubAndDeletedFalse(sub)
                .orElseThrow(() -> new BusinessException("AUTH_NO_ACCOUNT",
                        "No admin account.", HttpStatus.UNAUTHORIZED));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("accessToken", devJwt.accessToken(sub));
        res.put("refreshToken", devJwt.refreshToken(sub));
        res.put("role", "ADMIN");
        res.put("firstName", firstNameOf(user.getDisplayName()));
        res.put("userId", user.getId().toString());
        return ApiResponse.ok(res);
    }

    private String firstNameOf(String displayName) {
        if (displayName == null || displayName.isBlank()) return "User";
        int sp = displayName.indexOf(' ');
        return sp > 0 ? displayName.substring(0, sp) : displayName;
    }
}
