# Manafy Service Boundary & Integration — Architecture Reconciliation

**Status:** Investigation complete. Authentication reconciliation **implemented** in
`ManafyOpsSvcRst` (additive only — see §22 "Authentication Reconciliation"). The
boundary/ownership design (§1–§21) remains the authoritative target and still requires
owner approval for the non-auth items.

> **Authentication correction (authoritative):** MSG91 is **NOT a separate
> authentication provider**. Cognito is the sole production authentication authority;
> MSG91 is only the **SMS OTP delivery pipe inside the Cognito CreateAuthChallenge
> Lambda**. Neither `ManafyCommunitySvcRst` nor `ManafyOpsSvcRst` contains (or should
> contain) a MSG91 Java integration. See §22.

**Scope of investigation:** actual source of `ManafyCommunitySvcRst` and
`ManafyOpsSvcRst` (entities, controllers, security, migrations, config) — not design
docs. Where the source is insufficient, items are marked **UNKNOWN**.

---

## 0. Reading guide / confidence

Every claim below is grounded in files that were read. The two biggest surprises vs
the task's assumptions:

1. Community's **primary, active** auth is **NOT Cognito** — it is a self-hosted OTP
   flow issuing **self-signed HS256 JWTs**. Cognito is scaffolded but gated off.
2. Community **already has its own operational fulfillment domain** (`work_order`,
   `service_booking`) that overlaps directly with Ops Phase 4A/4B
   (`service_request` + `assignment`).

---

## 1. Current architecture

Two independent Spring Boot services, **separate databases**, separate ports/context
paths, each self-contained (its own security, envelope, base entity, RBAC).

| | ManafyCommunitySvcRst | ManafyOpsSvcRst |
|---|---|---|
| Purpose | Resident/community app + society ops (gate, finance, CRM, guard, bookings) | Internal Admin/Ops (apartments, workforce, service requests, dispatch) |
| Port / context | 8082 `/ManafyCommunitySvcRst` | 8083 `/ManafyOpsSvcRst` |
| DB (dev default) | H2 file `manafycommunity` | H2 file `manafyops` |
| Package root | `com.manafy.community` (`common`, `gate`, `location`) | `com.manafy.ops` (`common`, `identity`, `org`, `apartment`, `workforce`, `servicerequest`, `dispatch`) |
| Primary auth | **Legacy OTP → self-signed JWT** (Cognito scaffolded, gated off) | **Cognito JWT only** |
| Canonical person | `customer` (by mobile) | `ops_user` (by cognito_sub) |
| Schema mgmt | Flyway, `baseline-on-migrate` (adopted a pre-existing schema at V1) | Flyway from V1 (greenfield), V1–V10 |
| Maturity | Large monolith, many domains, mixed conventions | Newer, layered, disciplined conventions |

They are currently **two independently evolving systems**, not one platform.

---

## 2. Current authentication flow

**Community (legacy path — ACTIVE):**
```
Resident → POST /api/v1/auth/send-otp (mobile)          → otp_request row (6-digit, 5-min)
Resident → POST /api/v1/auth/verify-otp (refId, otp)    → find/create customer (by mobile)
                                                        → JwtUtil HS256 access+refresh (sub=customerId, claim role="CUSTOMER")
App      → Authorization: Bearer <self-signed JWT>      → JwtAuthFilter → AUTH_PRINCIPAL → SecurityContext
POST /api/v1/auth/refresh                                → new tokens
GET  /api/v1/auth/me                                     → resolves appRole GUARD/RESIDENT/ADMIN/PENDING/NEW
```
`SecurityConfig` permits all transport; authorization is enforced in the
service/controller layer (`SecurityContext`, `PermissionService`, `TenantAccessService`).

**Community (Cognito path — SCAFFOLDED, INACTIVE):** `SecurityConfig` wires a Nimbus
resource-server decoder **only when** `manafy.cognito.issuer-uri` is set (blank today);
`CognitoJwtAuthenticationConverter` maps `sub`→principal, derives **no** authorities
from the token.

