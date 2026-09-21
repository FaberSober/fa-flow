-- update flowlong to 1.2.6
-- FlowLong 1.2.6: rename actor extension columns and remove actor task foreign keys.

SET @fa_flowlong_his_task_actor_ext_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_his_task_actor'
              AND COLUMN_NAME = 'extend'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_his_task_actor'
              AND COLUMN_NAME = 'ext'
        ),
        'ALTER TABLE `flw_his_task_actor` CHANGE COLUMN `extend` `ext` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''扩展json''',
        'SELECT 1'
    )
);
PREPARE fa_flowlong_his_task_actor_ext_stmt FROM @fa_flowlong_his_task_actor_ext_sql;
EXECUTE fa_flowlong_his_task_actor_ext_stmt;
DEALLOCATE PREPARE fa_flowlong_his_task_actor_ext_stmt;

SET @fa_flowlong_his_task_actor_fk_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_his_task_actor'
              AND CONSTRAINT_NAME = 'fk_his_task_actor_task_id'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'ALTER TABLE `flw_his_task_actor` DROP FOREIGN KEY `fk_his_task_actor_task_id`',
        'SELECT 1'
    )
);
PREPARE fa_flowlong_his_task_actor_fk_stmt FROM @fa_flowlong_his_task_actor_fk_sql;
EXECUTE fa_flowlong_his_task_actor_fk_stmt;
DEALLOCATE PREPARE fa_flowlong_his_task_actor_fk_stmt;

SET @fa_flowlong_task_actor_ext_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_task_actor'
              AND COLUMN_NAME = 'extend'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_task_actor'
              AND COLUMN_NAME = 'ext'
        ),
        'ALTER TABLE `flw_task_actor` CHANGE COLUMN `extend` `ext` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT ''扩展json''',
        'SELECT 1'
    )
);
PREPARE fa_flowlong_task_actor_ext_stmt FROM @fa_flowlong_task_actor_ext_sql;
EXECUTE fa_flowlong_task_actor_ext_stmt;
DEALLOCATE PREPARE fa_flowlong_task_actor_ext_stmt;

SET @fa_flowlong_task_actor_fk_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flw_task_actor'
              AND CONSTRAINT_NAME = 'fk_task_actor_task_id'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'ALTER TABLE `flw_task_actor` DROP FOREIGN KEY `fk_task_actor_task_id`',
        'SELECT 1'
    )
);
PREPARE fa_flowlong_task_actor_fk_stmt FROM @fa_flowlong_task_actor_fk_sql;
EXECUTE fa_flowlong_task_actor_fk_stmt;
DEALLOCATE PREPARE fa_flowlong_task_actor_fk_stmt;

-- update flowlong to 1.2.7
-- FlowLong 1.2.7: add task urgency to process instances.

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
