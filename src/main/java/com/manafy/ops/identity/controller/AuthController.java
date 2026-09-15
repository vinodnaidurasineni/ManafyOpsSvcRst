package com.manafy.ops.identity.controller;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.identity.dto.MeResponse;
import com.manafy.ops.identity.entity.OpsUser;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Authentication-facing endpoints (Artifact #3 §1). {@code /auth/me} resolves the
 * current Cognito-authenticated principal to the Manafy user + roles + permission
 * grants with scope, which drives the client's permission-based navigation.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationContext authContext;

    public AuthController(AuthenticationContext authContext) {
        this.authContext = authContext;
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        OpsUser user = authContext.currentUser();
        List<String> roles = new ArrayList<>(authContext.roles(user.getId()));
        roles.sort(String::compareTo);
        List<MeResponse.PermissionGrant> grants = authContext.permissionGrants(user.getId());
        return ApiResponse.ok(new MeResponse(
                user.getId(), user.getDisplayName(), user.getEmail(), roles, grants));
    }

    /**
     * Logout is a client-side token discard for a stateless resource server;
     * there is no server session to invalidate. Endpoint exists for parity and
     * future device/session revocation.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // Ensure the caller is authenticated (throws 401 otherwise).
        authContext.currentUser();
        return ApiResponse.ok(null);
    }
}
