package com.manafy.ops.common.security;

import com.manafy.ops.common.authz.entity.ActivityLog;
import com.manafy.ops.common.authz.entity.AuditLog;
import com.manafy.ops.common.authz.repository.ActivityLogRepository;
import com.manafy.ops.common.authz.repository.AuditLogRepository;
import com.manafy.ops.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Central, reusable audit + activity recorder (design DD-09, spec §48/§51/§90).
 *
 * Not a controller concern: services/the AuthorizationService call this for
 * sensitive actions. Best-effort — auditing must never break the request path.
 * Append-only: only writes are performed here.
 *
 * HARD RULE: never persist secrets (tokens, OTPs, passwords, bootstrap values).
 */
@Service
public class AuditService {

    private final AuditLogRepository auditRepo;
    private final ActivityLogRepository activityRepo;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public AuditService(AuditLogRepository auditRepo, ActivityLogRepository activityRepo,
                        ObjectProvider<HttpServletRequest> requestProvider) {
        this.auditRepo = auditRepo;
        this.activityRepo = activityRepo;
        this.requestProvider = requestProvider;
    }

    /** Record a sensitive action. before/after are JSON strings (nullable). */
    public void audit(UUID actorUserId, String actorRole, String action, String resourceType,
                      UUID resourceId, String beforeState, String afterState, String reason) {
        try {
            AuditLog a = new AuditLog();
            a.setActorUserId(actorUserId);
            a.setActorRole(actorRole);
            a.setAction(action);
            a.setResourceType(resourceType);
            a.setResourceId(resourceId);
            a.setBeforeState(beforeState);
            a.setAfterState(afterState);
            a.setReason(reason);
            a.setSource("API");
            HttpServletRequest req = requestProvider.getIfAvailable();
            if (req != null) {
                a.setIpAddress(clientIp(req));
                a.setUserAgent(req.getHeader("User-Agent"));
                Object cid = req.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
                a.setCorrelationId(cid != null ? cid.toString() : null);
            }
            auditRepo.save(a);
        } catch (Exception ignored) {
            // Auditing must never break the request path.
        }
    }

    /** Convenience overload without before/after state. */
    public void audit(UUID actorUserId, String actorRole, String action, String resourceType,
                      UUID resourceId, String reason) {
        audit(actorUserId, actorRole, action, resourceType, resourceId, null, null, reason);
    }

    /** System-sourced audit (no HTTP request context), e.g. bootstrap promotion. */
    public void auditSystem(UUID actorUserId, String action, String resourceType, UUID resourceId, String reason) {
        try {
            AuditLog a = new AuditLog();
            a.setActorUserId(actorUserId);
            a.setAction(action);
            a.setResourceType(resourceType);
            a.setResourceId(resourceId);
            a.setReason(reason);
            a.setSource("SYSTEM");
            auditRepo.save(a);
        } catch (Exception ignored) {
        }
    }

    /** Record an operational activity-timeline event. */
    public void activity(String resourceType, UUID resourceId, UUID actorUserId, String event, String message) {
        try {
            ActivityLog l = new ActivityLog();
            l.setResourceType(resourceType);
            l.setResourceId(resourceId);
            l.setActorUserId(actorUserId);
            l.setEvent(event);
            l.setMessage(message);
            HttpServletRequest req = requestProvider.getIfAvailable();
            if (req != null) {
                Object cid = req.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
                l.setCorrelationId(cid != null ? cid.toString() : null);
            }
            activityRepo.save(l);
        } catch (Exception ignored) {
        }
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
