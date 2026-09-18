-- ------------------------- info -------------------------
-- @@ver: 1_000_005
-- @@info: 统一流程 Demo 租户ID字段类型
-- ------------------------- info -------------------------

ALTER TABLE "demo_flow_leave"
    ALTER COLUMN "tenant_id" TYPE varchar(32)
    USING "tenant_id"::varchar(32);
