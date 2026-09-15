# Manafy Fulfillment Boundary & Integration — Community vs Ops

**Status:** READ-ONLY investigation. **No source, migration, API, or business logic was
modified in either project.** This document determines the ownership boundary and
integration contract between Community's fulfillment concepts (`service_booking`,
`work_order`, `society_complaint`) and Ops's fulfillment domain (`service_request`,
`assignment`, `field_visit`, workforce).

Everything below is grounded in the **actual source** of both services. Assumptions
that could not be confirmed are marked **UNKNOWN**. Three anchor rules were honored:
**WorkOrder ≠ ServiceRequest**, **ServiceBooking ≠ ServiceRequest**, and **not every
booking requires operational fulfillment** — each is proven from code, not assumed.

---

## 1. Executive summary

The two systems solve **different halves** of the same journey:

- **Community owns the resident/society-facing side**: what a resident *asks for*
  (`service_booking`), what a resident *complains about* (`society_complaint`), and
  the society's *own maintenance workflow* (`work_order`, tied to per-society vendors
  and assets). These are resident- and society-facing records.
- **Ops owns operational fulfillment**: `service_request` → `assignment` →
  `field_visit`, dispatched to a **real workforce** (technician FK, skills, area
  coverage, certifications, eligibility), with audit/idempotency/optimistic-locking
  and an atomic request↔assignment state projection.

**Key discovery from code:** Community's fulfillment is largely **intake/manual**
today — `service_booking` is capture-only (never advances past `REQUESTED` in code),
and `work_order` uses a **free-text technician name/mobile**, not a workforce entity.
Ops is the only system with a real dispatch/workforce engine.

**Recommended boundary:** Community remains the source of truth for bookings,
complaints, work orders, resident-facing status, and finance. When a Community record
needs **operational fulfillment by Manafy's workforce**, it creates **one** Ops
`service_request` (idempotent, by source reference), and Ops projects operational
status back. **Nothing is retired now** — the systems coexist behind a clean
integration seam.

---

## 2. Current Community model (from source)

| Entity | Where created/updated | Lifecycle (from code) | Notes |
|---|---|---|---|
| `service_booking` | `ServiceCatalogService.createBooking` (create); `getMyBookings` (read) | `REQUESTED` only — **no code advances/confirms/completes/cancels it** | Resident books a catalog `service_code`; capture-only; no vendor/tech/payment link in code |
| `society_complaint` | `CommunityService.raiseComplaint`, `updateComplaintStatus`, `rateComplaint` | `OPEN → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED` (+ `REOPENED`) | Resolved **in place**; `assignedTo` is a free UUID (not an FK); SLA hours/breach fields; rating/feedback |
| `work_order` | `VendorAssetService.createWorkOrder/assignWorkOrder/startWorkOrder/completeWorkOrder/closeWorkOrder` | `OPEN → ASSIGNED → IN_PROGRESS → COMPLETED → CLOSED` (also `SCHEDULED/INVOICED/CANCELLED` in the column) | Society maintenance/AMC + asset service; ties to Community `vendor` + `asset`; **technician is free text**, not an entity; carries cost/invoice/photos inline |
| `service_catalog_item` | `ServiceCatalogService` (seeded + admin CRUD) | active/featured/sortable | Resident-facing catalog; `BOOK` action targets a `ServiceBooking` |
| `vendor` | `VendorAssetService.createVendor` | ACTIVE/SUSPENDED/BLACKLISTED | **Per-apartment** vendor (GST/PAN/bank/rating/`total_work_orders`) |
| `helper` | (resident domestic-help tracking) | — | Resident's **maid/cook/driver/nanny** (customer-linked, monthly salary/attendance) |

Catalog `BOOK` codes today: `FIND_HELPER`, `RECURRING_HELPERS`, `PEST_CONTROL`,
`DEEP_CLEANING`, `LOCAL_EXPERTS` (plumber/electrician/carpenter), `APPLIANCE_REPAIR`,
`PAINTING`, `SALON_AT_HOME`, `FITNESS_TRAINER`, `LAUNDRY`, `WATER_CANS`
(`HELPER_TRACKING` is `NAVIGATE`, not a booking).