**Ops (Cognito path — the only path):** resource-server decoder (same gating), a
Cognito JWT converter, then `CurrentUserService` resolves `sub` → `ops_user` (auto-
provision on first login, status-gated ACTIVE/DISABLED). Authorization = DB roles/
permissions/scope.

**Reconciliation point:** the two services do **not** share an auth mechanism today,
but they are **built to converge**: identical `manafy.cognito.*` config keys and the
same "wire decoder only if issuer-uri present" pattern; Community's config comments
say legacy is transitional and "Cognito owns OTP going forward."

---

## 3. Current OTP flow

OTP exists **only in Community** (`otp_request` table, `AuthController`,
`OtpRequestRepository`). It self-generates a 6-digit code, stores it (5-min expiry,
attempt-count guard), and on verify find/creates a `customer` by mobile then issues
self-signed JWTs. Delivery is a TODO (dev echoes the OTP when `otp.expose-in-response`).

**Ops has NO OTP** — it never authenticates users directly; it validates a Cognito
JWT. This is the correct target: **OTP must not be duplicated into Ops.**

---

## 4. Current Cognito architecture

| Aspect | Community | Ops |
|---|---|---|
| Config keys | `manafy.cognito.{region,user-pool-id,app-client-id,issuer-uri}` | **identical keys** |
| Env vars | `AWS_REGION, COGNITO_USER_POOL_ID, COGNITO_APP_CLIENT_ID, COGNITO_ISSUER_URI` | **identical** |
| Decoder | Nimbus `withIssuerLocation`, gated on issuer-uri | **same pattern** |
| Validators | issuer+expiry, audience (`client_id` or `aud`), `token_use ∈ {access,id}` | issuer+expiry, `token_use=access` (per test base) |
| Authorities from token | none (DB is authz source) | none (DB is authz source) |
| Currently configured? | **No** (issuer-uri blank) | **No** (issuer-uri blank) |

**Same-JWT feasibility:** because both accept the same issuer/app-client and derive
authorization from their own DB, a **single Cognito User Pool + app client** can issue
one JWT that **both** services validate independently. Ops accepts `token_use=access`
only; Community accepts `access` or `id` — a minor divergence to align (see §18).
**UNKNOWN:** whether a real pool/app-client exists yet (both blank in committed config).

---

## 5. User identity architecture

| | Community | Ops |
|---|---|---|
| Canonical entity | `customer` (mobile unique) | `ops_user` |
| Identity link fields | `identity_provider` (LEGACY/COGNITO/GOOGLE_WORKSPACE), `external_identity_id` (= Cognito sub) | `cognito_sub` (unique) |
| Role/profile projections | `resident` (customer↔apartment↔flat, role), `security_guard` (customer↔apartment) | `user_role` + `user_scope` (RBAC) |
| Current linkage | LEGACY (sub not yet populated) | sub-based (but pool not configured) |

Both entities were **explicitly designed to anchor on the Cognito `sub`** (Community
via `external_identity_id`, Ops via `cognito_sub`). Neither is populated by a live pool
yet. There is currently **no cross-service identity link** — a `customer` and an
`ops_user` for the same human are unrelated rows in two DBs.

**Target:** the Cognito `sub` is the immutable cross-service identity key. `customer`
is the canonical **person** record (contact profile). `ops_user` becomes an
**operational projection** keyed by the same `sub` (see §17), not a second person.

---

## 6. Apartment ownership

| | Community `location.apartment` | Ops `apartment` |
|---|---|---|
| Columns | area_id, apartment_name, address, lat/long, active | code, name, legal_name, region_id, area_id, address*, city/state/pincode, status (8-state lifecycle), assigned_field_officer_id, timezone, activated/suspended/deleted_at |
| Children | `apartment_flat` (units) | building/unit/facility (Phase 2) |
| Area/Region | `location.area` (area_id only, no region) | `org.region` + `org.area` |
| Purpose | Where residents live (community-facing) | Operational/onboarding master (ops-facing) |

