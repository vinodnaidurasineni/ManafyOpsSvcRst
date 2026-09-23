package com.manafy.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Manafy Operations Platform backend (ManafyOpsSvcRst).
 *
 * Phase 1 — Foundation: identity, RBAC, scope, organization/area, area↔field-officer,
 * Cognito authentication, the authorization engine (permission + scope + relationship
 * predicate), audit, activity logging, idempotency, configuration and feature flags.
 *
 * Cognito = authentication. Manafy PostgreSQL = authorization / source of truth.
 */
@SpringBootApplication
@EnableAsync
public class OpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpsApplication.class, args);
    }
}