---

## 3. Current Ops model (from Phase 4A/4B source)

- `service_request` — status `NEW → ASSIGNED → IN_PROGRESS → COMPLETED → REWORK →
  CANCELLED` (a **projection** of assignment activity, DD-36); anchored on
  apartment/area/region; carries `category`, `priority`, `description`,
  `active_assignment_id`.
- `assignment` — `ASSIGNED → ACCEPTED → EN_ROUTE → ARRIVED → IN_PROGRESS → COMPLETED`
  plus `DECLINED/CANCELLED/NO_SHOW/REWORK`; **real technician FK** (+ optional
  helper/vendor); history-preserving reassignment; `assignment_status_history`.
- `field_visit` — per-assignment visit (schedule/arrival/departure/outcome).
- **Workforce** — technician (status/availability/skills/area coverage/certifications),
  deterministic eligibility, field officers.
- **Cross-cutting** — permission+scope authorization, audit, `Idempotency-Key`,
  optimistic locking, atomic SR↔assignment projection.

---

## 4. ServiceBooking analysis (dedicated — §23)

- **Resident-facing purpose:** a resident requests a catalog service (cleaning, pest
  control, local expert, appliance repair, salon, laundry, water cans, helper hire).
- **Booking lifecycle (code):** `createBooking` sets `REQUESTED`. **No further
  transition exists in code** — no confirm, complete, cancel, reschedule, feedback, or
  notification path. It is an **intake record**; fulfillment happens manually/offline
  today.
- **Payment dependency:** none in code (a nullable `quoted_amount` only).
- **Vendor/technician dependency:** none.
- **Cancellation/rescheduling/completion/feedback:** none implemented.
- **Conclusion:** `ServiceBooking` is **NOT** an Ops `service_request`. It is a
  Community-owned **origination source**. A *subset* (services fulfilled by Manafy's
  own workforce — e.g. PEST_CONTROL, DEEP_CLEANING, LOCAL_EXPERTS, APPLIANCE_REPAIR,
  PAINTING) *should* create an Ops `service_request` when Manafy fulfills them.
  Others (WATER_CANS, LAUNDRY, FITNESS_TRAINER, FIND_HELPER/RECURRING_HELPERS) are
  marketplace/domestic-help flows that **may never need Ops** (owner decision §20).

---

## 5. WorkOrder analysis (dedicated — §22, all 14 questions)

1. **Who creates it?** `VendorAssetService.createWorkOrder` (admin/society staff).
2. **Which APIs expose it?** `VendorAssetController` (create/assign/start/complete/
   close/list/get/by-vendor).
3. **Which client uses it?** Admin/society management (apartment-scoped). **UNKNOWN**
   which specific UI screen; it is an admin/ops-of-the-society tool, not a resident
   app screen.
4. **Resident-visible status?** Not directly (no resident endpoint found for it).
5. **Vendor assignment?** Yes — `vendor_id` → Community `vendor`.
6. **Technician assignment?** Yes but as **free-text `technician_name`/`technician_mobile`** — NOT a workforce entity.
7. **Tracks costs?** Yes — `estimated_cost`, `actual_cost`, `cost_breakdown`,
   `invoice_number/url` (inline).
8. **Finance depends on it?** **No** — `FinanceService` has zero references to
   `work_order`/`service_booking` (grep = 0). Costs live inline on the work order.
9. **Notifications depend on it?** **UNKNOWN** (no direct reference found; not confirmed absent).
10. **Reports depend on it?** **UNKNOWN** (ReportsController not fully traced; likely counts).
11. **Actively used?** Yes — full create→assign→start→complete→close path is implemented and wired.
12. **Legacy?** No — it is current, working society maintenance functionality.
13. **Can Ops `service_request` + `assignment` replace its operational responsibilities?**
    **Partially.** Ops can replace the *dispatch/execution* (assign a real technician,
    track en-route/arrival/completion) — which WorkOrder does only crudely (free-text
    tech). Ops does **not** replace WorkOrder's **asset linkage**, **per-society vendor
    contract linkage**, **inline cost/invoice**, or **satisfaction/vendor-rating
    rollup**.
