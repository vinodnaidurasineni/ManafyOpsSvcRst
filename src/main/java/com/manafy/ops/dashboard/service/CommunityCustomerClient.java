package com.manafy.ops.dashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Reads the customer count from Community (ManafyCommunitySvcRst), the system of
 * record for resident/customer identity. Ops does not own a customer entity, so
 * the count is fetched cross-service and authenticated by the shared service token
 * (X-Service-Token) — the same credential used for the manual-request intake and
 * status callback, never a resident/Ops JWT.
 *
 * Best-effort and defensive: if Community is not configured or the call fails, it
 * returns {@code null} so the caller can render a graceful "unavailable" state
 * rather than a misleading zero. Never throws to the caller.
 */
@Service
public class CommunityCustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CommunityCustomerClient.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${manafy.integration.community-base-url:}")
    private String communityBaseUrl;

    @Value("${manafy.integration.service-token:}")
    private String serviceToken;

    public boolean isConfigured() {
        return communityBaseUrl != null && !communityBaseUrl.isBlank();
    }

    /** @return the Community active-customer count, or null when unavailable. */
    @SuppressWarnings("unchecked")
    public Long fetchCustomerCount() {
        if (!isConfigured()) {
            log.debug("Community base-url not configured — customer count unavailable");
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Token", serviceToken);
            String url = trimTrailingSlash(communityBaseUrl) + "/api/v1/platform/customer-count";
            ResponseEntity<Map> res = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
            Object data = res.getBody() == null ? null : res.getBody().get("data");
            if (data instanceof Map<?, ?> m && m.get("totalCustomers") != null) {
                return Long.valueOf(m.get("totalCustomers").toString());
            }
            return null;
        } catch (Exception e) {
            log.warn("Community customer-count fetch failed: {}", e.getMessage());
            return null;
        }
    }

    private String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
