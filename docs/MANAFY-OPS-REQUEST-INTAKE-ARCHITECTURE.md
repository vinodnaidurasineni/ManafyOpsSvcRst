# Manafy Ops Request Intake Architecture

**Status:** READ-ONLY investigation and design proposal. **No Java, entity, repository,
controller, migration, API, or business logic was modified in either project.** This
document only proposes a target architecture; nothing here is implemented.

**Scope:** How operational requests should *enter* `ManafyOpsSvcRst`, and how to keep
three genuinely different workflows — **Tech Team issues**, **manual Field Officer
assignments**, and **technician service fulfillment** — as separate concepts where
their business semantics differ.

**Grounding:** Every claim about existing behavior below is taken from the actual
source of both services (`ManafyCommunitySvcRst` package `com.manafy.community.gate`
and `ManafyOpsSvcRst` package `com.manafy.ops`, Flyway migrations `V1`–`V11` +
`R__seed_ops_authorization_catalog.sql`). This document **builds on** the prior
`MANAFY-FULFILLMENT-BOUNDARY-AND-INTEGRATION.md` and
`MANAFY-SERVICE-BOUNDARY-AND-INTEGRATION.md` and does not contradict their decisions;
it extends them to cover the request *type* distinction those docs did not address.

**Guiding principle:** *Do not force different business workflows into the existing
`service_request` + technician dispatch model merely because Ops already has those
tables.*

---

## Table of contents

1. Executive summary
2. Business rules
3. Existing Community request model
4. Existing Ops model
5. Tech Issue workflow
6. Manual Field Officer workflow
7. Technician Service Request workflow
8. State machines
9. Request classification
10. Ownership matrix
11. Source / reference strategy
12. Idempotency
13. API proposal
14. Database proposal
15. Queue design
16. Vendor / helper distinctions
17. WorkOrder coexistence
18. ServiceBooking coexistence
19. Failure scenarios
20. Migration / coexistence strategy
21. Risks
22. Open decisions
23. Recommended implementation sequence

---

## 1. Executive summary

Ops today has exactly **one** operational intake model: `service_request` →
`assignment` → `field_visit`, dispatched to a **real technician** through
`TechnicianEligibilityService` (skill / area / availability / certification). The
request `status` is a *projection* of assignment activity (`NEW → ASSIGNED →
IN_PROGRESS → COMPLETED → REWORK → CANCELLED`). That model is correct **only** for
work that a technician physically performs.

The business now needs three distinct operational paths, and only one of them is the
existing technician-dispatch path:

| Path | Example | Correct handler | Uses technician dispatch? |
|---|---|---|---|
| **Tech Issue** | app crash, login failure, push not arriving, data bug | Tech Team (developer / app / infra support) | **No** |
| **Manual Field Officer request** | maid needed, maid replacement, manually-coordinated resident service | Field Officer decides the assignee manually | **No auto-dispatch** |
| **Technician Service Request** | plumbing, electrical, HVAC, cleaning by Manafy workforce | existing dispatch engine | **Yes** |

**Core recommendation:** keep `service_request` **technician-focused and unchanged**,
and introduce **two new, small, explicit Ops domain entities** — `tech_issue` and
`manual_assignment_request` — each with its own lifecycle. Do **not** overload
`service_request` with a `TECHNICAL` / `MANUAL` type discriminator (that produces an
over-generic entity and a confused state machine, and drags the technician-dispatch
invariants onto records that must never touch a technician). Do **not** build a generic
ticketing platform, workflow engine, or message broker.

**Community stays the source of truth** for resident/society requests and continues to
handle its normal workflows entirely in-house. A Community request becomes an Ops
record **only** when it is explicitly classified as operational, and it enters the
**correct** Ops workflow — never automatically the technician queue. Classification
happens **in Community** (which owns the trigger and the resident context), is carried
as an explicit `opsIntakeType` on a service-to-service call, and is **re-validated in
Ops**. Every cross-boundary create is idempotent (deterministic key + unique source
reference) so one Community source produces at most one Ops record.

Critically: **there is no Community→Ops integration in code today** (no
`RestTemplate`/`WebClient`/`Feign`/`8083`/`ops` references anywhere in the Community
service). Everything in this document is net-new integration to be built later as
separate, explicitly-scoped tasks.

---

## 2. Business rules

These are the non-negotiable rules the architecture must enforce.

- **BR-1 — No automatic conversion.** A Community request (booking, complaint, work
  order, helper request, app issue report) must **never** automatically create an Ops
  `service_request`. Community handles its own workflows unless a request is explicitly
  classified as an Ops operational request.
- **BR-2 — Correct workflow, not the default one.** When a request does enter Ops, it
  enters the workflow that matches its semantics. Only technician-performed physical
  work enters the `service_request`/dispatch path.
- **BR-3 — Tech issues are not field work.** An app/technical issue must not create an
  `assignment`, `field_visit`, or technician dispatch. It is owned by the Tech Team.
- **BR-4 — Manual requests are manually assigned.** Maid/manual operational requests
  must not run technician eligibility / skill matching / automatic workforce-area
  dispatch. A Field Officer decides the assignee.
- **BR-5 — Four concepts are distinct.** `Tech Issue ≠ Technician Service Request ≠
  Manual Field Officer Request ≠ Community Request`. They may share generic attributes
  (status, priority, notes, apartment, created_by) but have different semantics and
  lifecycles.
- **BR-6 — Community owns identity and the resident-facing record.** Ops holds only
  references (source ids, apartment/customer ids) and a coarse status projection back;
  no cross-database foreign keys, no PII/finance duplication.
- **BR-7 — Idempotent intake.** One Community source → at most one Ops record, even
  under retry/timeout/double-send.
- **BR-8 — Community helper ≠ Ops helper.** They are different concepts and must not be
  merged, FK-linked, or renamed to look alike (see §16).
- **BR-9 — Do not over-engineer.** Prefer the simplest explicit domain model. No
  generic workflow engine, configurable state machine, Kafka/SQS, event bus, or
  microservice split unless real requirements prove it necessary.

---

## 3. Existing Community request model

All Community request entities live in `com.manafy.community.gate`, extend `BaseEntity`
(UUID id, timestamps, soft-delete `deleted`), and store status/category/priority as
plain `String` columns whose allowed values are documented only in code comments (there
are **no** Java `enum` types). **No Community code calls any Ops service today.**

