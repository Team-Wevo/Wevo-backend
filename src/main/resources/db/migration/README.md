# Database Migrations

Flyway SQL migrations live in this directory.

Use the naming pattern:

```text
V{version}__{description}.sql
```

Examples:

```text
V1__init_schema.sql
V2__add_project_status.sql
```

Do not edit an already-applied migration. Add a new versioned migration instead.

`V1__init_schema.sql` is the MVP baseline reviewed in issue #88. The `local` profile uses
`ddl-auto: validate`, so Flyway creates the schema and Hibernate only checks that entity
mappings still match it. See `SCHEMA_FREEZE.md` for the ERD/entity comparison and review
status.

`V2__add_synthesis_issue_foundation.sql` adds immutable synthesis result sets, issue follow-up
storage, inherited GAP answer references, and the opinion-gate generation used by synthesis
snapshot binding. It intentionally leaves V1 unchanged. The generation CHECK is added as
`NOT VALID`, then `V3__validate_opinion_gate_generation_constraint.sql` validates existing rows
after Flyway commits V2 so validation does not run while V2's stronger table lock is held.

`V4__add_ai_jobs_section_feature_indexes.sql` indexes the two lookups behind `GET` synthesis
(latest execution, and latest successful execution per section and feature). It adds indexes only —
no table, column, or constraint changes — so JPA mapping validation is unaffected.

`V5__expand_issue_decision_custom_input.sql` changes `issue_decisions.custom_input` from
`VARCHAR(200)` to `TEXT`. The API and domain enforce a maximum of 200 non-whitespace UTF-16
characters while preserving the submitted whitespace, so the database column cannot retain a
raw-length limit of 200.

`V6__add_synthesis_consensus_evidence.sql` stores the minimal submitted-opinion evidence set
behind each synthesis consensus. It preserves author/name/excerpt snapshots with deterministic
ordering so later opinion edits cannot rewrite the evidence history.

`V7__add_ai_section_draft_evidence.sql` adds the draft source discriminator and immutable
AI-generation evidence aggregate. It links each generated draft to its source job, synthesis set,
input snapshot, and ordered opinion/decision/GAP/prerequisite evidence.

`V8__add_ai_section_checks.sql` stores successful section precheck results. A result is linked to
its source job and checked draft, keeps ordered findings and direct-prerequisite version snapshots,
and records one-time full-rewrite application/rebinding metadata.

## Existing local databases

V1 is an initial migration for an **empty PostgreSQL database**. A database previously
created by the temporary `ddl-auto: update` configuration has no Flyway history and must
not be silently baselined. Back up any data that matters, then recreate the local volume:

```powershell
docker compose down -v
docker compose up -d
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

The reset is intentionally explicit: it proves that the complete versioned migration chain can
construct the current schema instead of accepting an unknown Hibernate-generated starting point.

## PostgreSQL verification

Database behavior tests use Testcontainers with PostgreSQL 16. Running the normal test
task starts an isolated PostgreSQL container, applies Flyway migrations, validates the JPA
mappings, and rolls transactional test data back. Docker must be running locally.

```powershell
.\gradlew.bat test
```

`postgresSchemaTest` is the CI service-container gate. It applies every versioned migration to a
caller-provided empty PostgreSQL database, starts the application with `ddl-auto: validate`, and
verifies the current schema and its major constraints. The GitHub Actions workflow supplies these
values with dedicated, non-production credentials.

```powershell
$env:WEVO_POSTGRES_SCHEMA_TEST='true'
$env:WEVO_POSTGRES_JDBC_URL='jdbc:postgresql://localhost:55432/wevo_schema_test'
$env:WEVO_POSTGRES_USERNAME='wevo_schema_test'
$env:WEVO_POSTGRES_PASSWORD='local-schema-test'
.\gradlew.bat postgresSchemaTest
```

The database must be empty for every run. This task does not silently reuse, clean, or
baseline a developer database. GitHub Actions creates a fresh PostgreSQL service container
for each job, while Testcontainers provides isolated PostgreSQL for the regular integration
test suite.

## Opinion resubmission model

The baseline creates `opinions.submitted_content` and status-dependent checks for both
`submitted_content` and `submitted_at` directly. Because V1 targets an empty database, no
data backfill statement belongs in this migration. Any future production data transition
must be implemented in a new versioned migration rather than by editing V1.
