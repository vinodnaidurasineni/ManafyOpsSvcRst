# PostgreSQL Validation — Runbook & Status

## Status: PENDING POSTGRES VALIDATION

A PostgreSQL development instance is **not available in the current environment**:
- no `psql` / `pg_isready` client on PATH,
- no service named `*postgres*`,
- nothing listening on TCP 5432,
- Docker CLI is installed but the **daemon is not running** (`docker ps` →
  "failed to connect to the docker API ... the daemon is running?").

Therefore PostgreSQL validation is **NOT claimed as passed**. The dialect-neutral
migration set is fully validated on **H2 2.2** (all 10 versioned migrations `V1`–`V10`
+ the repeatable `R__` seed apply, the seed is idempotent, and the full suite of
**176 tests** is green — the Spring context boots with Flyway migrate + Hibernate
`ddl-auto=validate`, so the entities validate against the Flyway-built schema). The
steps below must be executed in a DEV environment with PostgreSQL before staging.

> Phase 4A added `V9` (service_request). Phase 4B added `V10` (assignment,
> assignment_status_history, field_visit + a `service_request.status` widening via
> `ALTER TABLE DROP/ADD CONSTRAINT`, supported by both H2 and PostgreSQL). No
> partial/filtered indexes are used (H2-incompatible). Everything below applies to
> `V1`–`V10` + `R__`.

> Phase 3 (Workforce & HR) added migration `V8__phase3_workforce_domain.sql`
> (11 workforce tables) and extended the repeatable seed `R__` with the Phase 3
> permission catalog + role mappings. Everything below applies to `V1`–`V8` + `R__`.

---

## What must still be verified on real PostgreSQL

1. All migrations (`V1`–`V8` + `R__`) apply to an **empty** database.
2. All PK / FK / UNIQUE / CHECK constraints and indexes are created.
3. The repeatable seed executes and is **idempotent** on a second run/restart.
4. The application starts with `ddl-auto=validate` (Hibernate validates the
   Flyway-built schema against the entities — catches type mismatches H2 hides).
5. `GET /api/v1/auth/me` works with a valid authentication context.

### Dialect points to confirm specifically (H2 accepted these; Postgres must too)
- `area.geo_boundary VARCHAR` (no length) → maps to Postgres `varchar` (unbounded) — OK, confirm.
- `audit_log.before_state / after_state VARCHAR(4000)` — OK on Postgres.
- All `CHECK (... IN (...))` constraints (status/scope_type/designation).
- `${uuid_fn}` placeholder must be set to `gen_random_uuid()` (needs `pgcrypto`
  extension) OR `gen_random_uuid()` built-in (Postgres 13+ has it in core).
- `TIMESTAMP` columns (no timezone) — acceptable for Phase 1; note that
  Artifact #1 specifies `TIMESTAMPTZ` (see discrepancy report, docs/05 §Schema).

---

## Exact commands / configuration

### Option A — Docker (start Docker Desktop first)
```powershell
# 1) Start a throwaway empty Postgres
docker run --name manafy-ops-pg -e POSTGRES_PASSWORD=ops -e POSTGRES_DB=manafyops `
  -p 5432:5432 -d postgres:16

# 2) Run the app against it (Flyway migrates + seeds on startup).
#    gen_random_uuid() is built into Postgres 13+ core, so uuid_fn works without pgcrypto.
$env:DB_URL="jdbc:postgresql://localhost:5432/manafyops"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="ops"
$env:DB_DRIVER="org.postgresql.Driver"
$env:DB_UUID_FN="gen_random_uuid()"
mvn spring-boot:run
```

### Option B — Maven Flyway-only migrate (no app), then inspect
```powershell
mvn flyway:migrate `
  "-Dflyway.url=jdbc:postgresql://localhost:5432/manafyops" `
  "-Dflyway.user=postgres" "-Dflyway.password=ops" `
  "-Dflyway.locations=classpath:db/migration" `
  "-Dflyway.placeholders.uuid_fn=gen_random_uuid()"
# Run it a SECOND time to prove idempotency (repeatable R__ re-runs, inserts nothing new).
```

### Option C — Integration test against Postgres via Testcontainers (recommended for CI)
Add (test scope) `org.testcontainers:postgresql` and a `@Testcontainers` test that
points Flyway at the container, then asserts table/constraint counts and re-runs the
seed. Not added now (would pull a new dependency + require Docker); documented as the
CI mechanism.

### Idempotency re-run check (SQL)
```sql
-- After two migrate runs, these counts must be unchanged between runs:
SELECT count(*) FROM role;            -- expect 10
SELECT count(*) FROM permission;      -- expect full catalog count
SELECT count(*) FROM role_permission; -- expect stable
SELECT count(*) FROM flyway_schema_history WHERE success = false; -- expect 0
```

### /auth/me smoke (with a valid Cognito token in a Cognito-configured env)
```powershell
curl -H "Authorization: Bearer <valid-cognito-jwt>" `
  http://localhost:8083/ManafyOpsSvcRst/api/v1/auth/me
```

---

## Sign-off checklist (to complete in DEV)
- [ ] `V1..V8` + `R__` applied to empty Postgres, `flyway_schema_history` all success
- [ ] Phase 3 workforce tables (technician, helper, vendor, vendor_staff, skill,
      certification, employee, workforce_skill, workforce_area, workforce_availability,
      workforce_document, workforce_status_history) present with all CHECK constraints
- [ ] Second run: no new seed rows; no migration errors
- [ ] `ddl-auto=validate` passes at startup (no schema/entity mismatch)
- [ ] `/api/v1/auth/me` returns the resolved principal
- [ ] Constraints/indexes present per `\d+` inspection (compare to docs/01)
