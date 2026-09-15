# Phase 4B — Operations & Dispatch

Turns the Phase 4A Service Request into an operational workflow: assign a technician,
run the field lifecycle (accept → en-route → arrive → start → complete), handle
decline / cancel / reschedule / no-show / rework, and reassign while preserving
history — with the service request status kept as an atomic projection of assignment
activity (DD-36). Built entirely on the existing Phase 1 foundation and Phase 2/3/4A
domains; no infrastructure was duplicated.

**Deferred (NOT in this phase):** technician/helper/vendor login, mobile apps,
payments/payouts/invoices, automatic dispatch/optimization, AI matching,
notification providers (SMS/WhatsApp/push), advanced calendar scheduling, SLA
pause/resume, and all Finance workflows.

---

## 1. Assignment architecture

```
Service Request
   └── Assignment (history-preserving; one ACTIVE at a time)
          ├── Technician  (required)
          ├── Helper      (optional)
          └── Vendor      (optional; copied from the technician)
   └── Field Visit (per assignment)
```

Layering follows the mandated pattern end-to-end:
`Controller → AssignmentAppService → AuthorizationService → domain (state machine) →
Repository → AuditService`. Controllers are thin (HTTP only); all dispatch logic,
matching, transitions, scope resolution and projection live in services/domain.

New package `com.manafy.ops.dispatch`: `entity/` (Assignment, AssignmentStatusHistory,
FieldVisit), `repository/` (3), `domain/` (AssignmentStateMachine), `service/`
(AssignmentAppService, AssignmentLifecycleService, TechnicianEligibilityService,
OperationsQueueService), `controller/` (AssignmentController, OperationsQueueController),
`dto/` (DispatchDtos).

---

## 2. Lifecycle state machine

`AssignmentStateMachine` (explicit transitions; illegal → 409 `INVALID_STATE_TRANSITION`):

```
ASSIGNED → ACCEPTED → EN_ROUTE → ARRIVED → IN_PROGRESS → COMPLETED
ASSIGNED    → DECLINED | CANCELLED
ACCEPTED    → EN_ROUTE | CANCELLED | NO_SHOW
EN_ROUTE    → ARRIVED  | CANCELLED | NO_SHOW
ARRIVED     → IN_PROGRESS | NO_SHOW | CANCELLED
IN_PROGRESS → COMPLETED | CANCELLED
COMPLETED   → REWORK
Terminal: DECLINED, CANCELLED, NO_SHOW, REWORK
```

There is no generic status setter — each transition is a dedicated action endpoint.

---

## 3. Request ↔ assignment relationship (DD-36)

The service request status is a **projection** of assignment activity, updated in the
**same transaction** as the assignment mutation (they cannot drift). The Phase 4A
`ServiceRequestStateMachine` was extended:

```
NEW → ASSIGNED         (assignment created)
ASSIGNED → IN_PROGRESS (technician starts)
IN_PROGRESS → COMPLETED
COMPLETED → REWORK      (completed work re-opened)
REWORK → ASSIGNED       (rework assignment created)
ASSIGNED → NEW          (decline / cancel / no-show with no replacement → dispatchable)
IN_PROGRESS → ASSIGNED  (cancel mid-work → dispatchable)
NEW | ASSIGNED → CANCELLED (request cancelled — Phase 4A)
```

`service_request.active_assignment_id` points to the current assignment (null when
unassigned). `ServiceRequestAppService.projectStatus(...)` performs the validated
transition + audit; the dispatch service calls it inside the assignment transaction.

| Action        | Assignment                | Request projection            |
|---------------|---------------------------|-------------------------------|
| create        | → ASSIGNED (active)       | NEW/REWORK → ASSIGNED         |
| accept        | ASSIGNED → ACCEPTED       | (unchanged)                   |
| decline       | → DECLINED (inactive)     | → NEW                         |
| en-route/arrive | milestone stamp         | (unchanged)                   |
| start         | ARRIVED → IN_PROGRESS     | ASSIGNED → IN_PROGRESS        |
| complete      | → COMPLETED (inactive)    | IN_PROGRESS → COMPLETED       |
| no-show       | → NO_SHOW (inactive)      | → NEW                         |
| cancel        | → CANCELLED (inactive)    | → NEW (unless request cancelled) |
| reassign      | old → CANCELLED; new ASSIGNED | stays/into ASSIGNED       |
| rework        | completed → REWORK; new ASSIGNED | COMPLETED → REWORK → ASSIGNED |