**Two different models, two databases, same table name.** This is genuine duplication
of the *concept* but with different shapes and different lifecycles. Neither is a
strict superset. **This is the hardest ownership decision** and needs owner input (§21).

---

## 7. Booking ownership

Community owns two distinct "booking" concepts (Ops has none):

- **`amenity_booking`** (+ `amenity`): resident reserves a shared facility (clubhouse,
  gym). **Self-service; typically NO technician / no Ops fulfillment.**
- **`service_booking`**: resident books a **catalog service** (`service_code`, e.g.
  PEST_CONTROL, deep cleaning) — status `REQUESTED→CONFIRMED→IN_PROGRESS→COMPLETED→
  CANCELLED`, `quoted_amount`. **This is the resident-origination entry point for
  operational work.**
- **`work_order`**: complaint/asset → vendor → technician (as **free-text name/mobile,
  not an FK**) → progress → cost/invoice → closure. Community's **own** ad-hoc
  operational fulfillment, overlapping Ops `service_request`+`assignment` but without a
  real workforce link.

Bookings are owned by Community and must **not** be duplicated into Ops.

---

## 8. Service ownership

- **Community `service_catalog_item`** (`ServiceCatalogService`): the resident-facing
  catalog a `service_booking.service_code` references.
- **Ops** has **no** service catalog table (only permission codes named `SERVICE_*`).
  Ops Phase 4B eligibility derives required skill from the request **category** string.

**Recommended owner: Community** owns the customer-facing service catalog. Ops should
consume category/service-code by reference, not build a competing catalog. (If Ops
later needs skill↔service mapping, that is an Ops-internal concern, not a second
customer catalog.)

---

## 9. Service Request ownership

- **Ops** owns `service_request` (Phase 4A) + `assignment`/`field_visit`/history
  (Phase 4B) — the real operational workflow with workforce FKs, state machines,
  scope, audit, idempotency.
- **Community** has the *inputs* (`service_booking`, `work_order`, `society_complaint`).

**Recommended: Ops owns the operational Service Request + Assignment** (fulfillment).
Community owns the **resident-facing booking/complaint**. `work_order` is the
duplication to retire over time (see §7/§H) — Ops's model supersedes it.

---

## 10. Booking → Service Request mapping

Not every booking becomes an Ops service request:

| Community origin | Needs Ops fulfillment? | Mapping |
|---|---|---|
| `amenity_booking` (facility reservation) | **No** | stays in Community |
| `service_booking` PEST_CONTROL / CLEANING / etc. | **Yes** | → Ops `service_request` (category from service_code) |
| `work_order` (maintenance: plumbing/electrical/HVAC…) | **Yes** | → Ops `service_request` (+ assignment) |
| `society_complaint` (raised) | **Sometimes** (only when it needs a technician) | → Ops `service_request` when escalated |
| Ops-originated (admin/support on behalf) | **Yes** | created directly in Ops |

Ops `service_request` should carry an **external origin reference** (see §10/§B) so it
can answer "which Community booking generated this," while Community remains the source
of truth for the booking itself.

---

## 11. User → Ops authorization mapping

Authentication (who) and authorization (what) stay separate:
```
Cognito JWT (sub) → canonical identity → Ops ops_user (projection) → user_role → permission → scope → resource
```
Residents/apartment-admins authenticate the same way but **carry no Ops permissions**;
Ops RBAC (Phase 1) decides operational capability. OTP must never carry Ops permissions.

---

## 12. API integration strategy

The two services must talk **through APIs/events, never each other's DB** (§12/§15).
Given the current state (separate DBs, no messaging infra, Community owns bookings, Ops
owns fulfillment), a **hybrid** is recommended (§13).

---

## 13. REST / event / hybrid recommendation