| Entity (`table`) | Origin (endpoint) | Lifecycle (from code) | Requester / scope | Operational? |
|---|---|---|---|---|
| **ServiceBooking** (`service_booking`) | `POST /api/v1/services/bookings` → `ServiceCatalogService.createBooking` | `REQUESTED` only — **no transition code exists** (capture-only) | `customerId`, optional `apartmentId`; `serviceCode`, `timeSlot`, `frequency`, `quotedAmount` | A **subset** of catalog codes (pest control, deep cleaning, local experts, appliance repair, painting) |
| **SocietyComplaint** (`society_complaint`) | `POST /complaints`; transition `POST /complaints/{id}/status`; `POST /complaints/{id}/rate` | `OPEN → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED` (+ `REOPENED`); resolved **in place** | `residentId`, `apartmentId`, `flatId`; `category`, `priority`, `slaHours`(48)/`slaBreached`, rating | **Only operational categories** (PLUMBING/ELECTRICAL/LIFT/CLEANING/WATER); informational (NOISE/PARKING/INTERNET) stay in Community |
| **WorkOrder** (`work_order`) | `POST /api/v1/society/ops/work-orders` (+ `/assign` `/start` `/complete` `/close`) → `VendorAssetService` | `OPEN → ASSIGNED → IN_PROGRESS → COMPLETED → CLOSED` (`SCHEDULED/INVOICED/CANCELLED` declared but never set) | Society admin; `apartmentId`, optional `complaintId`/`assetId`/`vendorId`; **technician is free text** (`technicianName`/`technicianMobile`); inline cost/invoice/photos | Society maintenance/AMC; may need real dispatch (see §17) |
| **HelperRequest** (`helper_request`) | `POST /api/v1/helpers/requests` (+ `/assign` `/status`) → `HelperController` | `OPEN → ASSIGNED → FULFILLED` (+ `CANCELLED`) | `customerId`, optional `apartmentId`/`flatId`; substitute for an absent domestic helper; `assignedHelperName`/`Mobile` | **Maid/manual** — the clearest Manual FO candidate |
| **Helper** (`helper`) | `POST /api/v1/helpers` | resident domestic-staff tracking | `customerId`; `helperType` MAID/COOK/DRIVER/NANNY/… | Master data, not a request |
| **Subscription** (`subscription`) | `SubscriptionController` | `ACTIVE → PAUSED → RESUMED/CANCELLED/EXPIRED`; `assignedMaidId` | recurring maid/cook plan | Recurring manual fulfillment (future manual candidate) |
| **Vendor** / **VendorContract** | `VendorAssetController` | ACTIVE/SUSPENDED/BLACKLISTED | **per-apartment** AMC vendor (GST/PAN/bank/rating) | Master data (see §16) |
| **AmenityBooking** | `SocietyController /amenities/book` | CONFIRMED/CANCELLED/COMPLETED/NO_SHOW | facility reservation | **No** — physical facility reservation, never leaves Community |
| **EmergencyAlert** (SOS) | `SocietyController /emergency` | ACTIVE/ACKNOWLEDGED/RESOLVED/FALSE_ALARM | safety alert, resolved in-app | **No** |
| **MaintenanceInvoice / Marketplace** | Society/Community controllers | finance / classifieds | — | **No** |

**Takeaways for intake:**
- The two request types that most clearly need a *new* Ops workflow are **HelperRequest**
  (→ Manual Field Officer request) and **ServiceBooking operational subset / operational
  SocietyComplaint / WorkOrder-needing-dispatch** (→ Technician Service Request).
- **App/technical issues have no Community entity today.** There is no "report a bug"
  record in Community. A Tech Issue intake is therefore genuinely new on both sides
  (see §5, §9).

---

## 4. Existing Ops model

Ops (`com.manafy.ops`, Flyway `V1`–`V11`) already has a complete, well-guarded
technician-dispatch domain and reusable cross-cutting infrastructure.

**Technician-dispatch-specific (do not reuse for other workflows):**

- **`service_request`** (`V9`, widened in `V10`): `reference_no` (`SR-…`),
  `apartment_id`/`area_id`/`region_id` (NOT NULL scope anchors), `requester_user_id`
  (an `ops_user`, **not** a resident), `category` (`PLUMBING|ELECTRICAL|HVAC|CLEANING|
  SECURITY|GENERAL|OTHER`), `priority`, `description`, `status`
  (`NEW|ASSIGNED|IN_PROGRESS|COMPLETED|REWORK|CANCELLED`), `active_assignment_id`.
  Status is a **projection of assignment activity** (design DD-36). **No SLA columns.
  No source-reference columns.** Created only via internal `POST /api/v1/service-requests`
  (ops actor with `SERVICE_REQUEST_CREATE` + apartment scope). *These states only exist
  because of assignment activity — they are meaningless for non-dispatch work.*
- **`assignment`** (`V10`): explicit `technician_id` (**NOT NULL FK**) + optional
  `helper_id`/`vendor_id` (there is **no** `party_type` discriminator); rich lifecycle
  `ASSIGNED→ACCEPTED→EN_ROUTE→ARRIVED→IN_PROGRESS→COMPLETED` (+ `DECLINED/CANCELLED/
  NO_SHOW/REWORK`); `previous_assignment_id` for history-preserving reassignment;
  `assignment_status_history` (insert-only). Created **manually** via
  `AssignmentAppService.create` (SR must be `NEW`/`REWORK`; one-active-assignment
  invariant; ops actor **explicitly picks** the technician; eligibility must pass).
- **`field_visit`** (`V10`): per-assignment visit (schedule/arrival/departure/outcome),
  bound to `technician_id`.
- **`TechnicianEligibilityService`**: deterministic rules — employment `ACTIVE`, area
  coverage (`workforce_area`), availability not `ON_LEAVE`/`OFFLINE`, skill via a
  `CATEGORY_SKILL` map, certification for `ELECTRICAL`/`HVAC`. **No** distance/SLA/auto
  dispatch. `OperationsQueueService` is a read-only dispatch/operational queue.
