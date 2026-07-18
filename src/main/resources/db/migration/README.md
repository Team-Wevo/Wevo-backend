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

The initial schema migration should be added after the current entity schema is reviewed by the domain owners.

**Transition status (temporary):** until `V1__init_schema.sql` lands, the `local` profile
uses `ddl-auto: update` to create the schema. When V1 is added, switch `ddl-auto` to
`validate` in `application-local.yml` — from that point Flyway is the only thing that
changes the schema (see `CLAUDE.md` §4).
