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

**V1 checklist — opinion resubmission model (issue #55):** V1 must cover the
`opinions.submitted_content` transition for any pre-existing data:

```sql
UPDATE opinions SET submitted_content = content
WHERE status = 'SUBMITTED' AND submitted_content IS NULL;

ALTER TABLE opinions ADD CONSTRAINT chk_opinions_submitted_content
CHECK (status <> 'SUBMITTED' OR submitted_content IS NOT NULL);
```

Until then the application backfills lazily (`Opinion.updateContent` fixes the submitted
copy before the first re-edit of a legacy row), so no data is misread in the meantime.