- Fields inappropriate for app issues / maid / manual requests: `technician_id`,
  `helper_id`, `vendor_id`, `en_route_at`/`arrived_at`, `scheduled_start/end` on a
  visit, `field_visit`, eligibility, `REWORK`, and the `ASSIGNED/IN_PROGRESS`
  projection semantics that assume a technician is en route.

**Generic / reusable across any new workflow:**

- **`BaseEntity`** (UUID id, `@Version` optimistic lock, timestamps, soft-delete).
- **`ApiResponse` / `PageResponse`** envelopes.
- **`IdempotencyKey` + `IdempotencyService`** (`Idempotency-Key` header → claim or
  `409 DUPLICATE_REQUEST`).
- **`AuditService` (`audit_log`) + `ActivityLog`** — append-only.
- **Authorization pipeline**: permission gate + `ScopeService.covers()` scope gate +
  `RelationshipPredicate` (`SELF, ASSIGNED, RESIDENT, OWNER, AREA_RESPONSIBLE,
  RESOURCE_MANAGER`; only `SELF` + `AREA_RESPONSIBLE` implemented in foundation).
- **`area_field_officer`** — authoritative FO↔area mapping (`designation`
  PRIMARY/SECONDARY, `effective_from`/`effective_to`); `ScopeService.grantedAreaIds()`
  unions explicit AREA scopes with current FO-owned areas. This is exactly the scope
  primitive a manual FO queue needs.
- **Permission catalog** already seeds **unbuilt stubs** we can adopt rather than invent:
  `INCIDENT_VIEW/CREATE/UPDATE/ESCALATE/CLOSE`, `TICKET_VIEW/CREATE/UPDATE`,
  `COMPLAINT_VIEW/CREATE/UPDATE`, `ESCALATION_VIEW/CREATE/RESOLVE`, `INSPECTION_MANAGE`.
  These have **no entities/tables** behind them yet — `tech_issue` and
  `manual_assignment_request` do not exist anywhere in Ops.
- **`ops_user.community_customer_id`** (`V11`, nullable, **not** an FK) + Cognito `sub`
  are the cross-service identity references.

---

## 5. Tech Issue workflow

**Purpose:** capture an application / technical problem reported by (or on behalf of) a
resident or an internal user — app crash, login/OTP failure, push not arriving, wrong
data, performance — and route it to the **Tech Team** (developer / app-support /
infra). This needs technical investigation, status tracking, and resolution notes.
It **never** involves a technician, field visit, vendor, or dispatch (BR-3).

**Proposed entity:** `tech_issue` (new, small, Ops-owned). Justification: no existing
Ops entity fits — `service_request` is technician-dispatch (§4), and its states
(`ASSIGNED/EN_ROUTE/…`) are semantically wrong for a bug report. Reuse the already-seeded
`INCIDENT_*`/`TICKET_*` permission family instead of inventing new permissions.

**Lifecycle (proposed, verified against the task's suggested states):**

```
OPEN → TRIAGED → IN_PROGRESS → RESOLVED → CLOSED
                                   │
                                   └── REOPENED → IN_PROGRESS   (bounded reopen)
```

- `OPEN` — created (from Community app-issue report or internally).
- `TRIAGED` — Tech Team confirms/prioritizes/categorizes (severity, area of the app).
- `IN_PROGRESS` — under active investigation/fix.
- `RESOLVED` — fix applied / explanation recorded (`resolution_notes`).
- `CLOSED` — reporter/ops confirms; terminal.
- `REOPENED` — allowed from `RESOLVED` (and optionally `CLOSED` within a window) →
  returns to `IN_PROGRESS`. This replaces the technician-only `REWORK` concept.

Are the task's suggested states appropriate? **Yes, with `REOPENED` added** — a tech
issue is commonly reopened when a "fix" doesn't hold, and the `service_request` `REWORK`
state cannot be reused because it implies a new technician assignment.

**Ownership / fields (proposed):** `reference_no` (`TI-…`), `title`, `description`,
`severity` (`LOW|MEDIUM|HIGH|CRITICAL`), `priority`, `category`
(`APP|BACKEND|INFRA|DATA|INTEGRATION|OTHER`), `status`, `owner_team`
(`APP|BACKEND|INFRA|SUPPORT`), `assigned_ops_user_id` (an `ops_user`, optional),
`resolution_notes`, `reopened_count`, `source_system`/`source_type`/`source_id`
(§11), `apartment_id` (**nullable** — many tech issues are app-wide, not
apartment-scoped), plus `BaseEntity` audit/version.

**Who / authorization:** created by Community intake (service credential) or an internal
support user with `TICKET_CREATE`/`INCIDENT_CREATE`; visible to the Tech Team via
`TICKET_VIEW`. Because a tech issue is frequently **not** apartment-scoped, its scope
model is primarily `GLOBAL` (Tech Team) rather than area/apartment — do **not** run it
through `AREA_RESPONSIBLE`/FO scope. **No `technician`, `assignment`, `field_visit`,
`vendor`, or eligibility** (BR-3). SLA is optional/future (see §22).

---

## 6. Manual Field Officer workflow

**Purpose:** handle maid/domestic-help and other manually-coordinated resident services
where a **Field Officer decides** who/what is assigned — maid requirement, maid
replacement, maid availability, and other requests that need human FO intervention.
These must **not** run technician eligibility / skill matching / automatic
workforce-area dispatch (BR-4).

**Proposed entity:** `manual_assignment_request` (new, Ops-owned). Justification: the
`assignment` table hard-requires a `technician_id` (NOT NULL FK) and drives the
eligibility engine, so it cannot represent "FO assigns a maid/vendor/person." Reuse the
existing `area_field_officer` scope primitive and the `ASSIGNMENT_*` permission family
(`ASSIGNMENT_CREATE`/`REASSIGN`/`CANCEL`) for authorization, but **not**
`TechnicianEligibilityService` and **not** the `assignment` state machine.

**Lifecycle (proposed):**

```
OPEN → QUEUED → ACKED_BY_FO → ASSIGNED → IN_PROGRESS → COMPLETED
  │        │         │            │            │
  └────────┴─────────┴────────────┴────────────┴──────────► CANCELLED  (until COMPLETED)
```

- `OPEN` — created from a Community manual request (e.g. `HelperRequest`).
- `QUEUED` — visible on the Field Officer queue for the request's area (via
  `area_field_officer`).
