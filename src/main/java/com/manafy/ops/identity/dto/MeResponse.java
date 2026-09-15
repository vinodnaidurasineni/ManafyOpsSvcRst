package com.manafy.ops.identity.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response for GET /auth/me — the resolved principal that drives the client's
 * permission-based navigation (Artifact #3 §1). Roles + flattened permission
 * grants with scope so the frontend can pre-filter UX (backend still enforces).
 */
public record MeResponse(
        UUID userId,
        String displayName,
        String email,
        List<String> roles,
        List<PermissionGrant> permissions
) {
    /** A permission plus the scope(s) it is granted under. */
    public record PermissionGrant(String permission, String scope, List<String> scopeIds) {}
}
