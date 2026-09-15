# Phase 3 — Workforce & HR Management

Operational workforce foundation for `ManafyOpsSvcRst`, built on the Phase 1
foundation (identity, RBAC, scope, audit, idempotency, error envelope) and the
Phase 2 apartment domain. This phase adds employees, technicians, helpers, vendors,
vendor staff, skills, certifications, area coverage, availability, and workforce
documents — with lifecycle state machines, HR-centric RBAC, a strict KYC≠Finance
data split, and IDOR-safe access. It deliberately excludes technician login and
service dispatch (deferred to Phase 4).

---

## 1. Scope & non-goals

**In scope**
- Master data: `skill`.
- Workforce entities: `employee`, `technician`, `helper`, `vendor`, `vendor_staff`.
- Workforce sub-resources: skills, area coverage, certifications, availability
  windows, documents, status history.
- Lifecycle state machines (DRAFT → ACTIVE → SUSPENDED/INACTIVE → TERMINATED).
- HR-centric RBAC with KYC≠Finance separation and PII masking.
- Scope resolution for technicians via their area coverage.

**Explicit non-goals (deferred to Phase 4)**
- No technician/helper **login** (they are managed records, not principals).
- No **dispatch**, assignment, scheduling, or field-visit execution.
- No finance/payout processing (only the KYC≠Finance *authorization* boundary).

---

## 2. Architectural approach

The Phase 1/2 layering is preserved end-to-end:

```
Controller (thin, /api/v1)  →  Service (authz + business + audit)  →  Domain (state machine)
                                      ↓
                              Repository (Spring Data JPA)
```

- Controllers only translate HTTP ↔ DTO and delegate; they never touch repositories.
- Services own authorization (`AuthorizationService`/`ScopeService`), validation,
  the lifecycle transition, audit writes, and idempotency registration.
- Entities are never serialized directly; every response is a DTO record from
  `WorkforceDtos`.
- All reused infrastructure (audit, idempotency, envelope, scope, error codes) is
  the **existing** Phase 1 machinery — nothing was forked.

---

## 3. Reused foundation (no re-implementation)

| Concern        | Reused component                                              |
|----------------|---------------------------------------------------------------|
| AuthN          | Cognito JWT → `CurrentUserService` → `OpsUser`                |
| AuthZ (perm)   | `AuthorizationService.requirePermission`                      |
| AuthZ (scope)  | `ScopeService.hasGlobal` / `visibleAreaIds`                   |
| Role assign    | `AuthorizationService.requireCanAssignRole`                   |
| Audit          | `AuditService.audit(...)`                                     |
| Idempotency    | `IdempotencyService.register(key, op, actor)`                 |
| Envelope       | `ApiResponse` flat `{success,errorCode,message,errorId,data}` |
| Paging         | `PageResponse.of / normalizePage / clampPageSize`             |
| Errors         | `BusinessException` (client-safe `code` + `status`)           |

---

## 4. Data model (migration `V8__phase3_workforce_domain.sql`)

Eleven workforce tables + one append-only history table. All follow Phase 1/2
conventions: application UUID PKs, `VARCHAR + CHECK` for enums/status, `TIMESTAMP`,
`BIGINT version` for optimistic locking, and soft-delete columns on the mutable
master records.

| Table                        | Purpose                                             |
|------------------------------|-----------------------------------------------------|
| `skill`                      | Reusable skill master data (GLOBAL).                |
| `employee`                   | Internal Manafy staff; a Field Officer links to `ops_user`. |
| `vendor`                     | External vendor organization.                       |
| `technician`                 | Direct or vendor-associated technician.             |
| `helper`                     | MANAFY / TECHNICIAN / VENDOR helper.                |
| `vendor_staff`               | People associated with a vendor (historical).       |
| `workforce_skill`            | Skill assignment (technician|helper).               |
| `workforce_area`             | Area coverage (technician|helper|vendor).           |
| `certification`              | Workforce certifications with expiry.               |
| `workforce_availability`     | Availability windows / leave.                       |
| `workforce_document`         | Document metadata (KYC/GENERAL/FINANCE categories). |
| `workforce_status_history`   | Append-only lifecycle transition log.               |

Referential integrity: `technician`/`helper` → `vendor`; `technician` → `region`;
`vendor_staff` → vendor(+technician/helper); `workforce_*` sub-tables → their
parent + `skill`/`area`; `workforce_document` → all four parent kinds + `ops_user`
(verifier). Polymorphic sub-resources are constrained by a `ck_*_ref` CHECK that
enforces exactly one parent id matching `workforce_kind`.

