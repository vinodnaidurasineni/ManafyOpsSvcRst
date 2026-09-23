package com.manafy.ops.manualrequest.service;

import com.manafy.ops.manualrequest.entity.ManualAssignmentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Ops -> Community status/assignment callback.
 *
 * When a manual request changes state (assigned, in-progress, completed,
 * cancelled) Ops projects a coarse status + the assignee's name/phone back to
 * Community so the resident sees it. Only Community-source requests receive a
 * callback (identified by sourceSystem=COMMUNITY + sourceId = the Community
 * booking id).
 *
 * Best-effort + @Async (mirrors the Community NotificationService/OpsIntegration
 * pattern): failures are logged, never thrown — Ops is authoritative for
 * operational status and Community can also reconcile by polling. Authenticated
 * by the shared service token (X-Service-Token).
 */
@Service
public class CommunityCallbackService {

    private static final Logger log = LoggerFactory.getLogger(CommunityCallbackService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${manafy.integration.community-base-url:}")
    private String communityBaseUrl;

    @Value("${manafy.integration.service-token:}")
    private String serviceToken;

    public boolean isConfigured() {
        return communityBaseUrl != null && !communityBaseUrl.isBlank();
    }

    @Async
    public void pushStatus(ManualAssignmentRequest r) {
        // Only Community-originated requests have a resident-facing counterpart.
        if (!"COMMUNITY".equals(r.getSourceSystem()) || r.getSourceId() == null) return;
        if (!isConfigured()) {
            log.info("Community base-url not configured — skipping status callback for manual request {}", r.getId());
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sourceId", r.getSourceId());          // the Community booking id
            payload.put("opsRequestId", r.getId().toString());
            payload.put("opsStatus", r.getStatus());
            payload.put("assignedPersonName", r.getAssigneeName());
            payload.put("assignedPersonMobile", r.getAssigneePhone());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Token", serviceToken);

            String url = trimTrailingSlash(communityBaseUrl) + "/api/v1/services/bookings/ops-callback";
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, req, String.class);
            log.info("Pushed status {} for manual request {} back to Community", r.getStatus(), r.getId());
        } catch (Exception e) {
            log.error("Community status callback failed for manual request {}: {}", r.getId(), e.getMessage());
        }
    }

    private String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
