-- ------------------------- info -------------------------
-- @@ver: 1_000_006
-- @@info: sync flowlong to 1.2.7
-- ------------------------- info -------------------------

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'flw_task'
    ) THEN
        ALTER TABLE "flw_task" ADD COLUMN IF NOT EXISTS "urgent" int2 NOT NULL DEFAULT 0;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'flw_his_instance'
    ) THEN
        ALTER TABLE "flw_his_instance" ADD COLUMN IF NOT EXISTS "urgent" int2 NOT NULL DEFAULT 0;
        COMMENT ON COLUMN "flw_his_instance"."urgent" IS '任务紧急程度 0，常规 1，重要不紧急 2，紧急不重要 3，紧急且重要';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'flw_instance'
    ) THEN
        ALTER TABLE "flw_instance" ADD COLUMN IF NOT EXISTS "urgent" int2 NOT NULL DEFAULT 0;
        COMMENT ON COLUMN "flw_instance"."urgent" IS '任务紧急程度 0，常规 1，重要不紧急 2，紧急不重要 3，紧急且重要';
    END IF;
END
$$;
