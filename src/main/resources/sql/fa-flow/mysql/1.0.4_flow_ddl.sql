-- ------------------------- info -------------------------
-- @@ver: 1_000_004
-- @@info: sync flowlong to 1.2.6
-- ------------------------- info -------------------------

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