14. **What cannot be replaced today?** Asset-service history, per-society vendor
    management + rating rollup, and the work order's own cost/invoice record.

**Recommendation for WorkOrder:** **RETAIN + BRIDGE** (do NOT retire). WorkOrder stays
Community-owned as the society maintenance/asset/vendor record. When a work order needs
Manafy's *real* workforce dispatch, it may create/reference an Ops `service_request`
and consume Ops operational status. WorkOrder ≠ ServiceRequest.

---

## 6. SocietyComplaint analysis (dedicated — §7C)

- **Path (code):** resident `raiseComplaint` (`OPEN`) → staff `updateComplaintStatus`
  (status + `assignedTo` free UUID + `resolutionNotes`) → resident `rateComplaint`.
- **Does a complaint create a work order?** **No automatic path in code.**
  `createWorkOrder` accepts an *optional* `complaintId`, so an admin *may* manually
  link one, but nothing auto-generates it. Complaints are resolved **in place**.
- **Categories:** ELECTRICAL, WATER, PARKING, LIFT, CLEANING, SECURITY, NOISE,
  PLUMBING, INTERNET, OTHER. Some are **operational** (plumbing/electrical/lift/
  cleaning) and some are **community/informational** (noise/parking/internet).
- **Conclusion:** Complaint is Community-owned. Only **operational-category** complaints
  that require a Manafy technician should optionally escalate to an Ops
  `service_request`. Informational complaints must **NOT** create service requests.

```
Complaint
  ├── informational (NOISE/PARKING/INTERNET/…) → stays in Community (resolved in place)
  └── operational (PLUMBING/ELECTRICAL/LIFT/CLEANING/WATER) → MAY escalate → Ops service_request
```

---

## 7. State-machine comparison

Only semantically-equivalent statuses are mapped; one-sided statuses are flagged.

**ServiceBooking vs Ops service_request**

| Community (ServiceBooking) | Ops (service_request) | Equivalent? | Recommendation |
|---|---|---|---|
| REQUESTED | NEW | Partial (both "not yet worked") | On escalation, a REQUESTED booking creates a NEW request |
| (none) | ASSIGNED / IN_PROGRESS / COMPLETED / REWORK | No | Ops-only operational states; projected back as booking status |
| (no CANCELLED in code) | CANCELLED | No | Community would add booking cancellation later |

**WorkOrder vs Ops assignment**

| Community (WorkOrder) | Ops (assignment) | Equivalent? | Recommendation |
|---|---|---|---|
| OPEN | (request NEW / no assignment) | Partial | Pre-dispatch |
| ASSIGNED | ASSIGNED | Semantically yes (but WO tech = free text) | Map on bridge |
| SCHEDULED | (assignment scheduledStart set) | Partial | Ops models schedule as fields, not a status |
| IN_PROGRESS | IN_PROGRESS | Yes | Map |
| COMPLETED | COMPLETED | Yes | Map |
| INVOICED | (none) | No | Community/finance-only |
| CLOSED | (none — request COMPLETED) | No | Community approval/rating step |
| CANCELLED | CANCELLED | Yes | Map |
| (none) | ACCEPTED / DECLINED / EN_ROUTE / ARRIVED / NO_SHOW / REWORK | No | Ops-only operational granularity |

**SocietyComplaint vs Ops service_request**

| Community (SocietyComplaint) | Ops (service_request) | Equivalent? | Recommendation |
|---|---|---|---|
| OPEN | NEW | Partial | On escalation only |
| ASSIGNED / IN_PROGRESS | ASSIGNED / IN_PROGRESS | Partial | Map when escalated |
| RESOLVED / CLOSED | COMPLETED | Partial | Ops completion → complaint RESOLVED |
| REOPENED | REWORK | Partial | Map |

---

## 8. Ownership matrix