- `ACKED_BY_FO` — an FO with area scope picks it up (optional but useful for SLA/idle
  detection).
- `ASSIGNED` — the FO **manually records** the chosen assignee. The assignee is free-form
  by design: a name/mobile, a Community vendor reference, a Community domestic-helper
  reference, or an Ops workforce member — captured as `assignee_type` +
  `assignee_ref`/`assignee_name`/`assignee_phone`. **No eligibility check runs.**
- `IN_PROGRESS` — work underway.
- `COMPLETED` — done (`completion_notes`); terminal.
- `CANCELLED` — allowed from any non-terminal state; terminal.

Only `COMPLETED` and `CANCELLED` are terminal. The task's suggested
`OPEN → ASSIGNED_TO_FIELD_OFFICER → MANUAL_ASSIGNMENT → IN_PROGRESS → COMPLETED` maps
onto the above (`ACKED_BY_FO` = "assigned to FO"; `ASSIGNED` = "manual assignment
recorded").

**Ownership / fields (proposed):** `reference_no` (`MR-…`), `apartment_id`/`area_id`/
`region_id` (scope anchors, so FO area scope works via `covers()`), `request_type`
(`MAID_REQUIRED|MAID_REPLACEMENT|MAID_AVAILABILITY|OTHER_MANUAL`), `title`,
`description`, `priority`, `status`, `field_officer_id` (`ops_user`, nullable until
acked), `assignee_type` (`EXTERNAL_PERSON|COMMUNITY_VENDOR_REF|COMMUNITY_HELPER_REF|
OPS_WORKFORCE`), `assignee_ref`/`assignee_name`/`assignee_phone`, `completion_notes`,
plus source-reference (§11) and `BaseEntity`.

**Who can see / assign / reassign:** an FO whose **current** `area_field_officer` row
covers the request's `area_id` (resolved by `ScopeService.covers()` /
`grantedAreaIds()`); Area Ops Managers via region→area inheritance. Assignment/
reassignment/cancellation use `ASSIGNMENT_CREATE`/`ASSIGNMENT_REASSIGN`/
`ASSIGNMENT_CANCEL` gated by the `AREA_RESPONSIBLE` predicate. **Technician eligibility
is invoked only if the FO explicitly chooses an Ops technician** as the assignee — never
by default. A `manual_assignment_status_history` (insert-only) mirrors the existing
history pattern for audit. Idempotency + optimistic locking as everywhere else.

---

## 7. Technician Service Request workflow

**Unchanged.** This is the existing Ops `service_request → assignment → field_visit`
path (§4). It is the correct home for physical work Manafy's own workforce performs:
plumbing, electrical, HVAC, cleaning, security, and other technician-fulfilled services.

- Intake for this path only: the existing `POST /api/v1/service-requests` (internal) and,
  in future, the Community→Ops integration for the **operational subset** of
  `service_booking`, **operational** `society_complaint`, and `work_order` needing real
  dispatch (per `MANAFY-FULFILLMENT-BOUNDARY-AND-INTEGRATION.md`).
- Assignment stays manual-pick + eligibility-checked; status stays a projection of
  assignment activity; reassignment/rework/no-show semantics are unchanged.

**This document proposes no change to `service_request`, `assignment`, `field_visit`,
eligibility, or dispatch.** The only additive change these entities might see is the
shared **source-reference columns** (§11), which are also proposed for the two new
entities and are additive/nullable.

---

## 8. State machines

**Tech Issue (`tech_issue`)**
```
OPEN ──► TRIAGED ──► IN_PROGRESS ──► RESOLVED ──► CLOSED(terminal)
                          ▲              │
                          └── REOPENED ◄─┘   (RESOLVED→REOPENED→IN_PROGRESS)
CANCELLED  ← from OPEN/TRIAGED (never worked)   (terminal)
```

**Manual Field Officer request (`manual_assignment_request`)**
```
OPEN ─► QUEUED ─► ACKED_BY_FO ─► ASSIGNED ─► IN_PROGRESS ─► COMPLETED(terminal)
  └────────┴───────────┴────────────┴────────────┴────► CANCELLED(terminal)
(reassignment: ASSIGNED/IN_PROGRESS → ASSIGNED with a new assignee; history preserved)
```

**Technician Service Request (`service_request`) — EXISTING, unchanged**
```
NEW ─► ASSIGNED ─► IN_PROGRESS ─► COMPLETED ─► REWORK ─► ASSIGNED …
 │        │                                        
 └────────┴────► CANCELLED (terminal)
(status is a projection of the active assignment lifecycle
 ASSIGNED→ACCEPTED→EN_ROUTE→ARRIVED→IN_PROGRESS→COMPLETED (+DECLINED/CANCELLED/NO_SHOW/REWORK))
```

**Why three machines, not one:** the terminal/transition semantics genuinely differ.
`service_request` transitions are *driven by assignment events* (a technician accepting,
arriving, completing). `tech_issue` transitions are *driven by a Tech Team investigating*
and can *reopen*. `manual_assignment_request` transitions are *driven by an FO's manual
decision* and never require acceptance/en-route/arrival. Collapsing them forces
impossible transitions (e.g. a bug report going `EN_ROUTE`) and dead states.

---

## 9. Request classification

**Where classification happens: in Community, re-validated in Ops.**

Community owns the trigger, the resident context, and the request category, so it is the
only place with enough information to decide the path at creation/escalation time. Ops
cannot classify without duplicating Community data (which the boundary rules forbid).
Therefore Community sets an explicit **`opsIntakeType`** when (and only when) it decides
a request is operational, and Ops **re-validates** it (permission + scope + payload
shape) rather than trusting it blindly.

```
Community request
        │  classification (Community-side, explicit human/rule decision)
        ├── COMMUNITY_ONLY ─────────────► stays entirely in Community (default; BR-1)
        └── operational  ── opsIntakeType:
                 ├── TECH_ISSUE          ─► Ops  POST /api/v1/tech-issues
                 ├── MANUAL_ASSIGNMENT   ─► Ops  POST /api/v1/manual-requests
                 └── TECHNICIAN_SERVICE  ─► Ops  POST /api/v1/service-requests
                                             (Ops re-validates permission + scope + type)
```

