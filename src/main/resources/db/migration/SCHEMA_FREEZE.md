# MVP Schema Freeze Candidate

> Issue: #88
> Entity baseline: `dev` at `bb965af`
> ERD inspected: 2026-07-20 (KST)
> ERD: <https://www.erdcloud.com/d/QvsLy6iJKCDuaiyfX>

This document records the review basis for `V1__init_schema.sql`. It is a freeze
**candidate** until the domain owners approve their tables in the PR. The schema becomes
the MVP baseline when that PR is approved and merged.

## Inventory

| Owner/domain | JPA entity | Table |
| --- | --- | --- |
| User/Auth | `User` | `users` |
| User/Auth | `AuthAccount` | `auth_accounts` |
| Project | `Project` | `projects` |
| Project | `ProjectMember` | `project_members` |
| Project | `InviteLink` | `invite_links` |
| Section | `SectionTemplate` | `section_templates` |
| Section | `TemplateDependency` | `template_dependencies` |
| Section | `ProjectSection` | `project_sections` |
| Section | `SectionLabel` | `section_labels` |
| Section | `SectionStatusHistory` | `section_status_histories` |
| Section | `SectionDraft` | `section_drafts` |
| Section | `DraftLease` | `draft_leases` |
| Opinion | `Opinion` | `opinions` |
| Review | `ReviewLink` | `review_links` |
| Review | `ReviewSubmission` | `review_submissions` |
| Review | `TeamReview` | `team_reviews` |
| AI | `AiJob` | `ai_jobs` |
| AI | `AiUsageLog` | `ai_usage_logs` |

The ERD and code both contain these 18 tables. The ERD's 32 non-identifying relationships
also match the entity relationships. Actor IDs that remain scalar in Java, such as
`draft_leases.holder_user_id`, still receive database foreign keys.

## Reconciliation decisions

| Topic | ERD/entity observation | V1 decision |
| --- | --- | --- |
| Enum storage | ERD exports a generic `ENUM`; JPA uses `EnumType.STRING` | Store as `VARCHAR` and add named value checks |
| Time | Entities use `LocalDateTime`; API contract is offset-free KST | Use `TIMESTAMP WITHOUT TIME ZONE` |
| Audit timestamps | ERD requires timestamps with `CURRENT_TIMESTAMP`; JPA auditing writes them | Keep non-null DB defaults and let JPA update `updated_at` |
| AI provider | `AiUsageLog.provider` is required in code but absent from the ERD | Include `ai_usage_logs.provider VARCHAR(30) NOT NULL` |
| Lease holder | ERD/code use `holder_user_id`; issue checklist abbreviates it as `holder_id` | Keep `holder_user_id`, require it, and reference `users(id)` |
| Lease cardinality | One reusable row per section | Unique `draft_leases.project_section_id` |
| Opinion resubmission | Submitted opinions require an immutable submitted copy | Add `submitted_content` and its status-dependent check |
| Overlay columns | ERD and current entity contain all three overlays | Persist `drift_status`, nullable `ai_check_status`, and `synthesis_stale` independently |
| Status-history version | ERD marks it required, but current status transitions explicitly have no draft version before drafting | Keep `section_status_histories.version` nullable |

## Unique constraints and indexes

Entity-declared unique constraints are preserved for auth accounts, draft versions,
leases, opinions, review links/submissions, team reviews, AI request IDs, and AI execution
sequences. V1 additionally enforces invariants implied by current services:

- one membership per `(project_id, user_id)`;
- one template key and order per result type;
- one template and section order per project;
- globally unique invite tokens;
- no duplicate template dependency type for the same pair;
- no duplicate label order within a section.

Indexes follow current repository lookups. In particular, the lease's unique section
index serves acquire/status/renew and pessimistic row locking. A separate `lease_until`
index is retained only for expiration scans. AI job and usage-log indexes mirror their
recovery and audit queries.

## Domain-owner review gate

- [ ] Auth/Project owner: `users`, `auth_accounts`, `projects`, `project_members`, `invite_links`
- [ ] Section owner: templates, dependencies, sections, labels, histories, drafts
- [ ] Opinion/Realtime owner: `opinions`, `draft_leases`
- [ ] Review/Export owner: review link, submission, and team review tables
- [ ] AI owner: `ai_jobs`, `ai_usage_logs`, persisted enum values and recovery indexes

Reviewers should verify column meaning, nullability, defaults, unique constraints, and
foreign-key ownership. Approval of the #88 PR records the schema freeze decision.

## Changes after freeze

Do not edit V1 after it is merged. Any schema change requires:

1. a new `Vn__description.sql` migration;
2. the matching entity change in the same PR;
3. domain-owner review when a product field, relation, or enum meaning changes;
4. empty-database migration and Hibernate `validate` verification;
5. a full `gradlew.bat test` run.
