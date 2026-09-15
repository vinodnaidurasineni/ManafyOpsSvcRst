# Phase 4A — Service Request Foundation

The core Service Request domain for `ManafyOpsSvcRst`, built on the Phase 1
foundation (identity, RBAC, scope, audit, idempotency, error envelope), the Phase 2
apartment domain, and the Phase 3 workforce domain. This is the record that later
phases (Operations, Dispatch, Payments, Notifications, mobile) will build on.

**Phase 4A is intentionally minimal**: it delivers the Service Request record, a
create/view/list/cancel API, and a two-state lifecycle (`NEW → CANCELLED`). It reuses
every piece of existing infrastructure and introduces no parallel mechanisms.

---

## 1. Scope & non-goals

**In scope**
- `service_request` table + `ServiceRequest` entity/repository.
- Create, get, list (filtered + paginated), cancel.
- Lifecycle `NEW → CANCELLED` via a dedicated state machine.
- HR/operations-centric RBAC mapped to already-cataloged `SERVICE_REQUEST_*` permissions.
- IDOR-safe, apartment/area/region-anchored scope reusing the Phase 2 model.

**Explicitly deferred (NOT in this phase)**
- Workforce **assignment** (technician / helper / vendor).
- **Dispatch**, **scheduling**, **field visits**.
- **Payments**, invoices; **notifications**, SMS, push.
- **Technician login** and **mobile-specific** endpoints.
- ALB / API Gateway / AWS infrastructure.
- The `ASSIGNED / ACCEPTED / IN_PROGRESS / COMPLETED / REOPENED` statuses and the
  `SERVICE_REQUEST_UPDATE` / `SERVICE_REQUEST_REOPEN` permissions (cataloged but
  unmapped).

---

## 2. Reused foundation (no re-implementation)

| Concern       | Reused component                                                    |
|---------------|---------------------------------------------------------------------|
| AuthN         | Cognito JWT → `CurrentUserService` (`auth.currentUserId()`)         |
| AuthZ (perm)  | `AuthorizationService.requirePermission`                            |
| AuthZ (scope) | `AuthorizationService.authorize(user, perm, ResourceRef)` + `ScopeService.covers` |
| Scope anchor  | `ResourceScopeResolver.serviceRequest(...)` (new method, same pattern as `apartment(...)`) |
| Audit         | `AuditService.audit(...)` + `AuditService.activity(...)`            |
| Idempotency   | `IdempotencyService.register(key, endpoint, actor)`                 |
| Concurrency   | JPA `@Version` on `BaseEntity` + explicit `CONCURRENCY_CONFLICT` check |
| Envelope      | `ApiResponse` (single) / `PageResponse` (list)                      |
| Errors        | `BusinessException` + existing error-code registry                  |
| State machine | New `ServiceRequestStateMachine` following the Workforce/Onboarding template |

No authorization, audit, idempotency, envelope, exception, or state-machine
infrastructure was duplicated.

---

## 3. Database schema (`V9__phase4_service_request_domain.sql`)

One table, `service_request`, dialect-neutral (H2 + PostgreSQL), matching the
Phase 1/2/3 conventions: application UUID PK, `VARCHAR + CHECK` enums, `TIMESTAMP`,
`BIGINT version`, soft-delete columns.

| Column              | Type          | Notes                                              |
|---------------------|---------------|----------------------------------------------------|
| `id`                | UUID PK       | application-generated                              |
| BaseEntity columns  |               | `created_at/by`, `updated_at/by`, `deleted`, `deleted_at`, `version` |
| `reference_no`      | VARCHAR(40)   | unique human reference (`SR-XXXXXXXXXXXX`)         |
| `apartment_id`      | UUID FK       | → `apartment(id)`                                  |
| `area_id`           | UUID FK       | → `area(id)`; **server-derived** from the apartment |
| `region_id`         | UUID FK       | → `region(id)`; server-derived                     |
| `requester_user_id` | UUID FK       | → `ops_user(id)`; the actor who raised it          |
| `category`          | VARCHAR(40)   | CHECK: PLUMBING/ELECTRICAL/HVAC/CLEANING/SECURITY/GENERAL/OTHER |
| `priority`          | VARCHAR(20)   | CHECK: LOW/MEDIUM/HIGH/URGENT (default MEDIUM)      |
| `description`       | VARCHAR(2000) | required                                           |
| `status`            | VARCHAR(20)   | CHECK: NEW/CANCELLED (default NEW)                 |
| `cancelled_at`      | TIMESTAMP     | set on cancel                                      |
| `cancel_reason`     | VARCHAR(1000) | optional                                           |