**Classification guidance (proposed; requires owner confirmation — §22):**
- App/technical problem report → `TECH_ISSUE`.
- `HelperRequest` (maid substitute/availability), maid replacement, and manually
  coordinated resident services → `MANUAL_ASSIGNMENT`.
- Operational `service_booking` subset (pest control, deep cleaning, local experts,
  appliance repair, painting), **operational-category** `society_complaint`
  (PLUMBING/ELECTRICAL/LIFT/CLEANING/WATER), and `work_order` needing real dispatch →
  `TECHNICIAN_SERVICE`.
- Everything else (amenity bookings, SOS, informational complaints, water cans/laundry/
  fitness bookings, marketplace, finance) → `COMMUNITY_ONLY`.

The default is `COMMUNITY_ONLY`; a request is escalated only by an explicit decision, so
BR-1 holds by construction.

---

## 10. Ownership matrix

Extends §19 (Required decision matrix). "Verified" = the request category exists in code
today; "Proposed" = a new category this design introduces.

| Request type | Origin system | Ops entity | Assignment model | Field Officer? | Technician? | Category status |
|---|---|---|---|---|---|---|
| Normal Community request | Community | None | Community-internal | No | No | Verified |
| App / technical issue | Ops | `tech_issue` (new) | Tech Team (ops user), manual | No | No | **Proposed** (no Community entity exists today) |
| Maid request | Ops | `manual_assignment_request` (new) | Manual (FO picks) | **Yes** | No | **Proposed** (maps from Community `HelperRequest`) |
| Other manual operational request | Ops | `manual_assignment_request` (new) | Manual (FO picks) | **Yes** | Usually no | **Proposed** |
| Technician-required service | Ops | `service_request` (existing) | Dispatch + eligibility | Depends (FO may act as dispatcher via scope) | **Yes** | Verified |
| Community WorkOrder | Community initially | None / reference on dispatch | Existing Community flow (free-text tech) → optional bridge to Ops `service_request` | Existing | Existing | Verified (bridge Proposed) |

Domain ownership (source of truth) is unchanged from the fulfillment-boundary doc:
Community owns resident/society records + identity + finance; Ops owns operational
fulfillment records (`service_request`/`assignment`/`field_visit`/workforce) and now
also `tech_issue` and `manual_assignment_request`.

---

## 11. Source / reference strategy

Ops records that originate in Community must carry an **external source reference**, not
duplicated Community data, and never a cross-database FK (boundary rule). A bare
`community_booking_id` is **not** sufficient because there are several distinct source
types (complaint, booking, work order, helper request, app issue) feeding several Ops
entities.

**Proposed shared reference (applies to `tech_issue`, `manual_assignment_request`, and
optionally `service_request`):**

```
source_system   VARCHAR   -- e.g. 'COMMUNITY' | 'OPS_INTERNAL'
source_type     VARCHAR   -- 'SERVICE_BOOKING' | 'WORK_ORDER' | 'COMPLAINT'
                          -- | 'HELPER_REQUEST' | 'APP_ISSUE' | null (internal)
source_id       VARCHAR   -- the Community record id (opaque string; NOT an FK)
```

Plus these external reference fields already recommended by the boundary doc, carried on
the payload (not FKs): `community_customer_id`, `community_apartment_id`,
`community_flat_id`.

- **Complaint** → `source_type = COMPLAINT`, `source_id = society_complaint.id`.
- **Booking** → `source_type = SERVICE_BOOKING`, `source_id = service_booking.id`.
- **Work order** → `source_type = WORK_ORDER`, `source_id = work_order.id`.
- **App issue** → `source_type = APP_ISSUE`, `source_id = <community app-issue id>` (a
  Community app-issue capture record does not exist yet — see §22).
- **Manual request** → `source_type = HELPER_REQUEST` (or other), `source_id =
  helper_request.id`.

**Uniqueness:** a unique constraint on `(source_system, source_type, source_id)` **per
Ops entity table** guarantees one Community source → one Ops record of that type. This
is the durable backstop behind the idempotency key (§12).

Internal Ops-created records leave the source columns null (no external origin).

---

## 12. Idempotency

Reuse the existing `IdempotencyService` (`Idempotency-Key` header → claim or
`409 DUPLICATE_REQUEST`), exactly as `service_request`/`assignment` already do.

- **Deterministic key:** the caller (Community) derives the key from the source, e.g.
  `COMMUNITY:APP_ISSUE:<id>`, `COMMUNITY:HELPER_REQUEST:<id>`,
  `COMMUNITY:SERVICE_BOOKING:<id>`. Retries/timeouts/double-sends reuse the same key.
- **Two-layer guarantee:** the idempotency key stops duplicate *requests*; the unique
  `(source_system, source_type, source_id)` constraint (§11) stops duplicate *records*
  even if the key store is bypassed or expired. Together they enforce BR-7.
- Every mutating endpoint on the new entities (create, transition, assign, cancel)
  requires an `Idempotency-Key`, consistent with the rest of Ops.

---

## 13. API proposal

**Minimum surface. Illustrative — not to be implemented in this task.** Existing
`service_request`/`assignment` APIs are reused unchanged for the technician path; only
the two new workflows need new endpoints.

**Tech Issue** (Tech Team; `TICKET_*`/`INCIDENT_*` permissions):
```
POST   /api/v1/tech-issues                 (create; Idempotency-Key)
GET    /api/v1/tech-issues                  (scoped/paginated list; filter status/severity/team)
GET    /api/v1/tech-issues/{id}
POST   /api/v1/tech-issues/{id}/triage      (OPEN→TRIAGED)
POST   /api/v1/tech-issues/{id}/start       (TRIAGED→IN_PROGRESS)
POST   /api/v1/tech-issues/{id}/resolve     (IN_PROGRESS→RESOLVED; resolution_notes)
POST   /api/v1/tech-issues/{id}/close       (RESOLVED→CLOSED)
POST   /api/v1/tech-issues/{id}/reopen      (RESOLVED→IN_PROGRESS)
```
(Prefer explicit action endpoints over `PATCH status`, matching Ops design DD-07.)

