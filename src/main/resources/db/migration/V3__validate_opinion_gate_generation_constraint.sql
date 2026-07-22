-- V2 adds this constraint without scanning the existing table. Flyway commits V2 first, so this
-- validation uses PostgreSQL's weaker validation lock instead of retaining V2's ACCESS EXCLUSIVE lock.
ALTER TABLE project_sections
    VALIDATE CONSTRAINT chk_project_sections_opinion_gate_generation;
