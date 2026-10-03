package com.manafy.ops.compat.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.dev.DevJwtService;
import com.manafy.ops.common.security.dev.DevUserSeeder;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ManafyOps mobile COMPATIBILITY — admin username/password login.
 *
 * The ported ManafyOps app offers an "Admin? Login with username" path
 * (AdminLoginScreen → POST /admin/auth/login). The new backend authenticates via
 * Cognito / dev-OTP and has no username/password store. For local dev this adapter
 * accepts the legacy default admin credential ({@code admin} / {@code admin@123})
 * and mints a dev token for the seeded SUPER_ADMIN account (7000000004), so the
 * admin flow is reachable without Cognito.
 *
 * Registered ONLY when {@code manafy.dev-auth.enabled=true} (same guard as
 * DevAuthController). Never active with a real Cognito issuer / in production.
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@ConditionalOnProperty(name = "manafy.dev-auth.enabled", havingValue = "true")
public class AdminAuthCompatController {

    private static final String SUPER_ADMIN_MOBILE = "7000000004";

    private final DevJwtService devJwt;
    private final OpsUserRepository userRepo;

    public AdminAuthCompatController(DevJwtService devJwt, OpsUserRepository userRepo) {
        this.devJwt = devJwt;
        this.userRepo = userRepo;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        // Legacy default admin credential (matches old ManafySvcRst bootstrap admin).
        boolean ok = "admin".equals(username) && "admin@123".equals(password);
        if (!ok) {
            throw new BusinessException("AUTH_INVALID", "Invalid admin credentials", HttpStatus.UNAUTHORIZED);
        }
        String sub = DevUserSeeder.subForMobile(SUPER_ADMIN_MOBILE);
        OpsUser user = userRepo.findByCognitoSubAndDeletedFalse(sub)
                .orElseThrow(() -> new BusinessException("AUTH_NO_ACCOUNT",
                        "Dev super-admin not seeded", HttpStatus.UNAUTHORIZED));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("accessToken", devJwt.accessToken(sub));
        res.put("refreshToken", devJwt.refreshToken(sub));
        res.put("userId", user.getId().toString());
        res.put("role", "SUPER_ADMIN");
        res.put("firstName", firstName(user.getDisplayName()));
        return ApiResponse.ok(res);
    }

    private static String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "Admin";
        int i = displayName.trim().indexOf(' ');
        return i < 0 ? displayName.trim() : displayName.trim().substring(0, i);
    }
}
