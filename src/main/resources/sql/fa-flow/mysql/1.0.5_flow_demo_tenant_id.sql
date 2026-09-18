-- ------------------------- info -------------------------
-- @@ver: 1_000_005
-- @@info: 统一流程 Demo 租户ID字段类型
-- ------------------------- info -------------------------

ALTER TABLE `demo_flow_leave`
    MODIFY COLUMN `tenant_id` varchar(32) NULL COMMENT '租户ID';