| Domain | Community | Ops | Source of Truth |
|---|---|---|---|
| Customer / resident identity | Yes | projection (`ops_user`) | **Community** |
| Resident ↔ apartment/flat membership | Yes | No | **Community** |
| Service catalog | Yes | (none) | **Community** |
| Service booking | Yes | reference only | **Community** |
| Society complaint | Yes | reference (when escalated) | **Community** |
| Work order (asset/AMC maintenance) | Yes | reference (when dispatched) | **Community** |
| Service request (operational) | reference | Yes | **Ops** |
| Assignment | No | Yes | **Ops** |
| Field visit | No | Yes | **Ops** |
| Technician / workforce | No | Yes | **Ops** |
| Ops helper (assignment assistant) | No | Yes | **Ops** |
| Vendor (per-society AMC) | Yes | No | **Community** |
| Vendor (operational dispatch) | No | Yes | **Ops** |
| Resident domestic helper (maid/cook) | Yes | No | **Community** |
| Operational completion/cost info | consumes | produces | **Ops** (operational), **Community** (resident-facing charge) |
| Resident-visible status | Yes (projection of Ops when escalated) | authoritative for operational status | split (see §12) |
| Finance / invoice / ledger / payout | Yes | No (deferred) | **Community** |

---

## 9. Integration recommendation

Evaluated options A–E against the actual code (separate DBs, **no** message broker,
Community owns triggers, Ops owns fulfillment):

- **A (Community → Ops):** natural — Community owns the trigger. ✅
- **B (Ops pulls Community):** rejected — Ops would need to poll/scan Community.
- **C (synchronous REST create):** simplest reliable MVP. ✅
- **D (event/outbox):** best long-term but no broker/outbox exists yet.
- **E (hybrid REST-now, outbox-later):** ✅ **RECOMMENDED.**

**Recommendation: Option E (hybrid, REST-first).** Community synchronously calls Ops
`POST /service-requests` with a source reference + `Idempotency-Key`; Ops calls back
(or Community polls) for status. Introduce a transactional **outbox** in each service
later (no broker required) once volume justifies it — same REST contract, async
delivery. Do **not** add Kafka/SQS for this.

---

## 10. API contract proposal (illustrative — not final)

Community → Ops (service-to-service; see §19 for auth):
```http
POST /api/v1/service-requests
Idempotency-Key: <deterministic-per-source>          # e.g. "COMMUNITY:SERVICE_BOOKING:<bookingId>"
Authorization: Bearer <service credential>            # NOT a forwarded resident JWT
X-Correlation-Id: <uuid>
```
```json
{
  "source": "COMMUNITY",
  "sourceType": "SERVICE_BOOKING | WORK_ORDER | COMPLAINT",
  "sourceId": "<community record id>",
  "communityCustomerId": "<customer id>",
  "communityApartmentId": "<apartment id>",
  "communityFlatId": "<flat id, optional>",
  "category": "PLUMBING | ELECTRICAL | HVAC | CLEANING | SECURITY | GENERAL | OTHER",
  "priority": "LOW | MEDIUM | HIGH | URGENT",
  "description": "…",
  "scheduledAt": "…optional…"
}
```
Response: the standard Ops `ApiResponse` envelope with the created `service_request`
(id, referenceNo, status). Duplicate key → `DUPLICATE_REQUEST` (409) returning/ු
referencing the already-created request. Validation errors → `VALIDATION_ERROR` (400).
Ops must map Community apartment/area to its own scope anchors (owner decision — see
apartment ownership in the boundary doc). **This exact contract is a proposal**; final
shape depends on the apartment-ownership decision.

Status callback Ops → Community (or Community poll `GET /service-requests/{id}`):
minimal operational status only (see §12).

---

## 11. Identifier / idempotency strategy

- Ops `service_request` should carry a **source reference**, not duplicated booking
  data. The minimum required is a **single polymorphic pair** (`source_type` +
  `source_id`), or — if concrete columns are preferred — add **only the ones actually
  used**: `community_booking_id`, `community_work_order_id`, `community_complaint_id`.
  Do **not** add all three speculatively; add per the flows that ship.