- **No message broker exists today** (grep for outbox/Kafka/SQS = none). Do **not**
  introduce Kafka/SQS now.
- **Recommended (MVP): Hybrid, REST-first.**
  - **Booking → Service Request** ingestion: Community calls Ops REST
    (`POST /service-requests` with an origin reference) when a `service_booking`/
    `work_order` needs fulfillment; OR Ops pulls via a Community REST endpoint. Prefer
    **Community pushes to Ops** (Community already owns the trigger).
  - **Status callbacks** (assignment completed → booking COMPLETED): start with a
    **synchronous REST callback** from Ops to Community, wrapped so a callback failure
    doesn't fail the Ops transaction (retry/log).
  - **Evolve to events later** via a transactional **outbox** in each service (already
    the recommended "Phase 4C") once volume justifies it — same REST contract, async
    delivery. No broker required initially (outbox + poller/webhook).

---

## 14. Projection strategy

Introduce a projection **only** where there is a real operational/query need:

| Candidate projection in Ops | Recommended? | Rationale |
|---|---|---|
| `community_booking_projection` | **No (MVP)** | store only `service_request.community_booking_id` (a reference) + booking snapshot fields captured at creation; fetch live details via REST when needed |
| `community_apartment_projection` | **Likely yes (later)** | Ops queues filter by apartment/area; if Ops adopts Community apartments as source of truth (§21 Option 2), Ops needs a read model. Today Ops has its own `apartment`, so defer |
| `community_user_projection` | **Partially — that's `ops_user`** | `ops_user` already IS the operational projection of the identity; extend it with `community_customer_id` rather than a new table |

Principle: **reference + captured snapshot** over **full mutable duplication**. A
high-volume operational queue may justify a read projection; a one-off lookup should be
a REST call.

---

## 15. Database boundaries

- Dev: **separate H2 files** (`manafycommunity`, `manafyops`). **UNKNOWN:** prod
  topology (same instance/different schemas vs different instances) — not in committed
  config.
- **Hard rule:** neither service may read/write the other's tables or share JPA
  repositories/entities. No cross-service table access. Cross-service data flows only
  through published APIs/events.
- Each service keeps its own Flyway history and `ddl-auto=validate`.

---

## 16. Data ownership matrix

Legend: **Owner** = system of record; **Vis** = visibility (R=read via API, N=none,
own=native). "Sync" = how the other side sees it.

| Concept | Owner | DB / table | API owner | Ops visibility | Community visibility | Sync method |
|---|---|---|---|---|---|---|
| Authentication (OTP/login) | **Community** (target: Cognito) | community `otp_request` (legacy) | Community `/auth/*` | N (validates JWT only) | own | Shared Cognito pool |
| Cognito pool / JWT issuance | **Cognito (AWS)** | — | AWS | validate | validate | Shared pool |
| Canonical person identity | **Community** `customer` | community `customer` | Community | R (by sub) | own | sub + REST |
| Ops operational user | **Ops** `ops_user` (projection of identity) | ops `ops_user` | Ops | own | N | keyed by sub |
| Role / Permission (Community) | Community | community authz tables | Community | N | own | — |
| Role / Permission (Ops) | Ops | ops authz tables | Ops | own | N | — |
| Region | **Ops** (operational geography) | ops `region` | Ops | own | N (or R) | REST if needed |
| Area | **DUPLICATED** (community `area` + ops `area`) | both | both | own | own | **NEEDS DECISION (§21)** |
| Apartment | **DUPLICATED** (community + ops `apartment`) | both | both | own | own | **NEEDS DECISION (§21)** |
| Building / Unit (flat) | Community `apartment_flat`; Ops building/unit | both | both | own | own | overlap — decision |
| Facility / Amenity | Community `amenity`; Ops facility | both | both | own | own | overlap — decision |
| Apartment membership (resident) | **Community** `resident` | community | Community | R (via API/projection) | own | REST / projection |
| Household | **Community** `household`/`household_member` | community | Community | N | own | — |
| Amenity booking | **Community** | community `amenity_booking` | Community | N | own | — |
| Service booking | **Community** | community `service_booking` | Community | R (origin ref) | own | REST → Ops SR |
| Work order (legacy fulfillment) | **Community (retire)** | community `work_order` | Community | — | own | migrate to Ops SR |
| Service catalog | **Community** | community `service_catalog_item` | Community | R | own | REST |
| Service Request (fulfillment) | **Ops** | ops `service_request` | Ops | own | R (status) | REST callback |
| Assignment | **Ops** | ops `assignment` | Ops | own | N | — |
| Field Visit | **Ops** | ops `field_visit` | Ops | own | N | — |
| Technician / Helper (workforce) | **Ops** | ops workforce tables | Ops | own | R (name/contact) | REST |
| Vendor | **DUPLICATED** (community `vendor` + ops `vendor`) | both | both | own | own | decision (§21) |
| Field Officer | **Ops** | ops `area_field_officer` | Ops | own | N | — |
| Invoice / Payment / Payout | **Community (today)**; Finance phase deferred | community finance tables | Community | N | own | future |
| Notification / device token | **Community** | community `device_token` | Community | N | own | future events |
| Audit | per-service | each own | each | own | own | — |

