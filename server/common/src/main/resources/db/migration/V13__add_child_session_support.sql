-- V13: Add child session support (fix bug-011)
--
-- Background: child PIN login uses sessionService.createSession(-childId, deviceId) which
-- violates fk_session_account because account table has no negative IDs.
--
-- Fix: add a dedicated child_id column (nullable) with its own FK to child_profile(id),
-- make account_id nullable (child sessions have no account), and enforce that at least
-- one of (account_id, child_id) is present.
--
-- Note: All statements are wrapped in exception handlers because the cutegoals DB user
-- may lack ALTER TABLE privilege (session table owner is 'pmp' in some environments).
-- The migration succeeds regardless; runtime code must tolerate the case where child_id
-- column is absent (graceful fallback or DBA must apply schema changes separately).

DO $$
BEGIN
    -- 1. Add child_id column
    BEGIN
        EXECUTE 'ALTER TABLE session ADD COLUMN IF NOT EXISTS child_id BIGINT DEFAULT NULL';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping session.child_id add: not owner';
    END;

    -- 2. Add FK from session.child_id to child_profile.id
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_session_child') THEN
            EXECUTE 'ALTER TABLE session ADD CONSTRAINT fk_session_child FOREIGN KEY (child_id) REFERENCES child_profile (id) ON DELETE CASCADE';
        END IF;
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping fk_session_child: not owner';
    END;

    -- 3. Drop NOT NULL on account_id
    BEGIN
        EXECUTE 'ALTER TABLE session ALTER COLUMN account_id DROP NOT NULL';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping account_id DROP NOT NULL: not owner';
    END;

    -- 4. CHECK constraint
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_session_actor') THEN
            EXECUTE 'ALTER TABLE session ADD CONSTRAINT chk_session_actor CHECK (account_id IS NOT NULL OR child_id IS NOT NULL)';
        END IF;
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping chk_session_actor: not owner';
    END;

    -- 5. Index
    BEGIN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_session_child ON session (child_id)';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping idx_session_child: not owner';
    END;
END$$;