---

## 5. Enum / status vocabularies (CHECK-enforced)

| Column                              | Allowed values                                          |
|-------------------------------------|---------------------------------------------------------|
| `technician.status` / `helper` / `vendor` / `employee` | `DRAFT, ACTIVE, INACTIVE, SUSPENDED, TERMINATED` |
| `technician.availability_status`    | `AVAILABLE, BUSY, OFFLINE, ON_LEAVE`                    |
| `helper.relationship`               | `MANAFY, TECHNICIAN, VENDOR`                            |
| `employee.employee_type`            | `STAFF, FIELD_OFFICER, MANAGER`                         |
| `workforce_skill.skill_level`       | `BEGINNER, INTERMEDIATE, EXPERT`                        |
| `certification.status`              | `ACTIVE, EXPIRED, REVOKED`                              |
| `workforce_document.doc_category`   | `GENERAL, KYC, FINANCE`                                 |
| `workforce_document.status`         | `UPLOADED, UNDER_REVIEW, VERIFIED, REJECTED, EXPIRED`   |
| `skill.status`                      | `ACTIVE, INACTIVE`                                      |

Because these are DB CHECK constraints, invalid enum values are rejected in H2 and
PostgreSQL alike (no dialect-specific ENUM types were introduced).

---

## 6. Lifecycle state machine (`WorkforceStateMachine`)

A single shared state machine governs technician, helper, vendor, and employee.

```
DRAFT      → ACTIVE, TERMINATED
ACTIVE     → SUSPENDED, INACTIVE, TERMINATED
SUSPENDED  → ACTIVE, INACTIVE, TERMINATED
INACTIVE   → ACTIVE, TERMINATED
TERMINATED → (terminal)
```

- Transitions are **explicit actions** (e.g. `POST /technicians/{id}/activate`);
  arbitrary `status` updates through the update endpoint are impossible.
- Illegal transitions raise `INVALID_STATE_TRANSITION` (HTTP 409).
- `isOperational(status)` is true only for `ACTIVE` — the dispatch-readiness hook
  that Phase 4 will consume.
- Every transition is recorded (see §8) and audited.

---

## 7. Shared lifecycle service (`WorkforceLifecycleService`)

To avoid per-entity duplication, one service performs every transition:

```
transition(kind, id, fromStatus, toStatus, actor, reason)
  → WorkforceStateMachine.requireTransition(from, to)   // 409 if illegal
  → append workforce_status_history row                 // append-only
  → audit.audit(...)                                     // audit trail
```

Each entity service (`TechnicianService`, `HelperService`, `VendorService`,
`EmployeeService`) calls `transition(...)`, then sets the new status and, for
`TERMINATED`, sets `deletedAt`. History reads go through `lifecycle.history(kind, id)`.

---

## 8. Status history (append-only)

`workforce_status_history` is insert-only — rows are never updated or deleted. Each
row captures `workforce_kind`, `workforce_id`, `from_status`, `to_status`,
`changed_by`, `reason`, `created_at`. Exposed read-only via
`GET /api/v1/technicians/{id}/history`.

---

## 9. Permissions & role catalog (seed `R__`)

Phase 3 permissions were reconciled with the pre-seeded catalog and the missing ones
added, then mapped to roles. The `SUPER_ADMIN` catch-all mapping is re-applied
**last** in `R__` so it holds every Phase 3 permission (and the
`SeedIdempotencyTest` count assertion stays green).

Representative Phase 3 permissions: `EMPLOYEE_*`, `TECHNICIAN_*` (incl.
`_ACTIVATE/_SUSPEND/_DEACTIVATE/_TERMINATE/_PII_VIEW/_KYC_VIEW/_FINANCE_VIEW`),
`HELPER_*`, `VENDOR_*` (+ `VENDOR_STAFF_MANAGE`), `SKILL_VIEW/_MANAGE`,
`CERTIFICATION_MANAGE`, `WORKFORCE_SKILL_MANAGE`, `WORKFORCE_AREA_MANAGE`,
`WORKFORCE_AVAILABILITY_MANAGE`, `WORKFORCE_DOC_VIEW/_MANAGE/_VERIFY`,
`FIELD_OFFICER_MANAGE`.

---

## 10. Role → permission mapping (workforce)