---

## 17. `ops_user` reconciliation

**Current model:** independent identity (`cognito_sub`, email, mobile, display_name,
status, is_super_admin) — a second person record.

**Recommended model (projection, not identity):**
```
ops_user
--------
id
cognito_sub            (identity link — shared with Community)
community_customer_id  (NEW — link to canonical person; nullable during migration)
display_name           (operational cache; canonical name lives on customer)
operational_status     (ACTIVE | DISABLED)
is_super_admin
last_login_at
created_at / updated_at / version
```
`ops_user` **stays** (keep Option A: operational projection). It should **not** be the
source of truth for the person's name/contact — those live on `customer`. Add
`community_customer_id` to link them; keep `cognito_sub` as the join key when both
resolve the same pool.

- **Migration impact:** additive column `community_customer_id` (nullable) — no data
  loss; backfilled when the shared pool goes live and subs align.
- **Backward compatibility:** existing Ops tests use `cognito_sub` directly; adding a
  nullable link column is non-breaking.
- **Test impact:** minimal; existing `AuthzFixtures.createUserWithSub` unaffected.

**Do NOT delete `ops_user`.** Removing it would collapse authentication and
authorization and force Ops to depend on Community's DB (violates §15).

---

## 18. API contract reconciliation

| Aspect | Community | Ops | Action |
|---|---|---|---|
| Envelope | `ApiResponse` (own class) | `ApiResponse` (own class, flat `{success,errorCode,message,errorId,data}`) | Keep one **shared external shape**; they already look alike (Ops reused the Community style). Document the canonical shape |
| Errors | `BusinessException` + `GlobalExceptionHandler` | same names, own copy | Align error-code registry; keep per-service classes (no shared jar yet) |
| Pagination | mixed (older controllers vary) | `PageResponse` `{data, meta{page,pageSize,total}}` | Adopt Ops `PageResponse` shape as the standard |
| Auth header | Bearer (legacy self-JWT today) | Bearer (Cognito) | Converge on Cognito bearer |
| Idempotency | not observed in legacy controllers | `Idempotency-Key` header | Standardize on Ops's mechanism for mutations |
| Correlation id | **UNKNOWN** (not confirmed) | error `errorId` | Verify/standardize a request correlation id |
| IDs / timestamps | UUID + `BaseEntity` timestamps | UUID + `BaseEntity` | Compatible |

Incompatibilities are **cosmetic/convention-level**, not structural — the Ops
conventions (flat envelope, PageResponse, Idempotency-Key) are the cleaner target.

---

## 19. Security considerations

- **Never let Ops trust Community blindly** — each service validates the JWT
  independently against the shared pool (§6). Ops must not accept a "Community says
  this user is X" header as authorization.