- **Idempotency:** the caller sends a **deterministic `Idempotency-Key`** derived from
  the source (`COMMUNITY:<sourceType>:<sourceId>`). Combined with a **unique
  constraint on (`source_type`,`source_id`)** in Ops, this guarantees:
  ```
  ONE Community fulfillment source  →  ONE Ops service_request
  ```
  even under retry/timeout/double-send. Reuses the existing Phase 1 `IdempotencyService`.

---

## 12. Status synchronization

- **Ops is authoritative for operational status** (assignment lifecycle, field visit).
- **Community is authoritative for the resident-facing record** (booking/complaint/
  work order) and projects Ops status onto it.
- Community needs only a **coarse operational status** (e.g. NEW→ASSIGNED→IN_PROGRESS→
  COMPLETED/CANCELLED) — **not** assignment internals, technician PII, or cost
  breakdown. Expose a **minimal projection**, never Ops internal detail.

```
Community record (booking/complaint/WO)
      │ create (REST + Idempotency-Key)
      ▼
Ops service_request ──▶ assignment ──▶ field_visit ──▶ COMPLETED
      │ operational status callback / poll (coarse)
      ▼
Community updates its resident-facing status projection
```

---

## 13. Cancellation / rescheduling (state transition matrix)

| Action | Allowed? | Effect |
|---|---|---|
| Community cancels booking after Ops request exists | Yes (owner-confirmed policy) | Community calls Ops cancel; Ops cancels the active assignment + request → CANCELLED |
| Community changes date/time | Yes | Community calls Ops reschedule (assignment `scheduledStart/End`) |
| Community changes service type | **No** (recommend) | Cancel + create a new request (category is an eligibility input) |
| Ops rejects a request | Yes | Assignment DECLINED → request back to NEW; surfaced to Community |
| Ops cancels fulfillment | Yes | Request CANCELLED; Community notified |
| Booking cancelled while assignment in progress | Policy needed (owner) | Recommend: allow cancel → assignment CANCELLED, but block after COMPLETED |
| In-progress work order | Community-owned | Unaffected unless it created an Ops request |
| Override completion | Ops only, via its RBAC | Community cannot force-complete an Ops request |

---

## 14. Vendor / workforce reconciliation

- Community `Vendor` = **per-apartment** AMC/service vendor (GST/PAN/bank/rating/
  `total_work_orders`). Ops `Vendor` = **global operational dispatch** vendor. **Same
  word, different scope/semantics.**
- Community `Helper` = **resident's domestic staff** (maid/cook/driver/nanny). Ops
  `Helper` = **technician's assistant** on an assignment. **Completely different
  concepts.**
- Community WorkOrder technician = **free text**; Ops technician = a real workforce
  entity.

**Recommendation:** **Ops owns operational workforce** (technician/helper/dispatch
vendor). Do **not** merge with Community vendor/helper. If a future need arises to
correlate a society's preferred vendor with an operational vendor, use an **optional
reference / canonical vendor identity** later — **do not migrate vendor data now**
(explicitly out of scope).

---

## 15. Service catalog reconciliation

- Community owns the **customer-facing catalog** (`service_catalog_item`). Ops has no
  catalog table (Phase 4B derives required skill from the request **category** string).
- **Recommendation:** Community remains catalog owner. Ops does **not** duplicate the
  catalog. On integration, Community passes the **service code / category**; Ops maps
  it to its internal skill/category (an Ops-internal concern). Only introduce an Ops
  catalog projection if a real operational need appears (none today).

---

## 16. Apartment / customer references

Do **not** copy `customer`/`resident`/`apartment`/`flat` into Ops for fulfillment.
Pass **external identifiers** (`communityCustomerId`, `communityApartmentId`,
`communityFlatId`) on the request. **No cross-database foreign keys.** How Ops maps a
Community apartment to its own area/region scope anchors depends on the **apartment
ownership decision** in `MANAFY-SERVICE-BOUNDARY-AND-INTEGRATION.md` (§21.3) — that
decision is a prerequisite for wiring booking→request.