| Role                   | Workforce capability                                                       |
|------------------------|----------------------------------------------------------------------------|
| `SUPER_ADMIN`          | Everything (catch-all, applied last).                                      |
| `HR`                   | Full workforce management incl. **KYC** and PII — **not** finance.         |
| `MANAFY_ADMIN`         | Broad manage — **not** KYC, **not** finance (those stay HR/Finance owned). |
| `OPERATIONS_COORDINATOR` | Read technicians (+PII)/helpers/vendors/skills for dispatch readiness.    |
| `FIELD_OFFICER`        | Read technicians/helpers/vendors/skills, **scope-limited** to their area.  |
| `AREA_OPERATIONS_MANAGER` | Read workforce + `FIELD_OFFICER_MANAGE`, employees.                     |
| `FINANCE`              | Technician **finance** view only — **not** KYC.                            |
| `SUPPORT_AGENT`        | Non-sensitive lookup only — **no** KYC/finance/PII.                        |
| `REPORTING`            | Read + export for reports.                                                 |

---

## 11. KYC ≠ Finance ≠ Support (data separation)

`workforce_document.doc_category` drives the required VIEW permission:

| Category  | Required permission          | Held by            |
|-----------|------------------------------|--------------------|
| `GENERAL` | `WORKFORCE_DOC_VIEW`         | HR, admin, …       |
| `KYC`     | `TECHNICIAN_KYC_VIEW`        | **HR only**        |
| `FINANCE` | `TECHNICIAN_FINANCE_VIEW`    | **Finance only**   |

Consequences (enforced by `WorkforceDocumentService`): Finance cannot open a KYC
document (403 FORBIDDEN), HR cannot open a finance-only document, and Support can
open neither. List endpoints filter documents to the categories the caller may see.

---

## 12. PII masking

`TechnicianDetailResponse` includes `piiMasked`. The controller masks `phone`,
`email`, and `dateOfBirth` unless the caller holds `TECHNICIAN_PII_VIEW`
(`TechnicianService.canViewPii`). Masking is applied at the edge, so even an
authorized reader without PII rights never receives raw PII. HR holds PII_VIEW;
a Field Officer reading a technician in their own area sees it masked.

---

## 13. Scope resolution (technician anchored by area)

A technician is anchored to the areas it covers (`workforce_area`).
`TechnicianService.authorizeTechnician` resolves scope:

1. Permission gate always applies (`requirePermission`).
2. `GLOBAL`-scoped users pass immediately.
3. Otherwise the technician must cover an area the caller can see
   (`ScopeService.visibleAreaIds`); if not → `SCOPE_DENIED`.
4. A technician with **no** area anchor is visible **only** to GLOBAL-scoped users;
   region/area-scoped users are denied (fail-closed).

Vendor, helper, and employee use a permission-only gate (no per-area scope) in this
phase; their area coverage exists in `workforce_area` for Phase 4 dispatch use.
`ResourceScopeResolver` was not extended for technicians because a technician's
anchor is multi-area — that logic lives in the service where the set semantics are
clear.

---

## 14. IDOR safety

All child/sub-resource operations resolve through the parent record's `load(...)`
(which enforces existence + scope), so a caller cannot reach a resource by guessing
an id:

- `vendor-staff/{id}` authorizes via its parent vendor.
- `workforce-documents/{id}` authorizes by the document's category **and** confirms
  the parent workforce record still exists.
- Cross-area technician reads fail with `SCOPE_DENIED` and **no** response body.

---

## 15. Field Officer — single source of truth

A Field Officer is an `ops_user` optionally described by an `employee` row of type
`FIELD_OFFICER`. Area↔FO ownership remains **only** in Phase 1's `area_field_officer`
table (managed by the Phase 1 Area endpoints). Phase 3 deliberately does **not**
create a second FO↔area source of truth. `GET /api/v1/field-officers` lists FO
employees; assignment stays in the Phase 1 flow.

---

## 16. API surface (`/api/v1`)

| Resource            | Endpoints                                                                                 |
|---------------------|-------------------------------------------------------------------------------------------|
| Skills              | `GET/POST /skills`, `PUT/DELETE /skills/{id}`                                              |
| Vendors             | `GET/POST /vendors`, `GET/PUT /vendors/{id}`, `POST /vendors/{id}/activate|suspend|deactivate|terminate` |
| Vendor staff        | `GET/POST /vendors/{id}/staff`, `GET/PUT /vendor-staff/{id}`                               |
| Technicians         | `GET/POST /technicians`, `GET/PUT /technicians/{id}`, lifecycle `/activate|suspend|deactivate|terminate`, `POST /availability` |
| Technician sub-res  | `/{id}/skills`, `/{id}/areas`, `/{id}/certifications`, `/{id}/availability-windows`, `/{id}/history` |
| Technician docs     | `GET/POST /technicians/{id}/documents`, `GET/DELETE /workforce-documents/{documentId}`    |
| Helpers             | `GET/POST /helpers`, `GET/PUT /helpers/{id}`, lifecycle, `/{id}/skills`                    |
| Employees           | `GET/POST /employees`, `PUT /employees/{id}`, `/activate|deactivate`                       |
| Field officers      | `GET /field-officers`                                                                     |