---

## 4. Technician eligibility (deterministic MVP)

`TechnicianEligibilityService` — a rule pipeline, **not** ranking/AI/optimization:

1. Employment `status == ACTIVE`.
2. Covers the request's **area** (`workforce_area`).
3. Availability not `OFFLINE`/`ON_LEAVE` (`BUSY` still eligible for scheduling).
4. Holds the **required skill** for the request category.
5. Holds a **valid (non-expired, non-revoked) certification** where the category needs one.

Since no Service catalog exists yet, the required skill is derived deterministically
from the request category:

| Category    | Required skill | Requires certification |
|-------------|----------------|------------------------|
| PLUMBING    | PLUMBING       | no                     |
| ELECTRICAL  | ELECTRICAL     | ELECTRICAL             |
| HVAC        | HVAC           | HVAC                   |
| CLEANING    | CLEANING       | no                     |
| SECURITY    | SECURITY       | no                     |
| GENERAL/OTHER | (none)       | no                     |

`GET /service-requests/{id}/eligible-technicians` returns a safe operational DTO
(id, code, name, vendor, availability, area, skills, current active-job count,
maxConcurrentJobs) — **no KYC, finance, or PII**. Results are ordered by current
workload then code.

---

## 5. Dispatch workflow (manual MVP)

Operations users: view the dispatch queue → inspect eligible technicians → create an
assignment → optionally reassign / cancel / reschedule. **No automatic dispatch** —
assignment is an explicit operations decision. A request must be `NEW` or `REWORK`
(awaiting assignment) to receive one; a second active assignment is blocked
(state guard first, active-assignment guard as defense-in-depth).

---

## 6. Reassignment behavior (history-preserving)

Reassigning from technician A → B **never** rewrites the old row. In one transaction:
the old assignment is transitioned to `CANCELLED` and `active=false`; a new assignment
is inserted with `previousAssignmentId` linking back, `active=true`; the request's
active-assignment pointer is updated. Both rows survive; the request's assignment list
shows the full chain. Both sides are audited.

---

## 7. Scheduling (basic)

`scheduledStart`/`scheduledEnd` on the assignment; set on create/reassign and changed
via `/reschedule`. Invalid windows are rejected (`end < start` → `VALIDATION_ERROR`;
also a DB `CHECK`). Rescheduling a terminal/completed assignment → 409. A `field_visit`
record (per assignment) captures the visit schedule/arrival/departure/outcome without
duplicating assignment state. No calendar/optimization engine (deferred).

---

## 8. No-show

`POST /assignments/{id}/no-show` (reason captured) transitions the assignment to
`NO_SHOW` (inactive), returns the request to `NEW` (needs re-dispatch), records history
and audits. Allowed from `ACCEPTED`/`EN_ROUTE`/`ARRIVED`.

---

## 9. Rework

`POST /assignments/{id}/rework` on a `COMPLETED` assignment: the completed assignment
→ `REWORK`, the request `COMPLETED → REWORK`, then a **new** assignment is created
(request `REWORK → ASSIGNED`) linked via `previousAssignmentId`. The historical
completed assignment is preserved.

---

## 10. Field Officer scope

Field Officers are area-scoped via `area_field_officer` (the sole source of truth —
no second table). An assignment is scoped through its parent request's area/region
(`ResourceScopeResolver.assignment(...)`), so a FO sees/acts on operational work only
in their own area; another area's assignment yields `SCOPE_DENIED` (403) with no body.
Enforced through permission + area scope + resource scope — no hard-coded role checks.

---

## 11. RBAC