**Indexes** (only those justified by the list/filter API + ordering; no speculative
indexes): `idx_sr_apartment`, `idx_sr_area`, `idx_sr_requester`, `idx_sr_status`,
`idx_sr_priority`, `idx_sr_created_at`. Constraints follow `uk_`/`fk_`/`ck_` naming.

The `area_id`/`region_id` are denormalized from the apartment at create time so
authorization resolves scope from the request's own persisted columns.

---

## 4. Lifecycle / state machine

`ServiceRequestStateMachine` (in `servicerequest/domain`) mirrors the existing
`WorkforceStateMachine` template:

```
NEW → CANCELLED
CANCELLED → (terminal)
```

- The only transition is `NEW → CANCELLED`, performed by the explicit cancel action.
- Illegal transitions (e.g. cancelling an already-cancelled request) raise
  `INVALID_STATE_TRANSITION` (409) via `requireTransition`.
- There is no generic status-update endpoint — arbitrary status writes are impossible.
- `ASSIGNED/IN_PROGRESS/COMPLETED` are deliberately not modeled (deferred).

---

## 5. API surface (`/api/v1/service-requests`)

| Method & path                              | Permission              | Envelope       |
|--------------------------------------------|-------------------------|----------------|
| `POST /service-requests`                   | `SERVICE_REQUEST_CREATE`| `ApiResponse`  |
| `GET /service-requests/{id}`               | `SERVICE_REQUEST_VIEW`  | `ApiResponse`  |
| `GET /service-requests`                    | `SERVICE_REQUEST_VIEW`  | `PageResponse` |
| `POST /service-requests/{id}/cancel`       | `SERVICE_REQUEST_CANCEL`| `ApiResponse`  |

- Create accepts an optional `Idempotency-Key` header; body = `{apartmentId, category, priority?, description}`. `area_id`/`region_id` are derived server-side (never trusted from the client).
- List filters (justified by the domain): `apartmentId`, `areaId`, `requesterId`, `status`, `priority`, plus `page`/`pageSize`. Ordered newest-first by `createdAt` (tiebreak on reference). List returns `PageResponse` directly; single/mutation endpoints wrap in `ApiResponse.ok(...)`, matching the apartment convention.
- Cancel accepts optional `{reason}` body, an optional `?version=` for optimistic concurrency, and an optional `Idempotency-Key` header.

---

## 6. Permissions & RBAC

The `SERVICE_REQUEST_*` permissions were **already in the catalog** (OPERATIONS
domain) from an earlier phase — **no new permissions were created**. Phase 4A only
**maps** the relevant ones to roles. Listing reuses `SERVICE_REQUEST_VIEW` (no
separate `_LIST` permission, matching `APARTMENT_VIEW`).

| Role                      | VIEW | CREATE | CANCEL |
|---------------------------|:----:|:------:|:------:|
| `SUPER_ADMIN`             | ✅ (catch-all) | ✅ | ✅ |
| `MANAFY_ADMIN`            | ✅ | ✅ | ✅ |
| `OPERATIONS_COORDINATOR`  | ✅ | ✅ | ✅ |
| `AREA_OPERATIONS_MANAGER` | ✅ | ✅ | ✅ |
| `FIELD_OFFICER`           | ✅ | ✅ | ✅ (scope-limited to their area at runtime) |
| `SUPPORT_AGENT`           | ✅ | ✅ | ❌ (cancellation is an ops/field decision) |
| `FINANCE` / `HR` / `REPORTING` / `APARTMENT_ONBOARDER` | ❌ | ❌ | ❌ |

No existing authorization was weakened. The `SERVICE_REQUEST_UPDATE` and
`SERVICE_REQUEST_REOPEN` permissions remain unmapped (deferred). The `SUPER_ADMIN`
catch-all block is re-applied as the **final** statement in `R__` so it holds every
permission (keeps `SeedIdempotencyTest` green).

---

## 7. Authorization & scope model (IDOR)

A service request is anchored on its apartment + area + region, all resolved
server-side by `ResourceScopeResolver.serviceRequest(srId, apartmentId, areaId,
regionId)` from the request's persisted columns. `AuthorizationService.authorize`
then runs the permission gate **and** `ScopeService.covers`, which grants access via:

- **APARTMENT** scope grant, or
- **AREA** scope grant (including Field Officer area-ownership via
  `area_field_officer`), or
- **REGION → AREA** inheritance, or
- **GLOBAL** scope.