All mutations return the flat `ApiResponse` envelope; lists return `PageResponse`.

---

## 17. Idempotency & optimistic concurrency

- Create + lifecycle mutations accept an `Idempotency-Key` header; a repeated key
  for the same operation is rejected with `DUPLICATE_REQUEST` (409) via the existing
  `IdempotencyService`.
- Updates accept the entity `version`; a stale version yields `CONCURRENCY_CONFLICT`
  (409). This is enforced in-service (explicit check) in addition to JPA `@Version`.

---

## 18. Audit

Every mutation and lifecycle transition writes an audit record through
`AuditService.audit(actor, role, action, resourceKind, resourceId, before, after,
detail)`. For documents the audit records the **action and category only** — never
the raw document reference, object key, or content — so sensitive data does not leak
into the audit log.

---

## 19. Error codes used

`FORBIDDEN`, `SCOPE_DENIED`, `UNAUTHENTICATED`, `AUTH_DISABLED`, `NOT_FOUND`,
`RESOURCE_CONFLICT` (duplicate code/skill/area), `CONCURRENCY_CONFLICT`,
`DUPLICATE_REQUEST`, `VALIDATION_ERROR`, `INVALID_STATE_TRANSITION`,
`ROLE_NOT_ASSIGNABLE`. All are the existing Phase 1 registry codes — none were added.

---

## 20. Tests

Baseline before Phase 3: **84 tests**. After Phase 3: **113 tests, 0 failures,
0 errors** (`mvn test`, H2 + `@ActiveProfiles("test")`). No regression — the Phase 1
and Phase 2 suites (including `SeedIdempotencyTest`'s SUPER_ADMIN permission-count
assertion) remain green.

New tests (29):

| Test class                  | Kind        | Count | Covers                                                                 |
|-----------------------------|-------------|-------|------------------------------------------------------------------------|
| `WorkforceStateMachineTest` | Unit        | 5     | Valid/invalid transitions, terminal state, 409 on illegal, operational |
| `WorkforceHttpIT`           | Integration | 11    | HR CRUD, vendor-staff parent auth, inactive-area rule, invalid transition, duplicate skill/area, expired cert, stale version, idempotency, duplicate code |
| `WorkforceRbacIT`           | RBAC        | 8     | HR manages; onboarder denied technician + vendor; FO denied create; KYC≠Finance; Support no KYC; admin cannot grant SUPER_ADMIN; disabled user denied |
| `WorkforceIdorIT`           | IDOR/scope  | 5     | Cross-area denial, in-area allow (masked PII), unanchored-technician denial, HR unmasked PII, unknown-document 404 |

The eight explicit RBAC scenarios from the spec (§32) are all present in
`WorkforceRbacIT`.

---

## 21. Migration & environment validation

- **H2 (dev/test): PASSED.** All versioned migrations `V1`–`V8` + the repeatable
  `R__` seed apply, the seed is idempotent, and the full 113-test suite boots the
  Spring context with Flyway migrate **and** Hibernate `ddl-auto=validate` (the
  entities validate against the Flyway-built schema).
- **PostgreSQL: PENDING.** The Docker daemon is not running in this environment and
  no local PostgreSQL is reachable, so PostgreSQL validation was **not executed** and
  is **not claimed** as passed. The exact runbook and sign-off checklist are in
  [`docs/POSTGRES-VALIDATION.md`](./POSTGRES-VALIDATION.md); it must be completed in a
  DEV environment before staging.
- **`ManafyCommunitySvcRst`: untouched** — `git status --short` on that repository is
  empty; no files were modified there.

---

## Known limitations / deferred work

- No technician/helper authentication (managed records, not principals).
- No dispatch / assignment / scheduling / field execution.
- Vendor/helper/employee use permission-only authorization (no per-area scope yet);
  their `workforce_area` coverage is captured for Phase 4.
- Document bytes are not stored — only metadata + object-storage key.
- PostgreSQL validation pending (see above).

**Recommended next phase:** Phase 4 — Operations & Dispatch (service requests,
assignments to `ACTIVE` workforce via the `isOperational` readiness hook, scheduling,
field visits), building directly on the workforce anchors and area coverage this
phase established.
