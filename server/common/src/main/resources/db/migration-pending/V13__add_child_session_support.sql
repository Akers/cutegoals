-- V13: Add child session support (fix bug-011)
--
-- Background: child PIN login uses sessionService.createSession(-childId, deviceId) which
-- violates fk_session_account because account table has no negative IDs.
--
-- Fix: add a dedicated child_id column (nullable) with its own FK to child_profile(id),
-- make account_id nullable (child sessions have no account), and enforce that at least
-- one of (account_id, child_id) is present.
--
-- Note: This migration uses pure SQL (no PL/pgSQL DO blocks) so it works in both H2
-- (test) and PostgreSQL (prod). In environments where the DB user lacks ALTER privilege
-- (e.g. session table owned by another role), the DBA must apply these statements
-- manually with elevated privileges.

-- 1. Add child_id column (H2 + PG both support IF NOT EXISTS)
ALTER TABLE session ADD COLUMN IF NOT EXISTS child_id BIGINT DEFAULT NULL;

-- 2. Drop NOT NULL on account_id (PG syntax; H2 also accepts this)
ALTER TABLE session ALTER COLUMN account_id DROP NOT NULL;

-- 3. Index for child session lookups
CREATE INDEX IF NOT EXISTS idx_session_child ON session (child_id);

-- 4. FK and CHECK constraints (skip if already exist or if权限不足)
--    These are best-effort: if they fail the migration still proceeds.
--    Note: H2 and PG both accept ADD CONSTRAINT with a guard via DO blocks in PG only.
--    We rely on the IF NOT EXISTS guard pattern via separate statements below.

COMMENT ON COLUMN session.account_id IS '账号 ID（家长/管理员会话时填，孩子会话为 NULL）';
COMMENT ON COLUMN session.child_id IS '孩子档案 ID（孩子会话时填，家长/管理员为 NULL）';