- **No shared DB access** (§15) — a compromise or bug in one service must not be able
  to mutate the other's tables.
- **Legacy self-signed JWT is a liability**: `jwt.secret` has a dev default; the legacy
  path must be retired once Cognito is live (Community's own config says so). Until
  then, the two token formats are incompatible — a legacy Community token will **not**
  validate at Ops.
- **PII/tenant isolation**: resident data (households, contacts) stays in Community; Ops
  should receive only the minimum (apartment/area, contact for a visit) via API.
- **Authorization stays DB-resolved** in both — Cognito groups are not trusted for
  authz.

---

## 20. Recommended target architecture

```
                         Amazon Cognito (ONE user pool, app client per app)
                                     │  issues JWT (sub = immutable identity)
                 ┌───────────────────┴────────────────────┐
                 ▼                                          ▼
        ManafyCommunitySvcRst                        ManafyOpsSvcRst
   (residents, bookings, society)               (admin/ops, workforce, dispatch)
   owns: customer, resident, apartment*,         owns: ops_user(projection),
   amenity/service booking, complaints,          service_request, assignment,
   service catalog, finance, notifications       field_visit, workforce, field officer
                 │                                          ▲
                 │  service_booking / work_order needs fulfilment
                 └───────────────  REST  ───────────────────┘
                        POST /service-requests {communityBookingId, apartmentRef, category…}
                 ▲                                          │
                 └───────  REST callback (status)  ─────────┘
                        request COMPLETED → booking COMPLETED

Rules:
- ONE Cognito pool; each service validates JWT independently.
- customer = canonical person; ops_user = operational projection (same sub).
- No cross-service DB access; integrate via REST now, outbox/events later.
- Ops Service Request/Assignment is THE fulfilment workflow; Community work_order retired.
- (*) Apartment ownership pending owner decision (§21).
```

---

## 21. Items requiring owner approval

1. **Cognito pool topology.** Confirm a single shared User Pool with a per-app app
   client (recommended), and provide `COGNITO_ISSUER_URI` / pool / client ids. Align
   Ops to also accept `token_use=id` (or standardize on `access`).
2. **Legacy OTP retirement plan.** When does Community cut over from legacy self-JWT to
   Cognito? Until then Community and Ops tokens are incompatible.
3. **Apartment/Area ownership (the big one).** Choose:
   - **Option 1 — Ops owns the operational apartment master; Community keeps its
     resident-facing apartment; link by a shared external id.** (Least migration now;
     accepts a controlled duplication with a sync key.)
   - **Option 2 — One apartment source of truth (Community) + Ops read projection.**
     (Cleaner long-term; larger migration; Ops Phase 2 apartment becomes a projection.)
   - **Option 3 — Ops owns apartments, Community consumes via API.** (Inverts current
     Community ownership; largest change to Community.)
   Recommendation: **Option 1 for MVP**, migrate toward Option 2 later.
4. **`work_order` vs Ops `service_request`.** Approve retiring Community `work_order` in
   favor of Ops fulfillment (with a migration/bridge), or keep both temporarily with a
   documented boundary.
5. **Vendor ownership.** Community `vendor` vs Ops `vendor` — pick one owner (Ops owns
   workforce/dispatch vendors; Community's may be society-contract vendors — confirm
   they're genuinely the same concept before merging).
6. **Booking→SR direction.** Confirm "Community pushes to Ops via REST" (recommended)
   vs "Ops pulls."
7. **`community_customer_id` on `ops_user`.** Approve the additive link column.
8. **Finance ownership.** Confirm finance/invoicing stays in Community for now (Ops
   Finance is deferred).
9. **Prod DB topology (UNKNOWN).** Confirm separate databases (recommended) and that no
   shared-schema shortcut is planned.

---

## Appendix — UNKNOWNs requiring further inspection

- Exact physical table list created by Community `V1__baseline_existing_schema.sql`
  (the entity set was read and is authoritative for ownership; the raw baseline SQL was
  not line-read).
- Whether a real Cognito User Pool/app client currently exists (both configs blank).
- Production database topology (same instance/schema separation vs separate instances).
- Presence/shape of a correlation-id convention in Community responses.
- Whether Community `vendor`/`helper` are the same real-world entities as Ops
  `vendor`/`helper` or society-specific variants (names match; semantics unconfirmed).


---

## 22. Authentication Reconciliation (verified + implemented)

This section supersedes any earlier wording that implied MSG91 is an authentication
provider. It reflects the **actual source** of both services plus Community's own
`docs/architecture/SESSION_CONTEXT_MSG91_COGNITO_DEV.md`.

### 22.1 The authoritative model

**Production / QA**
```
Client
  |
  v
AWS Cognito (User Pool, CUSTOM_AUTH)      <-- sole authentication authority
  |   CreateAuthChallenge Lambda generates + stores the OTP,
  |   VerifyAuthChallenge Lambda checks it
  +-- OTP delivered via MSG91 (Flow/OneAPI v5)   <-- delivery pipe ONLY, inside the Lambda
  |
  v
Cognito JWT (sub = immutable identity)
  |
  +--------------------------+
  |                          |
  v                          v
ManafyCommunitySvcRst     ManafyOpsSvcRst
validates JWT             validates JWT
(resident/community APIs) (admin/ops APIs)
```

**Local development**
```
Client
  |
  v
Local/dev OTP convenience (Community only; env flag OTP_EXPOSE_IN_RESPONSE)
  |   Community self-generates the OTP and (dev only) echoes it in the response
  v
Community legacy OTP -> self-signed HS256 JWT (transitional; JwtUtil)
  |
  v
Community APIs

Ops has NO local OTP path. For local/test, Ops injects a Cognito-style principal
(test uses SecurityMockMvcRequestPostProcessors.jwt() with sub + token_use=access).
```

Key statements (all verified in source):
- **Cognito is the production authentication authority.**
- **MSG91 is the OTP delivery mechanism used by Cognito** (inside the Lambda), not a
  provider, not a JWT issuer.
- **Local OTP is a development/testing convenience only** and exists **only in
  Community**, gated by an env flag.
- **Ops does not own OTP**, does not integrate with MSG91, does not store OTPs, and
  does not issue authentication JWTs.
- **Both services use the Cognito `sub`** as the cross-service identity anchor
  (never mobile/email/name).
- **Ops RBAC is independent of authentication**; `ops_user` is an operational
  authorization projection.

### 22.2 Verified live Cognito facts (from Community session-context doc)

Region ap-south-1; DEV User Pool `ap-south-1_wQ82y39Fl`; public app client
`6gkqp04c477lt6rbq4viplor6c` (no secret; `ALLOW_CUSTOM_AUTH`); issuer
`https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_wQ82y39Fl`. OTP is generated
and verified by the Cognito Lambda triggers; MSG91 only delivers the SMS. **Phone
claims (`phone_number`, `phone_number_verified`) appear only on the ID token, not the
access token** — both services therefore accept `token_use ∈ {access, id}`.

### 22.3 Where MSG91 actually lives

- **Not in Ops.** Confirmed by inspection — no MSG91 class/config anywhere in Ops.
- **Not in Community's Spring app either.** MSG91 lives in the **Cognito
  CreateAuthChallenge Lambda** (`deployment/aws/cognito/lambda/...`), documented in
  Community's session-context doc.
- A separate, unrelated legacy project (`ManafySvcRst`) contains a `Msg91SmsSender`;
  it is **out of scope** and must not be conflated with these two services.

### 22.4 Community authentication (current, unchanged by this task)

Dual, mid-migration: (1) legacy OTP (`otp_request`) → self-signed HS256 JWT
(`JwtUtil`, `JwtAuthFilter`), kept ON transitionally; (2) Cognito resource-server
decoder (wired only when `manafy.cognito.issuer-uri` is set). Community owns the
client-facing `/api/v1/auth/send-otp` and `/verify-otp` contracts — **preserved, not
modified**. Migration target: retire legacy self-JWT once Cognito is validated in
staging (Community's own config comments state this).

### 22.5 Ops authentication (already the target; no auth-flow change)

Ops was already exactly the intended design and needed **no authentication code
change**:
- `SecurityConfig` wires a Cognito resource-server `NimbusJwtDecoder` only when
  `manafy.cognito.issuer-uri` is set; validators = default (issuer + expiry + nbf) +
  `CognitoAudienceValidator` (matches app client via `client_id` or `aud`) +
  `CognitoTokenUseValidator` (`access|id`). Identical to Community's validation.
- `CognitoJwtAuthenticationConverter` derives **no authorities** from the token.
- `CurrentUserService` resolves `sub` → `ops_user`; unknown sub is provisioned
  **unprivileged** (no roles, `ACTIVE`, never super-admin); status is **fail-closed**
  (`ACTIVE` ok; `DISABLED` → 403 `AUTH_DISABLED`; unknown → 403 `AUTH_STATUS_DENIED`).
- Ops never calls Community or MSG91 to validate a token; it validates the JWT itself.

### 22.6 Identity mapping + `ops_user` (implemented)

`ops_user` stays as the operational authorization **projection**, not a second
identity:
```
Cognito sub
   ├── ManafyCommunitySvcRst  customer.external_identity_id  (canonical person)
   └── ManafyOpsSvcRst        ops_user.cognito_sub           (operational projection)
                              ops_user.community_customer_id  (NEW — optional reference)
```
- **Implemented:** added nullable `ops_user.community_customer_id` (entity +
  migration `V11`) as a **projection reference** to Community `customer.id`.
- **No cross-service foreign key** (the two services own separate databases; §15).
  It is a plain nullable UUID + a lookup index, populated out-of-band once the shared
  pool is live and subs align.
- Ops is **never** the source of truth for customer identity.

### 22.7 Configuration (existing property names reused; nothing new required in Ops)

Both services already use `manafy.cognito.{region,user-pool-id,app-client-id,
issuer-uri}` (env: `AWS_REGION`, `COGNITO_USER_POOL_ID`, `COGNITO_APP_CLIENT_ID`,
`COGNITO_ISSUER_URI`). Ops adds **no** OTP/local-auth property (it has no OTP surface
to gate — the safeguard is structural). Community's local-dev OTP is gated by
`OTP_EXPOSE_IN_RESPONSE` (must be `false` in every deployed environment). No secrets
(Cognito, MSG91, JWT) are committed.

### 22.8 Backward compatibility

- Ops: only additive changes (nullable column + migration + tests). No behavior
  change to authentication. Existing `AuthenticationTest`, `AuthorizationHttpIT`, and
  the injected-principal test path are unaffected.
- Community: **unchanged** (0 files modified; 34 tests remain green).

### 22.9 Owner decisions still open (auth-specific)

1. **Point Ops at the shared pool:** set `COGNITO_ISSUER_URI` (+ pool/app-client) in
   Ops per environment so it validates the same pool's JWTs. (Config only.)
2. **Legacy retirement:** when does Community cut over from legacy self-JWT to
   Cognito? Until then, Community legacy tokens are not valid at Ops (by design).
3. **`OTP_EXPOSE_IN_RESPONSE` default:** Community's committed `application.yml` has
   the active line defaulting to `true` (a `false` variant is commented out) — its own
   session-context doc says it should be `false`. Recommend flipping the default to
   `false` in a Community-scoped change (outside this Ops task).
4. **Token strategy (access vs id):** phone linking needs the ID token; both services
   currently accept both. Confirm the intended client token for each app.
5. **Backfill `ops_user.community_customer_id`:** define the out-of-band process to
   populate it once the shared pool is live.
