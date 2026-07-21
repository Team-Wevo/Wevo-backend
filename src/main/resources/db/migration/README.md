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

## Existing local databases

V1 is an initial migration for an **empty PostgreSQL database**. A database previously
created by the temporary `ddl-auto: update` configuration has no Flyway history and must
not be silently baselined. Back up any data that matters, then recreate the local volume:

```powershell
docker compose down -v
docker compose up -d
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

The reset is intentionally explicit: it proves that V1 alone can construct the complete
schema instead of accepting an unknown Hibernate-generated starting point.

## Empty PostgreSQL verification

`postgresSchemaTest` applies V1 to a caller-provided empty PostgreSQL database, starts
the application with `ddl-auto: validate`, and verifies that all 18 entity tables exist.
It is opt-in so the normal unit-test task stays independent of external infrastructure.

```powershell
$env:WEVO_POSTGRES_SCHEMA_TEST='true'
$env:WEVO_POSTGRES_JDBC_URL='jdbc:postgresql://localhost:55432/wevo_schema_88'
$env:WEVO_POSTGRES_USERNAME='wevo_schema_88'
$env:WEVO_POSTGRES_PASSWORD='local-schema-test'
.\gradlew.bat postgresSchemaTest
```

The database must be empty for every run. Automated PostgreSQL provisioning in CI is a
follow-up task; this test does not silently reuse or clean a developer database.

## Opinion resubmission model

The baseline creates `opinions.submitted_content` and status-dependent checks for both
`submitted_content` and `submitted_at` directly. Because V1 targets an empty database, no
data backfill statement belongs in this migration. Any future production data transition
must be implemented in a new versioned migration rather than by editing V1.
