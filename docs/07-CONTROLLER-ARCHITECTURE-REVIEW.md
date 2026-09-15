# Artifact #7 — Controller Architecture Review & Mandatory Phase-2 Pattern

Review of the Phase-1 foundation controllers, a classification of the logic they
contain, and the **mandatory** layering pattern every module must follow from
Phase 2 onward. This is a governance document: Phase 2 code that violates the
pattern below should be rejected in review.

## 1. Controllers reviewed (Phase 1)

`AuthController`, `UserController`, `RoleController`, `PermissionController`,
`AuditLogController`, `RegionController`, `AreaController`,
`ConfigurationController`, `FeatureFlagController`.

## 2. Logic classification

Each block of logic is classified:
- **A** HTTP concern — acceptable in the controller.
- **B** Authorization concern — must go through the central authorization engine.
- **C** Business rule / workflow — belongs in an application/domain service.
- **D** Persistence — belongs in a repository/service.

| Controller | Logic present | Class | Verdict |
|---|---|---|---|
| AuthController | map principal → MeResponse | A | OK |
| PermissionController | list + `requirePermission` | A + B(centralized) | OK |
| AuditLogController | paginate + `requirePermission` | A + B | OK |
| RegionController | `requirePermission`, uniqueness check, version check, entity mutation, `save`, audit | A, B(ok), **C (uniqueness, version)**, **D (save)**, A(audit) | ACCEPTABLE for CRUD-only foundation; **must move C/D to a service in Phase 2 pattern** |
| ConfigurationController | permission + **sensitive-key rule**, version check, mutate, audit | B(ok), **C (sensitive-key business rule)**, D | ACCEPTABLE now; sensitive-key rule is a business rule that should live in a service |
| FeatureFlagController | permission, upsert, audit | B, D | ACCEPTABLE now |
| UserController | permission, **role-assignability**, **scope-ref validation**, entity mutation, save, audit, idempotency | B(ok via authz), **C (assignability delegated to AuthorizationService — good)**, **C (scope validation inline)**, D | **Highest concentration of C/D in a controller.** Works and is fully tested, but is the clearest example of logic that belongs in an application service. |
| AreaController | permission+scope (good), FO current-primary rollover, duplicate-current handling, mutation, save, audit | B(ok), **C (FO assignment workflow inline)**, D | **The FO-assignment rollover is a real domain workflow embedded in the controller.** Acceptable for the small foundation surface; **must be a service in Phase 2.** |

### Summary
- **Authorization is already centralized** (all controllers call
  `AuthorizationService`/`PermissionService`; no `if (role == ...)` anywhere). This
  is the most important property and it is correct.
- **Business rules and persistence are currently inline in controllers.** For the
  small, mostly-CRUD foundation surface this is acceptable and fully tested. It is
  **not acceptable for Phase 2 domain workflows** (onboarding, dispatch, etc.),
  which are multi-step and must not live in controllers.

## 3. MANDATORY pattern from Phase 2 onward

```
HTTP Request
  → Controller            (HTTP concern ONLY: bind DTO, call service, map response)
      → Application Service (orchestration, transaction boundary @Transactional)
          → AuthorizationService (permission + scope + relationship predicate)
          → Domain/business logic (state machines, invariants, validation)
          → Repository        (persistence)
          → AuditService      (sensitive-action audit, within the same transaction)
```

### Rules
1. **Controllers contain no business rules.** A controller may: read path/query/
   body into a DTO, invoke exactly one application-service method, and map the
   result to `ApiResponse`/`PageResponse`. Nothing else.
2. **Every mutating use case is a `@Transactional` application-service method.**
   The transaction boundary is the service, not the controller (so state change +
   history + audit + outbox commit atomically — design DD-11/§68).
3. **Authorization is invoked from the service** (or a controller-level guard for
   pure permission checks), always via `AuthorizationService` — never re-implemented.
4. **Persistence only through repositories**, called from services, never from
   controllers.
5. **State transitions go through dedicated service methods** mapped to action
   endpoints (design DD-07), never `PATCH status`.
6. **No cross-module repository reach-through**: a module's service uses its own
   repositories + shared foundation services; it does not query another module's
   tables directly.
7. **DTOs at the boundary**: entities are never serialized directly to the client;
   services return DTOs or the controller maps them.

### Phase-2 skeleton example (Apartments — illustrative, do NOT implement now)
```
ApartmentController        // HTTP only
  ApartmentService         // @Transactional use cases: create, onboard, verify, activate
    AuthorizationService   // APARTMENT_* permission + AREA/APARTMENT scope
    ApartmentStateMachine  // PROSPECT→…→ACTIVE transition validation
    ApartmentRepository    // persistence
    AuditService           // sensitive-action audit
```

## 4. Retrofit guidance for Phase 1 (optional, non-blocking)
The Phase-1 controllers are correct and tested; refactoring them into services is
**optional cleanup**, not a blocker. If touched, extract:
- `UserController` → `UserAdminService` (create/update/disable/assignRole/assignScope).
- `AreaController` → `AreaService` (+ the FO-assignment rollover workflow).
- `RegionController`/`ConfigurationController`/`FeatureFlagController` → thin services.
Do this opportunistically; do not gate Phase 2 on it. New Phase-2 code MUST follow
§3 from the start.
