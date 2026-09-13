-- ------------------------- info -------------------------
-- @@ver: 1_000_001
-- @@info: update flowlong to 1.2.1
-- ------------------------- info -------------------------

ALTER TABLE "flw_his_task_actor" DROP CONSTRAINT "fk_his_task_actor_task_id";

ALTER TABLE "flw_his_task_actor" RENAME COLUMN "extend" TO "ext";
COMMENT ON COLUMN "flw_his_task_actor"."ext" IS '扩展json';

ALTER TABLE "flw_task_actor" DROP CONSTRAINT "fk_task_actor_task_id";

ALTER TABLE "flw_task_actor" RENAME COLUMN "extend" TO "ext";
COMMENT ON COLUMN "flw_task_actor"."ext" IS '扩展json';