No new permissions were created — the `ASSIGNMENT_*` and `FIELD_VISIT_*` permissions
were already in the catalog (OPERATIONS domain); Phase 4B only maps them. Technician
login is deferred, so internal ops users perform the technician-side actions
(accept/execute) during MVP; the model stays extensible for a future technician-app
identity.

| Role                     | View | Create | Accept/Decline | Execute (en-route…complete/no-show) | Reassign | Cancel | Reschedule |
|--------------------------|:----:|:------:|:--------------:|:-----------------------------------:|:--------:|:------:|:----------:|
| SUPER_ADMIN              | ✅ (catch-all) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| MANAFY_ADMIN             | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| OPERATIONS_COORDINATOR   | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| AREA_OPERATIONS_MANAGER  | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ |
| FIELD_OFFICER            | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ |
| REPORTING                | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| FINANCE / HR / SUPPORT / ONBOARDER | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

`ASSIGNMENT_ACCEPT` maps to accept/decline; `ASSIGNMENT_EXECUTE` to the execution
milestones + no-show; `ASSIGNMENT_REASSIGN` also gates rework. The SUPER_ADMIN
catch-all is re-applied last in `R__` (keeps `SeedIdempotencyTest` green).

---

## 12. API inventory

Actions (`ApiResponse`), reads nested under the request, queues (`PageResponse`):

| Method & path                                             | Permission            |
|-----------------------------------------------------------|-----------------------|
| `POST /api/v1/service-requests/{requestId}/assignments`   | ASSIGNMENT_CREATE     |
| `GET  /api/v1/service-requests/{requestId}/assignments`   | ASSIGNMENT_VIEW       |
| `GET  /api/v1/service-requests/{requestId}/eligible-technicians` | ASSIGNMENT_VIEW |
| `GET  /api/v1/assignments/{id}`                           | ASSIGNMENT_VIEW       |
| `GET  /api/v1/assignments/{id}/history`                   | ASSIGNMENT_VIEW       |
| `POST /api/v1/assignments/{id}/accept`                    | ASSIGNMENT_ACCEPT     |
| `POST /api/v1/assignments/{id}/decline`                   | ASSIGNMENT_ACCEPT     |
| `POST /api/v1/assignments/{id}/reassign`                  | ASSIGNMENT_REASSIGN   |
| `POST /api/v1/assignments/{id}/cancel`                    | ASSIGNMENT_CANCEL     |
| `POST /api/v1/assignments/{id}/reschedule`                | ASSIGNMENT_RESCHEDULE |
| `POST /api/v1/assignments/{id}/en-route`                  | ASSIGNMENT_EXECUTE    |
| `POST /api/v1/assignments/{id}/arrive`                    | ASSIGNMENT_EXECUTE    |
| `POST /api/v1/assignments/{id}/start`                     | ASSIGNMENT_EXECUTE    |
| `POST /api/v1/assignments/{id}/complete`                  | ASSIGNMENT_EXECUTE    |
| `POST /api/v1/assignments/{id}/no-show`                   | ASSIGNMENT_EXECUTE    |
| `POST /api/v1/assignments/{id}/rework`                    | ASSIGNMENT_REASSIGN   |
| `GET  /api/v1/operations/service-requests/queue`          | ASSIGNMENT_VIEW       |
| `GET  /api/v1/operations/dispatch/queue`                  | ASSIGNMENT_VIEW       |

There is **no** `PATCH /assignments/{id}/status`. Operational queue filters: status,
priority, area, assignmentStatus, technician, vendor + pagination. Dispatch queue
surfaces `UNASSIGNED`, `AWAITING_ACCEPTANCE`, `REWORK`. All queues are scoped and
paginated (bounded).

---

## 13. Audit

Every dispatch action writes `AuditService.audit(actor, role, "ASSIGNMENT_<TO>",
"ASSIGNMENT", id, from, to, reason)` + an `activity(...)` event, plus the request
projection audit — via `AssignmentLifecycleService`. Field-visit creation is audited.
No KYC/financial data is logged.

---

## 14. Idempotency