**Manual Field Officer request** (`ASSIGNMENT_*` permissions + `AREA_RESPONSIBLE`):
```
POST   /api/v1/manual-requests                 (create; Idempotency-Key)
GET    /api/v1/manual-requests                  (FO/area-scoped queue; filters)
GET    /api/v1/manual-requests/{id}
POST   /api/v1/manual-requests/{id}/ack         (QUEUED→ACKED_BY_FO)
POST   /api/v1/manual-requests/{id}/assign      (record manual assignee; →ASSIGNED)
POST   /api/v1/manual-requests/{id}/start       (ASSIGNED→IN_PROGRESS)
POST   /api/v1/manual-requests/{id}/complete    (IN_PROGRESS→COMPLETED)
POST   /api/v1/manual-requests/{id}/reassign    (change assignee; history preserved)
POST   /api/v1/manual-requests/{id}/cancel      (→CANCELLED)
```

**Community→Ops intake** (service-to-service; boundary rule: **service credential, not a
forwarded resident JWT**): Community calls whichever endpoint matches `opsIntakeType`
(`/tech-issues`, `/manual-requests`, or the existing `/service-requests`) with the
source-reference payload (§11) and a deterministic `Idempotency-Key` (§12).

**Reuse vs new:** the technician path reuses existing APIs entirely. Only `tech_issue`
and `manual_assignment_request` need the endpoints above. No generic ticket API.

---

## 14. Database proposal

**No migrations are created here.** Two new tables proposed; `service_request` untouched
except for optional additive source-reference columns.

**`tech_issue`** — Owner: Ops (Tech Team). Purpose: track app/technical issues.
- Key fields: `id`, `reference_no` (unique `TI-…`), `title`, `description`, `severity`,
  `priority`, `category` (`APP|BACKEND|INFRA|DATA|INTEGRATION|OTHER`), `owner_team`,
  `assigned_ops_user_id` (nullable FK `ops_user`), `apartment_id` (**nullable**),
  `status` (CHECK `OPEN|TRIAGED|IN_PROGRESS|RESOLVED|CLOSED|REOPENED|CANCELLED`),
  `resolution_notes`, `reopened_count`.
- Source reference: `source_system`, `source_type`, `source_id` + unique
  `(source_system, source_type, source_id)`.
- Audit/idempotency: `BaseEntity` (version/soft-delete/timestamps); `tech_issue_status_history`
  (insert-only) recommended; writes via `AuditService`/`ActivityLog`.
- Relationships: none to technician/assignment/field_visit (**intentionally**).

**`manual_assignment_request`** — Owner: Ops (Field Officer). Purpose: manually
coordinated (maid/other) requests.
- Key fields: `id`, `reference_no` (unique `MR-…`), `apartment_id`/`area_id`/`region_id`
  (scope anchors, NOT NULL for scope resolution), `request_type`
  (`MAID_REQUIRED|MAID_REPLACEMENT|MAID_AVAILABILITY|OTHER_MANUAL`), `title`,
  `description`, `priority`, `status` (CHECK `OPEN|QUEUED|ACKED_BY_FO|ASSIGNED|
  IN_PROGRESS|COMPLETED|CANCELLED`), `field_officer_id` (nullable FK `ops_user`),
  `assignee_type` (`EXTERNAL_PERSON|COMMUNITY_VENDOR_REF|COMMUNITY_HELPER_REF|
  OPS_WORKFORCE`), `assignee_ref`/`assignee_name`/`assignee_phone`, `completion_notes`.
- Source reference: same triple + unique constraint.
- Audit/idempotency: `BaseEntity`; `manual_assignment_status_history` (insert-only);
  `AuditService`/`ActivityLog`.
- Relationships: **no NOT-NULL technician FK**; optional `assignee_ref` is an opaque
  reference, not an FK to Community. Runs eligibility **only** if an Ops technician is
  explicitly chosen.

**`service_request`** — optional additive columns only: `source_system`, `source_type`,
`source_id` + unique constraint (shared with the boundary-doc recommendation). No change
to its status set or state machine.

**Why explicit tables over one generic table:** a single `ops_request` with a `type`
discriminator would need nullable technician/assignment columns for two types that must
never use them, one CHECK constraint spanning three incompatible status sets, and
branching in every service method — exactly the over-generic entity BR-9 warns against.
Three focused tables keep each state machine and each set of invariants clean and
independently testable.

---

## 15. Queue design

Three **separate** queues — they surface different work to different roles and must not
be combined (BR-5, BR-9).

**Tech Team Queue** (reads `tech_issue`; `TICKET_VIEW`)
```
OPEN → TRIAGED → IN_PROGRESS → RESOLVED   (filter by severity/team/category; Tech-Team scope, typically GLOBAL)
```

**Field Officer Queue** (reads `manual_assignment_request`; `ASSIGNMENT_VIEW` +
`AREA_RESPONSIBLE`)
```
OPEN/QUEUED (UNASSIGNED) → ACKED_BY_FO → ASSIGNED → IN_PROGRESS   (scoped to the FO's current areas via ScopeService)
```

**Technician Dispatch Queue** — the **existing** `OperationsQueueService`
(`dispatchReason` = `UNASSIGNED`(NEW)/`REWORK`/`AWAITING_ACCEPTANCE`). Unchanged.

Each queue is a scoped, paginated read over its own entity, mirroring the existing
`OperationsQueueService` pattern. No shared "all requests" queue.

---

## 16. Vendor / helper distinctions

Explicitly preserved (BR-8); no merge, no cross-entity FK, no rename.

- **Community `Helper`** = a **resident's domestic staff** (maid/cook/driver/nanny),
  `customer`-linked, with attendance/salary. **Community `HelperRequest`** = a resident
  asking for a **substitute** domestic helper — this is the primary source for a Manual
  FO request.
