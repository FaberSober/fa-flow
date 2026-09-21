-- ------------------------- info -------------------------
-- @@ver: 1_000_006
-- @@info: sync flowlong to 1.2.7
-- ------------------------- info -------------------------

SET @fa_flowlong_his_instance_urgent_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_his_instance'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_his_instance'
              AND COLUMN_NAME = 'urgent'
        ),
        'ALTER TABLE `flw_his_instance` ADD COLUMN `urgent` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''任务紧急程度 0，常规 1，重要不紧急 2，紧急不重要 3，紧急且重要'' AFTER `last_update_time`',
        'SELECT 1'
    )
);
PREPARE fa_flowlong_his_instance_urgent_stmt FROM @fa_flowlong_his_instance_urgent_sql;
EXECUTE fa_flowlong_his_instance_urgent_stmt;
DEALLOCATE PREPARE fa_flowlong_his_instance_urgent_stmt;

SET @fa_flowlong_instance_urgent_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_instance'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_instance'
              AND COLUMN_NAME = 'urgent'
        ),
        'ALTER TABLE `flw_instance` ADD COLUMN `urgent` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''任务紧急程度 0，常规 1，重要不紧急 2，紧急不重要 3，紧急且重要'' AFTER `last_update_time`',
        'SELECT 1'
    )
);
PREPARE fa_flowlong_instance_urgent_stmt FROM @fa_flowlong_instance_urgent_sql;
EXECUTE fa_flowlong_instance_urgent_stmt;
DEALLOCATE PREPARE fa_flowlong_instance_urgent_stmt;