Every mutating action accepts an optional `Idempotency-Key` header and calls the
existing `IdempotencyService.register(...)` after the permission gate; a duplicate key
for the same endpoint → `DUPLICATE_REQUEST` (409). No second mechanism was built.

---

## 15. Concurrency

`Assignment` inherits `@Version`. Every action accepts an optional `?version=`; a stale
value → `CONCURRENCY_CONFLICT` (409). Competing assign/reassign/complete operations
cannot silently overwrite one another. The "one active assignment per request"
invariant is enforced in-service within the transaction (H2 lacks partial unique
indexes, so no filtered index is used — documented in the migration).

---

## 16. Security / IDOR

Assignment scope is resolved server-side through the parent request's persisted
apartment/area/region, so an assignment id cannot expose or mutate an out-of-scope
request. Tested: FO cannot view/act on another area's assignment; area-scoped
coordinator cannot read another area's assignment by id (IDOR); disabled user blocked
(`AUTH_DISABLED`); inactive/nonexistent/ineligible technician rejected
(`VALIDATION_ERROR`); unauthorized role blocked (`FORBIDDEN`).

---

## 17. Tests

Baseline before Phase 4B: **139**. After: **176 tests, 0 failures, 0 errors**
(`mvn test`, H2 + `@ActiveProfiles("test")`). No regression; existing tests were
updated only where Phase 4B legitimately changed behavior (the service-request
projection graph), never disabled.

New tests (37):

| Test class                    | Kind        | Count | Covers                                                            |
|-------------------------------|-------------|-------|-------------------------------------------------------------------|
| `AssignmentStateMachineTest`  | Unit        | 7     | Happy path, alternates, illegal transitions, terminal states, 409, SR projection graph |
| `TechnicianEligibilityTest`   | Unit/Spring | 8     | Full match, inactive, wrong area, missing skill, on-leave, GENERAL no-skill, cert required, expired cert |
| `DispatchHttpIT`              | Integration | 15    | Eligible list, create+projection, ineligible reject, full lifecycle, order guard, decline→NEW, reassign history, no-show→NEW, rework, reschedule validation, second-active block, queues, idempotency, stale version |
| `DispatchSecurityIT`          | Security    | 7     | RBAC deny, FO area isolation (view/act), FO own-area allow, assignment IDOR, disabled user, inactive/nonexistent technician |

---

## 18. PostgreSQL validation status

- **H2 (dev/test): PASSED.** Migrations `V1`–`V10` + the repeatable `R__` seed apply,
  and the full 176-test suite boots the Spring context with Flyway migrate **and**
  Hibernate `ddl-auto=validate` — so the new entities validate against the Flyway-built
  schema (including the `service_request.status` widening `ALTER` and the new
  `assignment` / `assignment_status_history` / `field_visit` tables).
- **PostgreSQL: PENDING.** The Docker daemon is not running in this environment and no
  local PostgreSQL is reachable, so PostgreSQL validation was **not executed** and is
  **not claimed**. Runbook: [`docs/POSTGRES-VALIDATION.md`](./POSTGRES-VALIDATION.md)
  (now covering `V1`–`V10`). One dialect note verified by design: no partial/filtered
  indexes were used (H2-incompatible); the `ALTER TABLE ... DROP/ADD CONSTRAINT` for
  the status widening is supported by both H2 and PostgreSQL.
- **`ManafyCommunitySvcRst`: untouched** — `git status --short` there is empty.

---

## 19. Deferred functionality

Technician/helper/vendor login, mobile/resident apps, payments/invoices/payouts/
earnings, automatic dispatch, AI matching, notification providers (SMS/WhatsApp/push)
and the transactional outbox (none exists yet), advanced calendar scheduling, full SLA
pause/resume, and Finance workflows are all deferred.

**Recommended next phase:** Phase 4C — Notifications & Outbox (introduce the
transactional outbox and emit events for assignment created/reassigned/cancelled/
scheduled/completed/no-show/rework), followed by the Technician App identity that will
consume `ASSIGNMENT_ACCEPT` / `ASSIGNMENT_EXECUTE` directly without any model redesign.
