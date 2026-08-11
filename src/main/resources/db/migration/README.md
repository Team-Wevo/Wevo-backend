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

`V8__review_links_single_active.sql` adds a partial unique index
(`uk_review_links_active_per_section`) enforcing at most one `ACTIVE` external review link per
section. Link termination is server-driven (content edits mark the link `OUTDATED`; re-issue
closes the previous `ACTIVE` link), so the "at most one live link" invariant that the status
recovery lookup (`GET .../review-links/current`) relies on is now guaranteed at the database
level rather than by application logic alone. Before creating the index it first closes any
pre-existing duplicate `ACTIVE` rows per section (keeping the newest by `id`); without that
cleanup the unique index build would fail and abort the whole migration on any database that
already holds duplicates. It adds a partial index (plus a bounded data cleanup) only — no table,
column, or constraint changes — so JPA mapping validation is unaffected.

`V7__add_ai_section_draft_evidence.sql` adds the draft source discriminator and immutable
AI-generation evidence aggregate. It links each generated draft to its source job, synthesis set,
input snapshot, and ordered opinion/decision/GAP/prerequisite evidence.

`V9__add_ai_section_checks.sql` stores successful section precheck results. A result is linked to
its source job and checked draft, keeps ordered findings and direct-prerequisite version snapshots,
and records one-time full-rewrite application/rebinding metadata.

`V11__add_author_intent_review_comparison.sql` adds version-bound author intent history, immutable
author-intent snapshots on new external review links, and one comparison lifecycle per public
submission. Existing links deliberately keep nullable intent snapshots and remain historical;
the migration never invents or backfills an author intent. It also extends the AI job/usage feature
constraints for extraction and comparison.

`V13__add_review_submission_reviewer_comment.sql` adds the reviewer's optional free-form comment
to public review submissions. It is a separate input from the understanding sentence (`summary`)
and is always optional, so the column is nullable with no default and no backfill — existing rows
stay `NULL` because those submissions had no comment.

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

## Project flow review history

`V14__add_project_flow_review.sql` adds the project-scoped AI feature and immutable result history.
Each result stores the canonical input hash and checked section/version/content hashes. Findings
reference real project sections through foreign keys, while application semantic validation also
requires every excerpt to occur in the corresponding confirmed snapshot. No current/outdated flag
is stored as a source of truth; freshness is derived from the current canonical snapshot. The
expanded AI feature checks are added as `NOT VALID`; `V15__validate_project_flow_review_feature_constraints.sql`
validates existing AI history rows after V14 commits so the table scan does not retain V14's
stronger lock. Dedicated section-reference indexes support foreign-key checks from `project_sections`.

`V16__add_ai_rollout_snapshots.sql` adds the immutable rollout, reasoning, pricing, and guardrail
policy selection to AI jobs and copies the operational selection identifiers into usage logs. Legacy
rows receive explicit conservative snapshot values during migration; new rows must always provide
the full selection. Feature/rollout indexes support model rollout comparison without using project,
section, user, or request identifiers as metric labels.

`V18__add_review_link_expiry.sql` adds the optional external review link expiry date and the
`EXPIRED` terminal status. `expires_on` is a `DATE` because the expiry is agreed in whole days
(KST); `NULL` keeps the existing "no expiry" behaviour, so existing rows need no backfill. The
"at least one day after issue" rule is enforced in the service against KST rather than by a CHECK,
because `created_at` is written in the server JVM zone and a midnight-boundary request would
otherwise fail the constraint. The widened status CHECK is added as `NOT VALID`;
`V19__validate_review_link_status_constraint.sql` validates existing rows after V18 commits so the
scan does not retain V18's stronger lock. The new CHECK is a superset of the previous one, so no
existing row can violate it.

`V22__add_ai_input_budget_error_type.sql` extends the `ai_jobs.final_error_type` CHECK with
`INPUT_BUDGET_EXCEEDED`, allowing deterministic oversized-input failures to be stored separately
from internal failures. It adds the replacement CHECK as `NOT VALID`; V23 validates existing AI
job history in a separate migration so the validation scan does not retain V22's stronger lock.
