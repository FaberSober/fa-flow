-- ------------------------- info -------------------------
-- @@ver: 1_000_004
-- @@info: sync flowlong to 1.2.6 and fix PostgreSQL viewed column type
-- ------------------------- info -------------------------

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'flw_his_task_actor'
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'flw_his_task_actor'
              AND column_name = 'extend'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'flw_his_task_actor'
              AND column_name = 'ext'
        ) THEN
            ALTER TABLE "flw_his_task_actor" RENAME COLUMN "extend" TO "ext";
        END IF;
        ALTER TABLE "flw_his_task_actor" DROP CONSTRAINT IF EXISTS "fk_his_task_actor_task_id";
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'flw_task_actor'
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'flw_task_actor'
              AND column_name = 'extend'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'flw_task_actor'
              AND column_name = 'ext'
        ) THEN
            ALTER TABLE "flw_task_actor" RENAME COLUMN "extend" TO "ext";
        END IF;
        ALTER TABLE "flw_task_actor" DROP CONSTRAINT IF EXISTS "fk_task_actor_task_id";
    END IF;
END
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'flw_task'
          AND column_name = 'viewed'
          AND data_type = 'boolean'
    ) THEN
        ALTER TABLE "flw_task" ALTER COLUMN "viewed" DROP DEFAULT;
        ALTER TABLE "flw_task"
            ALTER COLUMN "viewed" TYPE smallint
            USING CASE WHEN "viewed" THEN 1 ELSE 0 END;
        ALTER TABLE "flw_task" ALTER COLUMN "viewed" SET DEFAULT 0;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'flw_his_task'
          AND column_name = 'viewed'
          AND data_type = 'boolean'
    ) THEN
        ALTER TABLE "flw_his_task" ALTER COLUMN "viewed" DROP DEFAULT;
        ALTER TABLE "flw_his_task"
            ALTER COLUMN "viewed" TYPE smallint
            USING CASE WHEN "viewed" THEN 1 ELSE 0 END;
        ALTER TABLE "flw_his_task" ALTER COLUMN "viewed" SET DEFAULT 0;
    END IF;
END
$$;
