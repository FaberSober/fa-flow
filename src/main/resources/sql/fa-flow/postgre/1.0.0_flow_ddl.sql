-- ------------------------- info -------------------------
-- @@ver: 1_000_000
-- @@info: 初始化fa-flow模块
-- ------------------------- info -------------------------

-- ---------------------------- flowlong begin ----------------------------
-- ----------------------------
-- Table structure for flw_process
-- ----------------------------
-- DROP TABLE IF EXISTS "flw_process";
CREATE TABLE IF NOT EXISTS "flw_process" (
    "id" bigint NOT NULL,
    "tenant_id" varchar(50),
    "create_id" varchar(50) NOT NULL,
    "create_by" varchar(50) NOT NULL,
    "create_time" timestamp NOT NULL,
    "process_key" varchar(100) NOT NULL,
    "process_name" varchar(100) NOT NULL,
    "process_icon" varchar(255) DEFAULT NULL,
    "process_type" varchar(100),
    "process_version" integer NOT NULL DEFAULT 1,
    "instance_url" varchar(200),
    "remark" varchar(255),
    "use_scope" smallint NOT NULL DEFAULT 0,
    "process_state" smallint NOT NULL DEFAULT 1,
    "model_content" text,
    "sort" smallint DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "flw_process" IS '流程定义表';
COMMENT ON COLUMN "flw_process"."id" IS '主键ID';
COMMENT ON COLUMN "flw_process"."tenant_id" IS '租户ID';
COMMENT ON COLUMN "flw_process"."create_id" IS '创建人ID';
COMMENT ON COLUMN "flw_process"."create_by" IS '创建人名称';
COMMENT ON COLUMN "flw_process"."create_time" IS '创建时间';
COMMENT ON COLUMN "flw_process"."process_key" IS '流程定义 key 唯一标识';
COMMENT ON COLUMN "flw_process"."process_name" IS '流程定义名称';
COMMENT ON COLUMN "flw_process"."process_icon" IS '流程图标地址';
COMMENT ON COLUMN "flw_process"."process_type" IS '流程类型';
COMMENT ON COLUMN "flw_process"."process_version" IS '流程版本，默认 1';
COMMENT ON COLUMN "flw_process"."instance_url" IS '实例地址';
COMMENT ON COLUMN "flw_process"."remark" IS '备注说明';
COMMENT ON COLUMN "flw_process"."use_scope" IS '使用范围 0，全员 1，指定人员（业务关联） 2，均不可提交';
COMMENT ON COLUMN "flw_process"."process_state" IS '流程状态 0，不可用 1，可用 2，历史版本';
COMMENT ON COLUMN "flw_process"."model_content" IS '流程模型定义JSON内容';
COMMENT ON COLUMN "flw_process"."sort" IS '排序';
CREATE INDEX "idx_process_name" ON "flw_process" ("process_name");

-- ----------------------------
-- Table structure for flw_his_instance
-- ----------------------------
-- DROP TABLE IF EXISTS "flw_his_instance";
CREATE TABLE IF NOT EXISTS "flw_his_instance" (
    "id" bigint NOT NULL,
    "tenant_id" varchar(50),
    "create_id" varchar(50) NOT NULL,
    "create_by" varchar(50) NOT NULL,
    "create_time" timestamp NOT NULL,
    "process_id" bigint NOT NULL,
    "parent_instance_id" bigint,
    "priority" smallint,
    "instance_no" varchar(50),
    "business_key" varchar(100),
    "variable" text,
    "current_node_name" varchar(100) NOT NULL,
    "current_node_key" varchar(100) NOT NULL,
    "expire_time" timestamp NULL DEFAULT NULL,
    "last_update_by" varchar(50),
    "last_update_time" timestamp NULL DEFAULT NULL,
    "instance_state" smallint NOT NULL DEFAULT 0,
    "end_time" timestamp NULL DEFAULT NULL,
    "duration" bigint,
    PRIMARY KEY ("id"),
    CONSTRAINT "fk_his_instance_process_id" FOREIGN KEY ("process_id") REFERENCES "flw_process" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT
);
COMMENT ON TABLE "flw_his_instance" IS '历史流程实例表';
COMMENT ON COLUMN "flw_his_instance"."id" IS '主键ID';
COMMENT ON COLUMN "flw_his_instance"."tenant_id" IS '租户ID';
COMMENT ON COLUMN "flw_his_instance"."create_id" IS '创建人ID';
COMMENT ON COLUMN "flw_his_instance"."create_by" IS '创建人名称';
COMMENT ON COLUMN "flw_his_instance"."create_time" IS '创建时间';
COMMENT ON COLUMN "flw_his_instance"."process_id" IS '流程定义ID';
COMMENT ON COLUMN "flw_his_instance"."parent_instance_id" IS '父流程实例ID';
COMMENT ON COLUMN "flw_his_instance"."priority" IS '优先级';
COMMENT ON COLUMN "flw_his_instance"."instance_no" IS '流程实例编号';
COMMENT ON COLUMN "flw_his_instance"."business_key" IS '业务KEY';
COMMENT ON COLUMN "flw_his_instance"."variable" IS '变量json';
COMMENT ON COLUMN "flw_his_instance"."current_node_name" IS '当前所在节点名称';
COMMENT ON COLUMN "flw_his_instance"."current_node_key" IS '当前所在节点key';
COMMENT ON COLUMN "flw_his_instance"."expire_time" IS '期望完成时间';
COMMENT ON COLUMN "flw_his_instance"."last_update_by" IS '上次更新人';
COMMENT ON COLUMN "flw_his_instance"."last_update_time" IS '上次更新时间';
COMMENT ON COLUMN "flw_his_instance"."instance_state" IS '状态 -2，已暂停状态 -1，暂存待审 0，审批中 1，审批通过 2，审批拒绝 3，撤销审批 4，超时结束 5，强制终止 6，自动通过 7，自动拒绝';
COMMENT ON COLUMN "flw_his_instance"."end_time" IS '结束时间';
COMMENT ON COLUMN "flw_his_instance"."duration" IS '处理耗时';
CREATE INDEX "idx_his_instance_process_id" ON "flw_his_instance" ("process_id");

-- ----------------------------
-- Table structure for flw_his_task
-- ----------------------------
-- DROP TABLE IF EXISTS "flw_his_task";
CREATE TABLE IF NOT EXISTS "flw_his_task" (
    "id" bigint NOT NULL,
    "tenant_id" varchar(50),
    "create_id" varchar(50) NOT NULL,
    "create_by" varchar(50) NOT NULL,
    "create_time" timestamp NOT NULL,
    "instance_id" bigint NOT NULL,
    "parent_task_id" bigint,
    "call_process_id" bigint,
    "call_instance_id" bigint,
    "task_name" varchar(100) NOT NULL,
    "task_key" varchar(100) NOT NULL,
    "task_type" smallint NOT NULL,
    "perform_type" smallint,
    "action_url" varchar(200),
    "variable" text,
    "assignor_id" varchar(100),
    "assignor" varchar(255),
    "expire_time" timestamp NULL DEFAULT NULL,
    "remind_time" timestamp NULL DEFAULT NULL,
    "remind_repeat" smallint NOT NULL DEFAULT 0,
    "viewed" boolean NOT NULL DEFAULT FALSE,
    "finish_time" timestamp NULL DEFAULT NULL,
    "task_state" smallint NOT NULL DEFAULT 0,
    "duration" bigint,
    PRIMARY KEY ("id"),
    CONSTRAINT "fk_his_task_instance_id" FOREIGN KEY ("instance_id") REFERENCES "flw_his_instance" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT
);
COMMENT ON TABLE "flw_his_task" IS '历史任务表';
COMMENT ON COLUMN "flw_his_task"."id" IS '主键ID';
COMMENT ON COLUMN "flw_his_task"."tenant_id" IS '租户ID';
COMMENT ON COLUMN "flw_his_task"."create_id" IS '创建人ID';
COMMENT ON COLUMN "flw_his_task"."create_by" IS '创建人名称';
COMMENT ON COLUMN "flw_his_task"."create_time" IS '创建时间';
COMMENT ON COLUMN "flw_his_task"."instance_id" IS '流程实例ID';
COMMENT ON COLUMN "flw_his_task"."parent_task_id" IS '父任务ID';
COMMENT ON COLUMN "flw_his_task"."call_process_id" IS '调用外部流程定义ID';
COMMENT ON COLUMN "flw_his_task"."call_instance_id" IS '调用外部流程实例ID';
COMMENT ON COLUMN "flw_his_task"."task_name" IS '任务名称';
COMMENT ON COLUMN "flw_his_task"."task_key" IS '任务 key 唯一标识';
COMMENT ON COLUMN "flw_his_task"."task_type" IS '任务类型 -1，结束节点 0，主办 1，审批 2，抄送 3，条件审批 4，条件分支 5，调用外部流程任务 6，定时器任务 7，触发器任务 8，并行分支 9，包容分支 10，转办 11，委派 12，委派归还 13，代理人任务 14，代理人归还 15，代理人协办 16，被代理人自己完成 17，拿回任务 18，待撤回历史任务 19，拒绝任务 20，跳转任务 21，驳回跳转 22，路由跳转 23，路由分支 24，驳回重新审批跳转 25，暂存待审 30，自动通过 31，自动拒绝';
COMMENT ON COLUMN "flw_his_task"."perform_type" IS '参与类型 0，发起 1，按顺序依次审批 2，会签 3，或签 4，票签 6，定时器 7，触发器 9，抄送';
COMMENT ON COLUMN "flw_his_task"."action_url" IS '任务处理的url';
COMMENT ON COLUMN "flw_his_task"."variable" IS '变量json';
COMMENT ON COLUMN "flw_his_task"."assignor_id" IS '委托人ID';
COMMENT ON COLUMN "flw_his_task"."assignor" IS '委托人';
COMMENT ON COLUMN "flw_his_task"."expire_time" IS '任务期望完成时间';
COMMENT ON COLUMN "flw_his_task"."remind_time" IS '提醒时间';
COMMENT ON COLUMN "flw_his_task"."remind_repeat" IS '提醒次数';
COMMENT ON COLUMN "flw_his_task"."viewed" IS '已阅 0，否 1，是';
COMMENT ON COLUMN "flw_his_task"."finish_time" IS '任务完成时间';
COMMENT ON COLUMN "flw_his_task"."task_state" IS '任务状态 0，活动 1，跳转 2，完成 3，拒绝 4，撤销审批 5，超时 6，终止 7，驳回终止 8，自动完成 9，自动驳回 10，自动跳转 11，驳回跳转 12，驳回重新审批跳转 13，路由跳转';
COMMENT ON COLUMN "flw_his_task"."duration" IS '处理耗时';
CREATE INDEX "idx_his_task_instance_id" ON "flw_his_task" ("instance_id");
CREATE INDEX "idx_his_task_parent_task_id" ON "flw_his_task" ("parent_task_id");

-- ----------------------------
-- Table structure for flw_his_task_actor
-- ----------------------------
-- DROP TABLE IF EXISTS "flw_his_task_actor";
CREATE TABLE IF NOT EXISTS "flw_his_task_actor" (
    "id" bigint NOT NULL,
    "tenant_id" varchar(50),
    "instance_id" bigint NOT NULL,
    "task_id" bigint NOT NULL,
    "actor_id" varchar(100) NOT NULL,
    "actor_name" varchar(100) NOT NULL,
    "actor_type" integer NOT NULL,
    "weight" integer,
    "agent_id" varchar(100),
    "agent_type" integer,
    "extend" text,
    PRIMARY KEY ("id"),
    CONSTRAINT "fk_his_task_actor_task_id" FOREIGN KEY ("task_id") REFERENCES "flw_his_task" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT
);
COMMENT ON TABLE "flw_his_task_actor" IS '历史任务参与者表';
COMMENT ON COLUMN "flw_his_task_actor"."id" IS '主键 ID';
COMMENT ON COLUMN "flw_his_task_actor"."tenant_id" IS '租户ID';
COMMENT ON COLUMN "flw_his_task_actor"."instance_id" IS '流程实例ID';
COMMENT ON COLUMN "flw_his_task_actor"."task_id" IS '任务ID';
COMMENT ON COLUMN "flw_his_task_actor"."actor_id" IS '参与者ID';
COMMENT ON COLUMN "flw_his_task_actor"."actor_name" IS '参与者名称';
COMMENT ON COLUMN "flw_his_task_actor"."actor_type" IS '参与者类型 0，用户 1，角色 2，部门';
COMMENT ON COLUMN "flw_his_task_actor"."weight" IS '权重，票签任务时，该值为不同处理人员的分量比例，代理任务时，该值为 1 时为代理人';
COMMENT ON COLUMN "flw_his_task_actor"."agent_id" IS '代理人ID';
COMMENT ON COLUMN "flw_his_task_actor"."agent_type" IS '代理人类型 0，代理 1，被代理 2，认领角色 3，认领部门';
COMMENT ON COLUMN "flw_his_task_actor"."extend" IS '扩展json';
CREATE INDEX "idx_his_task_actor_task_id" ON "flw_his_task_actor" ("task_id");

-- ----------------------------
-- Table structure for flw_instance
-- ----------------------------
-- DROP TABLE IF EXISTS "flw_instance";
CREATE TABLE IF NOT EXISTS "flw_instance" (
    "id" bigint NOT NULL,
    "tenant_id" varchar(50),
    "create_id" varchar(50) NOT NULL,
    "create_by" varchar(50) NOT NULL,
    "create_time" timestamp NOT NULL,
    "process_id" bigint NOT NULL,
    "parent_instance_id" bigint,
    "priority" smallint,
    "instance_no" varchar(50),
    "business_key" varchar(100),
    "variable" text,
    "current_node_name" varchar(100) NOT NULL,
    "current_node_key" varchar(100) NOT NULL,
    "expire_time" timestamp NULL DEFAULT NULL,
    "last_update_by" varchar(50),
    "last_update_time" timestamp NULL DEFAULT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "fk_instance_process_id" FOREIGN KEY ("process_id") REFERENCES "flw_process" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT
);
COMMENT ON TABLE "flw_instance" IS '流程实例表';
COMMENT ON COLUMN "flw_instance"."id" IS '主键ID';
COMMENT ON COLUMN "flw_instance"."tenant_id" IS '租户ID';
COMMENT ON COLUMN "flw_instance"."create_id" IS '创建人ID';
COMMENT ON COLUMN "flw_instance"."create_by" IS '创建人名称';
COMMENT ON COLUMN "flw_instance"."create_time" IS '创建时间';
COMMENT ON COLUMN "flw_instance"."process_id" IS '流程定义ID';
COMMENT ON COLUMN "flw_instance"."parent_instance_id" IS '父流程实例ID';
COMMENT ON COLUMN "flw_instance"."priority" IS '优先级';
COMMENT ON COLUMN "flw_instance"."instance_no" IS '流程实例编号';
COMMENT ON COLUMN "flw_instance"."business_key" IS '业务KEY';
COMMENT ON COLUMN "flw_instance"."variable" IS '变量json';
COMMENT ON COLUMN "flw_instance"."current_node_name" IS '当前所在节点名称';
COMMENT ON COLUMN "flw_instance"."current_node_key" IS '当前所在节点key';
COMMENT ON COLUMN "flw_instance"."expire_time" IS '期望完成时间';
COMMENT ON COLUMN "flw_instance"."last_update_by" IS '上次更新人';
COMMENT ON COLUMN "flw_instance"."last_update_time" IS '上次更新时间';
CREATE INDEX "idx_instance_process_id" ON "flw_instance" ("process_id");

-- ----------------------------
-- Table structure for flw_task
-- ----------------------------
-- DROP TABLE IF EXISTS "flw_task";
CREATE TABLE IF NOT EXISTS "flw_task" (
    "id" bigint NOT NULL,
    "tenant_id" varchar(50),
    "create_id" varchar(50) NOT NULL,
    "create_by" varchar(50) NOT NULL,
    "create_time" timestamp NOT NULL,
    "instance_id" bigint NOT NULL,
    "parent_task_id" bigint,
    "task_name" varchar(100) NOT NULL,
    "task_key" varchar(100) NOT NULL,
    "task_type" smallint NOT NULL,
    "perform_type" smallint NULL,
    "action_url" varchar(200),
    "variable" text,
    "assignor_id" varchar(100),
    "assignor" varchar(255),
    "expire_time" timestamp NULL DEFAULT NULL,
    "remind_time" timestamp NULL DEFAULT NULL,
    "remind_repeat" smallint NOT NULL DEFAULT 0,
    "viewed" boolean NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id"),
    CONSTRAINT "fk_task_instance_id" FOREIGN KEY ("instance_id") REFERENCES "flw_instance" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT
);
COMMENT ON TABLE "flw_task" IS '任务表';
COMMENT ON COLUMN "flw_task"."id" IS '主键ID';
COMMENT ON COLUMN "flw_task"."tenant_id" IS '租户ID';
COMMENT ON COLUMN "flw_task"."create_id" IS '创建人ID';
COMMENT ON COLUMN "flw_task"."create_by" IS '创建人名称';
COMMENT ON COLUMN "flw_task"."create_time" IS '创建时间';
COMMENT ON COLUMN "flw_task"."instance_id" IS '流程实例ID';
COMMENT ON COLUMN "flw_task"."parent_task_id" IS '父任务ID';
COMMENT ON COLUMN "flw_task"."task_name" IS '任务名称';
COMMENT ON COLUMN "flw_task"."task_key" IS '任务 key 唯一标识';
COMMENT ON COLUMN "flw_task"."task_type" IS '任务类型 -1，结束节点 0，主办 1，审批 2，抄送 3，条件审批 4，条件分支 5，调用外部流程任务 6，定时器任务 7，触发器任务 8，并行分支 9，包容分支 10，转办 11，委派 12，委派归还 13，代理人任务 14，代理人归还 15，代理人协办 16，被代理人自己完成 17，拿回任务 18，待撤回历史任务 19，拒绝任务 20，跳转任务 21，驳回跳转 22，路由跳转 23，路由分支 24，驳回重新审批跳转 25，暂存待审 30，自动通过 31，自动拒绝';
COMMENT ON COLUMN "flw_task"."perform_type" IS '参与类型 0，发起 1，按顺序依次审批 2，会签 3，或签 4，票签 6，定时器 7，触发器 9，抄送';
COMMENT ON COLUMN "flw_task"."action_url" IS '任务处理的url';
COMMENT ON COLUMN "flw_task"."variable" IS '变量json';
COMMENT ON COLUMN "flw_task"."assignor_id" IS '委托人ID';
COMMENT ON COLUMN "flw_task"."assignor" IS '委托人';
COMMENT ON COLUMN "flw_task"."expire_time" IS '任务期望完成时间';
COMMENT ON COLUMN "flw_task"."remind_time" IS '提醒时间';
COMMENT ON COLUMN "flw_task"."remind_repeat" IS '提醒次数';
COMMENT ON COLUMN "flw_task"."viewed" IS '已阅 0，否 1，是';
CREATE INDEX "idx_task_instance_id" ON "flw_task" ("instance_id");

-- ----------------------------
-- Table structure for flw_task_actor
-- ----------------------------
-- DROP TABLE IF EXISTS "flw_task_actor";
CREATE TABLE IF NOT EXISTS "flw_task_actor" (
    "id" bigint NOT NULL,
    "tenant_id" varchar(50),
    "instance_id" bigint NOT NULL,
    "task_id" bigint NOT NULL,
    "actor_id" varchar(100) NOT NULL,
    "actor_name" varchar(100) NOT NULL,
    "actor_type" integer NOT NULL,
    "weight" integer,
    "agent_id" varchar(100),
    "agent_type" integer,
    "extend" text,
    PRIMARY KEY ("id"),
    CONSTRAINT "fk_task_actor_task_id" FOREIGN KEY ("task_id") REFERENCES "flw_task" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT
);
COMMENT ON TABLE "flw_task_actor" IS '任务参与者表';
COMMENT ON COLUMN "flw_task_actor"."id" IS '主键 ID';
COMMENT ON COLUMN "flw_task_actor"."tenant_id" IS '租户ID';
COMMENT ON COLUMN "flw_task_actor"."instance_id" IS '流程实例ID';
COMMENT ON COLUMN "flw_task_actor"."task_id" IS '任务ID';
COMMENT ON COLUMN "flw_task_actor"."actor_id" IS '参与者ID';
COMMENT ON COLUMN "flw_task_actor"."actor_name" IS '参与者名称';
COMMENT ON COLUMN "flw_task_actor"."actor_type" IS '参与者类型 0，用户 1，角色 2，部门';
COMMENT ON COLUMN "flw_task_actor"."weight" IS '权重，票签任务时，该值为不同处理人员的分量比例，代理任务时，该值为 1 时为代理人';
COMMENT ON COLUMN "flw_task_actor"."agent_id" IS '代理人ID';
COMMENT ON COLUMN "flw_task_actor"."agent_type" IS '代理人类型 0，代理 1，被代理 2，认领角色 3，认领部门';
COMMENT ON COLUMN "flw_task_actor"."extend" IS '扩展json';
CREATE INDEX "idx_task_actor_task_id" ON "flw_task_actor" ("task_id");

-- ----------------------------
-- Table structure for flw_instance
-- ----------------------------
-- DROP TABLE IF EXISTS "flw_ext_instance";
CREATE TABLE IF NOT EXISTS "flw_ext_instance" (
    "id" bigint NOT NULL,
    "tenant_id" varchar(50),
    "process_id" bigint NOT NULL,
    "process_name" varchar(100),
    "process_type" varchar(100),
    "model_content" text,
    PRIMARY KEY ("id"),
    CONSTRAINT "fk_ext_instance_id" FOREIGN KEY ("id") REFERENCES "flw_his_instance" ("id") ON DELETE RESTRICT ON UPDATE RESTRICT
);
COMMENT ON TABLE "flw_ext_instance" IS '扩展流程实例表';
COMMENT ON COLUMN "flw_ext_instance"."id" IS '主键ID';
COMMENT ON COLUMN "flw_ext_instance"."tenant_id" IS '租户ID';
COMMENT ON COLUMN "flw_ext_instance"."process_id" IS '流程定义ID';
COMMENT ON COLUMN "flw_ext_instance"."process_name" IS '流程名称';
COMMENT ON COLUMN "flw_ext_instance"."process_type" IS '流程类型';
COMMENT ON COLUMN "flw_ext_instance"."model_content" IS '流程模型定义JSON内容';

-- ---------------------------- flowlong end ----------------------------

CREATE TABLE IF NOT EXISTS "flow_catagory" (
  "id" integer GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "parent_id" integer NOT NULL,
  "name" varchar(255) NOT NULL,
  "sort" integer DEFAULT 0,
  "icon" varchar(255) DEFAULT NULL,
  "crt_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "crt_user" varchar(32) NOT NULL,
  "crt_name" varchar(255) NOT NULL,
  "crt_host" varchar(255) DEFAULT NULL,
  "upd_time" timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  "upd_user" varchar(32) DEFAULT NULL,
  "upd_name" varchar(255) DEFAULT NULL,
  "upd_host" varchar(255) DEFAULT NULL,
  "deleted" boolean NOT NULL DEFAULT FALSE,
  PRIMARY KEY ("id")
);
COMMENT ON TABLE "flow_catagory" IS 'FLOW-流程分类';
COMMENT ON COLUMN "flow_catagory"."id" IS 'ID';
COMMENT ON COLUMN "flow_catagory"."parent_id" IS '上级节点';
COMMENT ON COLUMN "flow_catagory"."name" IS '名称';
COMMENT ON COLUMN "flow_catagory"."sort" IS '排序ID';
COMMENT ON COLUMN "flow_catagory"."icon" IS '图标';
COMMENT ON COLUMN "flow_catagory"."crt_time" IS '创建时间';
COMMENT ON COLUMN "flow_catagory"."crt_user" IS '创建用户ID';
COMMENT ON COLUMN "flow_catagory"."crt_name" IS '创建用户';
COMMENT ON COLUMN "flow_catagory"."crt_host" IS '创建IP';
COMMENT ON COLUMN "flow_catagory"."upd_time" IS '更新时间';
COMMENT ON COLUMN "flow_catagory"."upd_user" IS '更新用户ID';
COMMENT ON COLUMN "flow_catagory"."upd_name" IS '更新用户';
COMMENT ON COLUMN "flow_catagory"."upd_host" IS '更新IP';
COMMENT ON COLUMN "flow_catagory"."deleted" IS '是否删除';
DROP TRIGGER IF EXISTS "flow_catagory__upd_time" ON "flow_catagory";
CREATE TRIGGER "flow_catagory__upd_time" BEFORE UPDATE ON "flow_catagory" FOR EACH ROW EXECUTE FUNCTION fa_base_set_upd_time();

CREATE TABLE IF NOT EXISTS "flow_process" (
  "id" integer GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "catagory_id" integer NOT NULL,
  "process_id" bigint DEFAULT NULL,
  "process_key" varchar(100) DEFAULT NULL,
  "process_name" varchar(100) NOT NULL,
  "process_icon" varchar(255) DEFAULT NULL,
  "process_type" varchar(100) DEFAULT NULL,
  "process_version" integer NOT NULL DEFAULT 1,
  "instance_url" varchar(200) DEFAULT NULL,
  "remark" varchar(255) DEFAULT NULL,
  "use_scope" smallint NOT NULL DEFAULT 0,
  "process_state" smallint NOT NULL DEFAULT 1,
  "model_content" text,
  "sort" integer DEFAULT 0,
  "crt_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "crt_user" varchar(32) NOT NULL,
  "crt_name" varchar(255) NOT NULL,
  "crt_host" varchar(255) DEFAULT NULL,
  "upd_time" timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  "upd_user" varchar(32) DEFAULT NULL,
  "upd_name" varchar(255) DEFAULT NULL,
  "upd_host" varchar(255) DEFAULT NULL,
  "deleted" boolean NOT NULL DEFAULT FALSE,
  PRIMARY KEY ("id")
);
COMMENT ON TABLE "flow_process" IS 'FLOW-流程定义';
COMMENT ON COLUMN "flow_process"."id" IS 'ID';
COMMENT ON COLUMN "flow_process"."catagory_id" IS '流程分类ID';
COMMENT ON COLUMN "flow_process"."process_id" IS '当前流程ID';
COMMENT ON COLUMN "flow_process"."process_key" IS '流程定义 key 唯一标识';
COMMENT ON COLUMN "flow_process"."process_name" IS '名称';
COMMENT ON COLUMN "flow_process"."process_icon" IS '图标';
COMMENT ON COLUMN "flow_process"."process_type" IS '类型';
COMMENT ON COLUMN "flow_process"."process_version" IS '流程版本，默认 1';
COMMENT ON COLUMN "flow_process"."instance_url" IS '实例地址';
COMMENT ON COLUMN "flow_process"."remark" IS '备注说明';
COMMENT ON COLUMN "flow_process"."use_scope" IS '使用范围 0，全员 1，指定人员（业务关联） 2，均不可提交';
COMMENT ON COLUMN "flow_process"."process_state" IS '流程状态 0，不可用 1，可用 2，历史版本';
COMMENT ON COLUMN "flow_process"."model_content" IS '流程模型定义JSON内容';
COMMENT ON COLUMN "flow_process"."sort" IS '排序ID';
COMMENT ON COLUMN "flow_process"."crt_time" IS '创建时间';
COMMENT ON COLUMN "flow_process"."crt_user" IS '创建用户ID';
COMMENT ON COLUMN "flow_process"."crt_name" IS '创建用户';
COMMENT ON COLUMN "flow_process"."crt_host" IS '创建IP';
COMMENT ON COLUMN "flow_process"."upd_time" IS '更新时间';
COMMENT ON COLUMN "flow_process"."upd_user" IS '更新用户ID';
COMMENT ON COLUMN "flow_process"."upd_name" IS '更新用户';
COMMENT ON COLUMN "flow_process"."upd_host" IS '更新IP';
COMMENT ON COLUMN "flow_process"."deleted" IS '是否删除';
DROP TRIGGER IF EXISTS "flow_process__upd_time" ON "flow_process";
CREATE TRIGGER "flow_process__upd_time" BEFORE UPDATE ON "flow_process" FOR EACH ROW EXECUTE FUNCTION fa_base_set_upd_time();

CREATE TABLE IF NOT EXISTS "demo_flow_leave" (
  "id" integer GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "flow_id" bigint NULL DEFAULT NULL,
  "apply_user_id" varchar(32) NOT NULL,
  "apply_date" timestamp NULL DEFAULT NULL,
  "apply_reason" varchar(255) NULL DEFAULT NULL,
  "leave_day_count" integer NULL DEFAULT NULL,
  "leave_start_time" timestamp NULL DEFAULT NULL,
  "leave_end_time" timestamp NULL DEFAULT NULL,
  "tenant_id" integer NULL DEFAULT NULL,
  "crt_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "crt_user" varchar(32) NOT NULL,
  "crt_name" varchar(255) NOT NULL,
  "crt_host" varchar(255) NULL DEFAULT NULL,
  "upd_time" timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  "upd_user" varchar(32) NULL DEFAULT NULL,
  "upd_name" varchar(255) NULL DEFAULT NULL,
  "upd_host" varchar(255) NULL DEFAULT NULL,
  "deleted" boolean NOT NULL DEFAULT FALSE,
  PRIMARY KEY ("id")
);
COMMENT ON TABLE "demo_flow_leave" IS 'DEMO-请假流程';
COMMENT ON COLUMN "demo_flow_leave"."id" IS 'ID';
COMMENT ON COLUMN "demo_flow_leave"."flow_id" IS '流程ID';
COMMENT ON COLUMN "demo_flow_leave"."apply_user_id" IS '请假员工ID';
COMMENT ON COLUMN "demo_flow_leave"."apply_date" IS '申请日期';
COMMENT ON COLUMN "demo_flow_leave"."apply_reason" IS '请假原因';
COMMENT ON COLUMN "demo_flow_leave"."leave_day_count" IS '请假天数';
COMMENT ON COLUMN "demo_flow_leave"."leave_start_time" IS '开始时间';
COMMENT ON COLUMN "demo_flow_leave"."leave_end_time" IS '结束时间';
COMMENT ON COLUMN "demo_flow_leave"."tenant_id" IS '租户ID';
COMMENT ON COLUMN "demo_flow_leave"."crt_time" IS '创建时间';
COMMENT ON COLUMN "demo_flow_leave"."crt_user" IS '创建用户ID';
COMMENT ON COLUMN "demo_flow_leave"."crt_name" IS '创建用户';
COMMENT ON COLUMN "demo_flow_leave"."crt_host" IS '创建IP';
COMMENT ON COLUMN "demo_flow_leave"."upd_time" IS '更新时间';
COMMENT ON COLUMN "demo_flow_leave"."upd_user" IS '更新用户ID';
COMMENT ON COLUMN "demo_flow_leave"."upd_name" IS '更新用户';
COMMENT ON COLUMN "demo_flow_leave"."upd_host" IS '更新IP';
COMMENT ON COLUMN "demo_flow_leave"."deleted" IS '是否删除';
DROP TRIGGER IF EXISTS "demo_flow_leave__upd_time" ON "demo_flow_leave";
CREATE TRIGGER "demo_flow_leave__upd_time" BEFORE UPDATE ON "demo_flow_leave" FOR EACH ROW EXECUTE FUNCTION fa_base_set_upd_time();

-- ----------------------------
-- add flow menu
-- ----------------------------
BEGIN;
INSERT INTO "base_rbac_menu" ("id", "parent_id", "scope", "name", "sort", "level", "icon", "status", "link_type", "link_url", "crt_time", "crt_user", "crt_name", "crt_host", "upd_time", "upd_user", "upd_name", "upd_host", "deleted") VALUES (13000000, 10000000, 1, '流程示例', 2, 1, 'briefcase', TRUE, 1, '/admin/flow', '2025-08-24 13:22:35', '1', '超级管理员', '127.0.0.1', '2025-08-24 13:22:48', NULL, NULL, NULL, FALSE);
-- INSERT INTO "base_rbac_menu" ("id", "parent_id", "scope", "name", "sort", "level", "icon", "status", "link_type", "link_url", "crt_time", "crt_user", "crt_name", "crt_host", "upd_time", "upd_user", "upd_name", "upd_host", "deleted") VALUES (22020002, 13000000, 1, '流程编辑器', 0, 1, NULL, TRUE, 1, '/admin/demo/flow/base', '2025-08-24 13:22:52', '1', '超级管理员', '127.0.0.1', '2025-08-24 13:23:05', NULL, NULL, NULL, FALSE);
INSERT INTO "base_rbac_menu" ("id", "parent_id", "scope", "name", "sort", "level", "icon", "status", "link_type", "link_url", "crt_time", "crt_user", "crt_name", "crt_host", "upd_time", "upd_user", "upd_name", "upd_host", "deleted") VALUES (13000100, 13000000, 1, '流程配置', 1, 1, NULL, TRUE, 1, '/admin/flow/manage/deploy', '2025-08-24 13:23:11', '1', '超级管理员', '127.0.0.1', '2025-08-24 13:23:24', NULL, NULL, NULL, FALSE);
INSERT INTO "base_rbac_menu" ("id", "parent_id", "scope", "name", "sort", "level", "icon", "status", "link_type", "link_url", "crt_time", "crt_user", "crt_name", "crt_host", "upd_time", "upd_user", "upd_name", "upd_host", "deleted") VALUES (13000200, 13000000, 1, '流程审批', 2, 1, NULL, TRUE, 1, '/admin/flow/manage/audit', '2025-08-24 13:23:22', '1', '超级管理员', '127.0.0.1', '2025-08-24 13:23:35', NULL, NULL, NULL, FALSE);
INSERT INTO "base_rbac_menu" ("id", "parent_id", "scope", "name", "sort", "level", "icon", "status", "link_type", "link_url", "crt_time", "crt_user", "crt_name", "crt_host", "upd_time", "upd_user", "upd_name", "upd_host", "deleted") VALUES (13000300, 13000000, 1, '流程实例', 3, 1, NULL, TRUE, 1, '/admin/flow/manage/monitor/instance', '2025-08-24 14:21:13', '1', '超级管理员', '127.0.0.1', '2025-08-24 14:21:26', NULL, NULL, NULL, FALSE);
COMMIT;

SELECT setval(
    pg_get_serial_sequence('base_rbac_menu', 'id'),
    COALESCE((SELECT MAX("id") FROM "base_rbac_menu"), 1),
    TRUE
);