Because anchors come from the row (never client input), a caller cannot reach another
request by changing the UUID — a mismatched scope yields `SCOPE_DENIED` (403) with no
response body. On create, the same check is applied against the target apartment's
area/region, so a request cannot be raised against an apartment outside the caller's
scope. This is the identical model used for apartments in Phase 2 — no parallel scope
mechanism was introduced.

---

## 8. Idempotency

`POST /service-requests` and `POST /service-requests/{id}/cancel` accept an optional
`Idempotency-Key` header and call `IdempotencyService.register(key, endpoint, actor)`
after the permission gate. A repeated key for the same endpoint is rejected with
`DUPLICATE_REQUEST` (409) — the existing mechanism, not a second one.

---

## 9. Concurrency

`ServiceRequest` inherits `@Version` from `BaseEntity`. Cancel accepts an optional
expected `version`; a stale value raises `CONCURRENCY_CONFLICT` (409) via the same
explicit check pattern used by apartments, in addition to JPA optimistic locking.

---

## 10. Audit

`create` and `cancel` each write an audit record via
`AuditService.audit(actor, primaryRole, action, "SERVICE_REQUEST", id, before, after,
reason)` plus an `AuditService.activity(...)` event — matching the apartment pattern.
No sensitive data is logged; the description is not echoed into the audit reason.

---

## 11. Validation

- Bean validation on the request DTO: `apartmentId` (`@NotNull`), `category`
  (`@NotBlank`), `description` (`@NotBlank`, `@Size(max=2000)`).
- Service-level: `category` must be an allowed value and `priority` (if provided)
  must be valid — otherwise `VALIDATION_ERROR` (400).
- The apartment must exist and not be soft-deleted (`VALIDATION_ERROR` if not); the
  request's `area_id`/`region_id` are copied from that apartment, so an
  apartment/area combination inconsistent with the domain is impossible by
  construction (the client never supplies the area).

---

## 12. Tests

Baseline before Phase 4A: **113 tests**. After Phase 4A: **139 tests, 0 failures,
0 errors** (`mvn test`, H2 + `@ActiveProfiles("test")`). No regression — the Phase
1/2/3 suites, including `SeedIdempotencyTest`'s SUPER_ADMIN permission-count
assertion, remain green.

New tests (26):

| Test class                     | Kind        | Count | Covers                                                              |
|--------------------------------|-------------|-------|---------------------------------------------------------------------|
| `ServiceRequestStateMachineTest` | Unit      | 5     | Valid NEW→CANCELLED, terminal CANCELLED, deferred states not modeled, 409 on illegal |
| `ServiceRequestHttpIT`         | Integration | 12    | Create/get, unknown 404, list, list-by-status, cancel, cancel-twice 409, missing-field 400, invalid category/priority 400, unknown apartment 400, stale version 409, idempotency dup |
| `ServiceRequestRbacIT`         | RBAC        | 5     | Operations coordinator can create; finance cannot create; no-role cannot view; support can view but not cancel; disabled user blocked |
| `ServiceRequestIdorIT`         | IDOR/scope  | 4     | FO cross-area read denied (SCOPE_DENIED, no body); FO own-area read allowed; FO cross-area cancel denied; list excludes out-of-scope |

---

## 13. Migration & environment validation

- **H2 (dev/test): PASSED.** Migrations `V1`–`V9` + the repeatable `R__` seed apply,
  the seed is idempotent, and the full 139-test suite boots the Spring context with
  Flyway migrate **and** Hibernate `ddl-auto=validate` — so the `ServiceRequest`
  entity validates against the Flyway-built `service_request` schema.
- **PostgreSQL: PENDING.** The Docker daemon is not running in this environment and
  no local PostgreSQL is reachable, so PostgreSQL validation was **not executed** and
  is **not claimed**. The runbook and sign-off checklist in
  [`docs/POSTGRES-VALIDATION.md`](./POSTGRES-VALIDATION.md) apply to `V1`–`V9`; run it
  in a DEV environment before staging.
- **`ManafyCommunitySvcRst`: untouched** — `git status --short` on that repository is
  empty; no files were modified there.

---

## 14. Deferred functionality (restated)

Assignment, dispatch, scheduling, field visits, payments, invoices, notifications,
SMS, push, technician login, and mobile-specific endpoints are **all deferred** to
later phases. Phase 4A provides only the Service Request record and its create /
view / list / cancel foundation.

**Recommended next phase:** Phase 4B — Operations & Dispatch (extend the lifecycle
with ASSIGNED/IN_PROGRESS/COMPLETED, map `SERVICE_REQUEST_UPDATE`, and assign the
request to `ACTIVE` workforce via the Phase 3 `isOperational` readiness hook).