- **Ops `Helper`** = a **technician's assistant** on an assignment (`relationship
  MANAFY|TECHNICIAN|VENDOR`). Completely different concept. A Manual FO request that
  wants a maid records the maid as an **opaque assignee reference** (name/mobile or a
  `COMMUNITY_HELPER_REF`), **never** as an Ops `Helper` and **never** FK-linked.
- **Community `Vendor`** = **per-apartment** AMC/service vendor (GST/PAN/bank/rating).
  **Ops `Vendor`** = **global operational dispatch** vendor. Same word, different scope;
  keep separate. If a manual request assigns a society vendor, it is captured as a
  `COMMUNITY_VENDOR_REF` string, not an Ops vendor row.

The architecture documents these as four separate concepts and relies on the
source-reference/opaque-reference mechanism (§11) to point at Community records without
coupling databases.

---

## 17. WorkOrder coexistence

**Retain + bridge** (unchanged from `MANAFY-FULFILLMENT-BOUNDARY-AND-INTEGRATION.md` §5;
do not retire — the task confirms WorkOrder has a real operational lifecycle).

- `work_order` stays **Community-owned** as the society maintenance/asset/AMC-vendor
  record: it keeps `asset_id`, `vendor_id` (Community vendor), inline
  `estimated_cost`/`actual_cost`/`cost_breakdown`/`invoice_*`, before/after photos,
  `satisfaction_rating`, and its `close` step that increments `vendor.total_work_orders`.
  Finance does **not** depend on it; there are no notifications on it today.
- When a work order needs **real Manafy workforce dispatch** (instead of its free-text
  `technician_name`/`technician_mobile`), Community classifies it `TECHNICIAN_SERVICE`
  and creates **one** Ops `service_request` (`source_type = WORK_ORDER`), then projects
  the coarse operational status back onto the work order. `WorkOrder ≠ ServiceRequest`.
- A work order does **not** normally become a `tech_issue` or `manual_assignment_request`
  — it is society maintenance, not an app bug or a maid coordination task.
- Asset-service history, per-society vendor management/rating rollup, and the work
  order's own cost/invoice record cannot be replaced by Ops and stay in Community.

---

## 18. ServiceBooking coexistence

Unchanged from the boundary doc §4, and now with explicit routing:

- `service_booking` stays **Community-owned** intake (capture-only; `REQUESTED` with no
  transition code today). `ServiceBooking ≠ ServiceRequest`.
- On escalation, Community classifies each booking by its `serviceCode`:
  - Operational, Manafy-fulfilled (pest control, deep cleaning, local experts, appliance
    repair, painting) → `TECHNICIAN_SERVICE` → Ops `service_request`
    (`source_type = SERVICE_BOOKING`).
  - Maid/domestic-help oriented (`FIND_HELPER`/`RECURRING_HELPERS`) → candidate
    `MANUAL_ASSIGNMENT` (FO-coordinated) rather than technician dispatch.
  - Marketplace/other (water cans, laundry, fitness) → `COMMUNITY_ONLY`; may never need
    Ops.
- Community projects coarse Ops status back onto the booking; Ops holds only the
  booking reference.

---

## 19. Failure scenarios

| # | Scenario | Expected behavior | Source of truth | Mechanism |
|---|---|---|---|---|
| 1 | Community creates an app issue but Ops is unavailable | Community record persists; Ops create is retried later | Community (its record) | Retry with same deterministic key; outbox later (no broker now) |
| 2 | Community sends the same issue twice | Second call returns the existing `tech_issue` | Ops (the tech issue) | `Idempotency-Key` + unique `(source_system,source_type,source_id)` → `409 DUPLICATE_REQUEST`/same id |
| 3 | Field Officer receives the same manual request twice | One `manual_assignment_request`; the queue shows it once | Ops | Same idempotency + unique source triple |
| 4 | Field Officer assigns a manual request incorrectly | Reassign to the correct assignee; prior assignment preserved | Ops | `/manual-requests/{id}/reassign` + `manual_assignment_status_history`; optimistic lock |
| 5 | Tech issue is reopened | `RESOLVED → REOPENED → IN_PROGRESS`; `reopened_count`++ | Ops | `/tech-issues/{id}/reopen`; bounded reopen (§5) |
| 6 | Manual request cancelled after assignment | Allowed until `COMPLETED`; `→ CANCELLED`, reason recorded | Ops | `/manual-requests/{id}/cancel`; blocked once `COMPLETED` |
| 7 | Technician service request cancelled after dispatch | Existing behavior: active assignment `CANCELLED`, request `→ CANCELLED` | Ops | Existing `AssignmentAppService` cancel path (unchanged) |

Cross-cutting: Ops is authoritative for operational status; Community reconciles via a
coarse status callback or poll; a failed callback leaves Ops correct and Community
briefly stale until reconciliation (matches boundary doc §12/§17).

---

## 20. Migration / coexistence strategy

No big-bang; additive and feature-flagged.

- **Phase 0 (prerequisite):** resolve the **apartment-ownership mapping** (how Ops maps
  a Community `apartment` to its own `area_id`/`region_id` scope anchors) — a hard
  blocker for *any* apartment-scoped intake (`manual_assignment_request`,
  `TECHNICIAN_SERVICE`). `tech_issue` (often apartment-agnostic) can proceed first.
- **Phase 1:** add the shared source-reference columns + unique constraints (additive,
  nullable) to `service_request`; introduce `tech_issue` and its API/queue. Wire a
  single low-risk `TECH_ISSUE` intake behind a feature flag. Community entities and the
  existing technician path are untouched.
- **Phase 2:** introduce `manual_assignment_request` + FO queue; wire Community
  `HelperRequest` → `MANUAL_ASSIGNMENT` behind a flag once Phase 0 is done.
- **Phase 3:** wire the operational `service_booking` subset and operational
  `society_complaint` → `TECHNICIAN_SERVICE` (existing path), per the boundary doc.
- **Phase 4:** optional WorkOrder → Ops dispatch bridge (WorkOrder itself stays).
- **Always kept in Community during transition:** amenity bookings, SOS, informational
  complaints, domestic-helper tracking, asset-service logs, per-society vendor/contract
  management, all finance.
- Each new workflow ships as its **own** implementation task; this doc is design only.

---

## 21. Risks

- **Apartment-ownership mapping is a hard prerequisite** for apartment-scoped intake
  (carried over from the boundary docs).
- **Over-generalization drift:** pressure to "just add a type to service_request" would
  reintroduce the coupling this design avoids. Guard with BR-9.
- **Community has no app-issue entity today**, so `TECH_ISSUE` intake needs either a new
  Community capture record or an internal-only Ops creation path — confirm before build
  (§22).
- **Duplicate creation** if idempotency key or unique source constraint is missing —
  both are required, not either/or.
- **Status drift** if the coarse callback fails without reconciliation — needs a
  reconciliation poll.
- **Shared names (Helper/Vendor)** cause conceptual confusion — must keep distinct
  (§16).
- **Service-to-service auth** must use a service credential; never forward a resident
  JWT across the boundary.
- **Unbuilt permission stubs** (`INCIDENT_*`/`TICKET_*`) imply prior intent — confirm
  the intended mapping (`tech_issue` → `TICKET_*` vs `INCIDENT_*`) with the owner to
  avoid semantic mismatch.

---

## 22. Open decisions

1. **App-issue origin:** does Community add a "report an issue" capture record (with its
   own id for `source_id`), or are tech issues created internally in Ops only? (Affects
   §5/§11.)
2. **Permission mapping for `tech_issue`:** adopt `TICKET_*`, `INCIDENT_*`, or a new
   `TECH_ISSUE_*` family? (Catalog currently seeds `TICKET_*` and `INCIDENT_*` unbuilt.)
3. **Manual-request assignee model:** confirm the `assignee_type` set and whether
   `COMMUNITY_VENDOR_REF`/`COMMUNITY_HELPER_REF` are needed at MVP or later.
4. **Which `service_booking` codes and which complaint categories** escalate, and which
   maid flows go `MANUAL_ASSIGNMENT` vs `COMMUNITY_ONLY`.
5. **Source-reference shape:** the shared `(source_system, source_type, source_id)`
   triple (recommended) vs concrete `community_*_id` columns.
6. **SLA:** do `tech_issue` / `manual_assignment_request` need SLA timers at MVP? (Ops
   has no SLA fields today; recommend deferring.)
7. **Reopen window** for `tech_issue` (from `RESOLVED` only, or `CLOSED` within N days).
8. **Cancellation policy** once a manual request is `IN_PROGRESS` (recommend allow until
   `COMPLETED`).
9. **Service-to-service auth mechanism** (client-credentials app client vs API key).
10. **Apartment-ownership mapping** (Phase 0 blocker) — carried from the boundary docs.

---

## 23. Recommended implementation sequence

1. **Owner sign-off** on §22 (esp. app-issue origin, permission mapping, apartment
   ownership, and which sources escalate to which path).
2. **Shared intake plumbing (separate task):** add additive, nullable
   `source_system`/`source_type`/`source_id` + unique constraint to `service_request`;
   define the shared intake contract (service credential + deterministic
   `Idempotency-Key`). No behavior change to existing dispatch.
3. **`tech_issue` (separate task):** entity + state machine
   (`OPEN→TRIAGED→IN_PROGRESS→RESOLVED→CLOSED` + `REOPENED`/`CANCELLED`) + action APIs +
   Tech Team queue, reusing `TICKET_*`/`INCIDENT_*` permissions, `BaseEntity`,
   idempotency, audit. Wire one Community `TECH_ISSUE` flow behind a feature flag.
   (Apartment-agnostic, so it can ship before Phase 0.)
4. **`manual_assignment_request` (separate task, after apartment-ownership Phase 0):**
   entity + state machine + FO queue scoped by `area_field_officer`/`ScopeService`,
   using `ASSIGNMENT_*` permissions + `AREA_RESPONSIBLE`, **without**
   `TechnicianEligibilityService`. Wire Community `HelperRequest` → `MANUAL_ASSIGNMENT`
   behind a flag.
5. **Technician-service intake (separate task):** wire the operational `service_booking`
   subset + operational `society_complaint` (+ optional WorkOrder bridge) to the
   **existing** `service_request` path per the boundary doc — no changes to dispatch.
6. **Status projection back to Community** (coarse callback or poll) + reconciliation.
7. **Revisit outbox/events only when volume warrants** — REST-first, no broker now.

---

## Answers to the required decision questions (§25)

1. **Should `service_request` remain technician-focused?** **Yes.** Keep it and its
   `assignment`/`field_visit`/eligibility path unchanged; do not add a type
   discriminator.
2. **Do we need `tech_issue`?** **Yes** — a small, Ops-owned entity with its own Tech
   Team lifecycle. No existing entity fits.
3. **Do we need `manual_assignment_request`?** **Yes** — a small, Ops-owned entity for
   FO-driven manual assignment; `assignment` cannot represent it (NOT-NULL technician +
   eligibility).
4. **Should app issues ever enter Field Officer queues?** **No.** Tech Team only.
5. **Should maid requests enter technician dispatch?** **No.** Manual FO assignment; no
   eligibility unless the FO explicitly picks an Ops technician.
6. **Which Community requests remain entirely in Community?** Amenity bookings, SOS,
   informational complaints (noise/parking/internet), non-operational bookings (water
   cans/laundry/fitness), marketplace, finance, and any request not explicitly
   classified operational (BR-1 default).
7. **Where does request classification occur?** In **Community** (owns the trigger), as
   an explicit `opsIntakeType`, **re-validated in Ops**.
8. **How does Community communicate an operational request to Ops?** A synchronous
   service-to-service REST call (service credential, not a resident JWT) to the
   path matching `opsIntakeType`, with a deterministic `Idempotency-Key`; outbox/async
   later, no broker now.
9. **What identifiers cross the boundary?** `source_system`/`source_type`/`source_id`,
   `community_customer_id`/`community_apartment_id`/`community_flat_id`, category/
   priority/description/schedule, and coarse status — **no PII beyond the minimum, no
   finance detail, no cross-DB FKs**.
10. **How is duplicate creation prevented?** Deterministic `Idempotency-Key` **and** a
    unique `(source_system, source_type, source_id)` per Ops table.
11. **How are statuses synchronized?** Ops is authoritative for operational status;
    Community holds a coarse projection updated by callback/poll with reconciliation.
12. **What happens to existing `WorkOrder`?** Retain + bridge; stays Community-owned;
    optionally creates one Ops `service_request` when real dispatch is needed. Not
    retired.
13. **What happens to `ServiceBooking`?** Stays Community-owned intake; operational
    subset escalates to `TECHNICIAN_SERVICE`, maid-oriented to `MANUAL_ASSIGNMENT`,
    rest `COMMUNITY_ONLY`. `ServiceBooking ≠ ServiceRequest`.
14. **What happens to `SocietyComplaint`?** Stays Community-owned; only
    operational-category complaints escalate to `TECHNICIAN_SERVICE`; informational
    complaints never create Ops records.