---

## 17. Failure / retry scenarios

| Case | Expected behavior | Source of truth | Strategy |
|---|---|---|---|
| 1. Community creates booking, Ops down | Booking persists; request creation queued/retried | Community (booking) | Retry with same Idempotency-Key; outbox later |
| 2. Community times out but Ops created the request | No duplicate on retry | Ops (request) | Deterministic key + unique (source_type,source_id) |
| 3. Community retries same request | Returns existing request | Ops | Idempotent create → `DUPLICATE_REQUEST` / same id |
| 4. Ops→Community status callback fails | Ops state stands; Community stale briefly | Ops (operational) | Retry callback and/or Community reconciliation poll |
| 5. Booking cancelled while Ops assignment exists | Cancel propagates to Ops (policy) | split | Community cancel → Ops cancel; block if COMPLETED |
| 6. Technician assigned then unavailable | Ops reassigns (history preserved) | Ops | Existing reassignment; Community sees status only |
| 7. Ops completes, Community never notified | Ops COMPLETED stands | Ops | Reconciliation poll / replayed callback |

---

## 18. Migration / coexistence strategy (no big-bang)

- **Phase 1:** Community fulfillment unchanged. Add the Ops integration endpoint +
  source reference/idempotency. Wire **one** low-risk flow (e.g. an operational
  `service_booking` category) to create an Ops request behind a feature flag.
- **Phase 2:** New eligible operational requests (selected booking categories +
  operational complaints) route to Ops. Existing work orders continue in Community.
- **Phase 3:** All eligible operational fulfillment goes through Ops; Community shows
  the projected status.
- **Phase 4:** Only if justified, deprecate the *free-text technician* portion of
  WorkOrder in favor of Ops dispatch — WorkOrder itself (asset/vendor/cost) **stays**.
- **Must keep using Community's model during transition:** amenity bookings, domestic
  helper tracking, informational complaints, asset-service logs, per-society vendor/
  contract management, and all finance.

---

## 19. Risks

- Apartment-ownership decision is a **hard prerequisite** (Ops scope anchors vs
  Community apartment ids).
- Double fulfillment if idempotency/unique-constraint isn't enforced (mitigated §11).
- Status drift if callbacks fail without reconciliation (mitigated §12/§17).
- Conceptual confusion from shared names (Vendor/Helper) — must keep them distinct.
- Service-to-service auth must **not** forward resident JWTs (§ security below).
- UNKNOWNs (notifications/reports on WorkOrder) could hide coupling — confirm before
  any deprecation.

---

## 20. Open owner decisions

1. **Apartment ownership** (from the service-boundary doc) — prerequisite for mapping
   Community apartment → Ops scope.
2. **Which booking categories require Ops fulfillment** vs stay Community-only
   (e.g. WATER_CANS/LAUNDRY/FITNESS/FIND_HELPER likely never Ops).
3. **Which complaint categories escalate** to Ops (operational only).
4. **Cancellation policy** once an assignment is in progress.
5. **Source reference shape** — polymorphic (`source_type`+`source_id`) vs concrete
   `community_*_id` columns.
6. **Service-to-service auth mechanism** (client-credentials app client vs API key).
7. **WorkOrder future** — retain-and-bridge (recommended) confirmation.
8. **Whether ServiceBooking gains a real lifecycle** in Community or is fully delegated
   to Ops once escalated.

---

## 21. Recommended implementation sequence

1. Owner resolves §20.1 (apartment ownership) and §20.2/§20.3 (which sources escalate).
2. Ops: add source reference + unique `(source_type, source_id)` + accept the
   integration create (behind existing idempotency). *(Separate implementation task.)*
3. Community: on the chosen flow(s), call Ops with a deterministic Idempotency-Key.
4. Add coarse status projection back to Community (callback or poll).
5. Roll out per §18 phases behind a feature flag; reconcile; expand.
6. Revisit outbox/events only when volume warrants (no broker now).

---

## Diagrams

**Booking → fulfillment (for operational categories only)**
```
Resident → Community ServiceBooking (REQUESTED)
   │  (category is operational AND Manafy-fulfilled)
   ▼
Ops service_request (NEW)  ── idempotent by COMMUNITY:SERVICE_BOOKING:<id>
   ▼
assignment → field_visit → COMPLETED
   │ coarse status callback
   ▼
Community projects status onto the booking
```

**Complaint → fulfillment (actual recommended path)**
```
Resident → Community SocietyComplaint (OPEN)
   ├── informational category → resolved in Community (no Ops)
   └── operational category → Ops service_request (NEW) → assignment → COMPLETED
                                  │ status callback
                                  ▼
                              complaint → RESOLVED
```

**Existing WorkOrder (current vs proposed)**
```
CURRENT:  Community WorkOrder OPEN → ASSIGNED(vendor + free-text tech) → IN_PROGRESS → COMPLETED → CLOSED
PROPOSED: WorkOrder stays in Community (asset/vendor/cost). When real dispatch is needed:
          WorkOrder → creates Ops service_request → assignment(real technician) → COMPLETED → status back to WorkOrder
```

**Status synchronization (authority)**
```
Community  ── authoritative: booking/complaint/work-order record, resident status, finance
    ▲  │
    │  ▼  create (REST + idempotency)
Ops        ── authoritative: service_request / assignment / field_visit operational status
```

---

## Final recommendation (A–S)

- **A. Community's responsibility:** resident/society-facing records — bookings,
  complaints, work orders, catalog, per-society vendors, domestic helpers, finance,
  and resident-facing status.
- **B. Ops's responsibility:** operational fulfillment — service_request, assignment,
  field_visit, real workforce dispatch, eligibility, operational status.
- **C. Community entities that remain:** all of them (service_booking, work_order,
  society_complaint, service_catalog_item, vendor, helper, finance, asset).
- **D. Community entities that become integration sources:** `service_booking`
  (operational categories), operational `society_complaint`, and `work_order` needing
  real dispatch.
- **E. Ops authoritative entities:** service_request, assignment,
  assignment_status_history, field_visit, technician/workforce.
- **F. Projections/references:** Ops holds only **references** to Community
  customer/apartment/booking/work-order/complaint ids; Community holds a **status
  projection** of the Ops request.
- **G. When is a service_request created?** Only when a Community record needs
  **operational fulfillment by Manafy's workforce** (selected booking categories,
  operational complaints, dispatch-needing work orders).
- **H. Which Community requests should NOT create service requests?** Amenity/facility
  bookings; non-operational bookings (water cans, laundry, fitness, domestic-helper
  hire); informational complaints (noise/parking/internet); pure asset-service logs.
- **I. Status sync:** Ops authoritative operationally; Community authoritative for its
  record; coarse status projected back (no Ops internals).
- **J. Idempotency:** deterministic `Idempotency-Key` + unique `(source_type,source_id)`
  in Ops → one source → one request.
- **K. Cancellation/rescheduling:** Community-initiated, propagated to Ops per §13;
  blocked after COMPLETED.
- **L. WorkOrder:** **retain + bridge** (not retired); WorkOrder ≠ ServiceRequest.
- **M. ServiceBooking:** Community-owned intake; escalate the operational subset to
  Ops; ServiceBooking ≠ ServiceRequest.
- **N. SocietyComplaint:** Community-owned; escalate only operational categories.
- **O. Vendor/helper/technician duplication:** keep distinct — Ops owns operational
  workforce; Community owns society vendors + domestic helpers; **no migration now**.
- **P. Service catalog duplication:** Community owns the catalog; Ops keeps none;
  pass code/category by reference.
- **Q. APIs required:** one Community→Ops create (idempotent) + one status
  read/callback; nothing else initially.
- **R. Data that must cross the boundary:** source ids, apartment/customer references,
  category/priority/description, schedule, and coarse operational status.
- **S. Data that must NOT cross:** resident PII beyond the minimum, finance/invoice
  detail, Ops assignment internals/technician PII, and any direct DB access.
