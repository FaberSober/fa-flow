# ADR-0001: 加固 fa-flow 动态表单与工作流运行链路

## Status

Accepted（待逐项实施）

## Date

2026-09-14

## Scope

本 ADR 覆盖以下代码范围：

- 后端：`fa-flow/`
- 前端：`frontend/apps/admin/features/fa-flow-pages/`

本文记录本次代码审查形成的修复基线，不包含业务代码修改。后续实现应按本文的工作项逐项落地，并在完成对应验收标准后勾选。

## Context

`fa-flow` 同时承担工作流定义、流程实例、任务办理和动态表单能力。动态表单会根据配置创建/变更业务表，并通过运行时 SQL 完成表单数据的新增、修改、分页、详情和删除；工作流又需要在表单数据、流程引擎和业务状态之间保持一致。项目同时支持 MySQL 和 PostgreSQL 18，因此运行时 DDL、元数据查询和字段值类型也必须遵循数据库方言。

本次静态审查发现，当前实现的主要风险集中在以下边界：

1. `FlowFormBiz` 拼接表名、字段名、排序字段、值和 ID；`FlowFormMapper.xml` 的 `selectByDynamicSql` 使用 `${sql}`。仅校验 `ff_` 前缀不足以构成标识符和权限边界。
2. 动态表虽包含 `tenant_id` 等系统字段，但运行时 CRUD 没有统一注入租户条件；子表更新主要按子表 ID 定位，缺少主表归属校验。直接 JDBC 也绕过了可能存在的 MyBatis 租户拦截器。
3. 发布、启动、保存表单、绑定实例和办理任务分散在多个调用中，缺少统一事务、幂等和失败补偿边界。部分入口还可能绕过流程状态、使用范围和表单权限校验。
4. 任务通过和驳回接口没有完整传递审批意见/驳回原因，驳回终止行为存在硬编码；流程模型还允许根据配置直接反射调用 Spring Bean 方法。
5. 已有 MySQL/PostgreSQL 版本 SQL 分目录，但运行时动态 DDL 和元数据 SQL 仍硬编码 MySQL 语法，不能仅靠拆分迁移文件解决兼容性。
6. 前端存在表单更新空实现、查看态仍暴露子表编辑操作、业务错误被无条件当作成功、配置项未持久化/未消费等契约问题。
7. 表单配置规则、Checkbox/Switch 的值绑定、Select 的 `valuePropName`、JSON 解析、子表行键以及流程编辑器的全局状态/重复渲染/分支遍历存在可靠性问题。
8. 发现若干确定性或高概率缺陷：任务 Mapper 参数名错误、`@Param` 引用错误包、前端删除接口路径与后端不一致、流程按 key 取最新定义可能误用于历史实例，以及生产代码中的测试提醒和 `System.out` 输出。

如果不先修复上述边界，继续扩展动态表单或审批功能会放大 SQL 注入、跨租户读取/修改、跨主表修改子表、流程状态不一致和前端误操作的风险。

## Decision

### 1. 建立动态表单的可信边界

- 取消用户可控的任意 SQL 片段。移除 `FlowFormMapper.xml` 中的 `${sql}` 通路，运行时值全部使用预编译参数。
- 表名、字段名、排序字段和操作类型不作为任意字符串直接执行：必须来自已发布的表单/字段元数据注册表，并在服务端再次做格式和权限校验。`ff_` 前缀只能作为附加校验，不能替代注册关系。
- 动态 DDL 仅允许已授权的表单设计/管理操作触发；操作需要记录操作者、租户、表/字段、旧值、新值和结果。数据 CRUD 与 DDL 权限分离。
- 动态查询只允许后端根据字段配置生成的固定谓词、排序和分页结构；筛选操作符、字段类型和可排序字段使用枚举/白名单。
- `tenant_id`、`flow_instance_id`、创建人、更新时间等系统字段由服务端生成或维护，客户端提交值必须忽略或拒绝。新增、详情、分页、更新、删除均必须带服务端上下文中的租户条件。
- 子表修改/删除必须同时校验“子表 ID + 当前主表 ID + 租户 ID + 当前用户权限”，不能只按子表 ID 定位。主表和子表的写操作应在同一事务中完成。

### 2. 统一工作流命令的生命周期和一致性

- 将发布、启动、通过、驳回、转办/加签等写操作收敛到明确的命令服务；Controller 只负责参数校验、权限入口和结果返回。
- 启动流程前必须校验流程定义存在、已发布/启用、当前用户命中 `useScope`、表单配置和业务数据归属均有效。`retInstanceStart` 等低层入口不得绕过这些规则；如需保留，只能作为内部适配器并复用同一命令服务。
- 发布流程时，FlowLong 部署状态与本地流程状态必须具备一致性策略：同一数据源使用事务；跨资源调用使用幂等键、事务事件/outbox 或明确的补偿动作，禁止出现“引擎已发布、本地未更新”而无法修复的状态。
- 启动时的表单保存、引擎启动和业务记录绑定必须定义原子性边界。无法纳入同一事务时，必须能检测孤儿数据并自动补偿/重试；重复请求不能重复创建实例或覆盖错误业务记录。
- 办理任务时校验任务当前状态、办理人/候选人、流程实例归属和幂等条件；通过意见、驳回原因必须传入引擎及审计记录，驳回策略从流程配置解析，不使用硬编码终止行为。
- 流程模型发布前执行服务端 JSON schema 和图结构校验：节点类型、分支、路由节点、必要配置、处理人配置和版本字段必须完整；不兼容模型不得进入可执行状态。

### 3. 禁止任意反射，改用处理器注册表

`FaTaskActorProvider` 不再信任流程模型中的任意类名、Bean 名和方法名。改为使用服务端维护的 `handlerCode -> handler` 注册表，流程模型只保存注册 code 和受控参数。注册表中的处理器需要显式声明输入类型、可用场景和权限。

对于历史模型的兼容迁移：先审计现存类路径，只有映射到已登记处理器的配置才允许继续执行；无法映射的模型进入人工修复/只读状态，不能通过“保留任意反射”静默放行。

### 4. 将数据库方言下沉到运行时适配层

- 根据 `DatabaseMetaData#getDatabaseProductName()` 映射数据库类型，并为动态 DDL、字段值转换和元数据查询分别提供 MySQL、PostgreSQL 实现。
- MySQL 使用项目约定的 `tinyint(1)`/`0、1`、`json`、`AUTO_INCREMENT` 等类型和语法；PostgreSQL 使用 `boolean`/`true、false`、`jsonb`、`GENERATED ... AS IDENTITY` 等类型和语法。不得在同一 SQL 文件或运行时分支中混用方言。
- `information_schema` 元数据查询按数据库类型实现，不复用 MySQL 的 `database()`、`COLUMN_COMMENT` 等字段表达式到 PostgreSQL。
- 已发布的 `1.0.x` 迁移脚本保持不变。后续新增升级脚本必须由升级执行器先检查对象/字段是否存在，或采用等价的可重入方案，避免无条件 `ALTER TABLE` 在重试或部分失败后再次执行时报错。禁止使用 `DROP TABLE`、`DROP SCHEMA`、`TRUNCATE` 作为升级手段。

### 5. 明确前端表单、结果和编辑器状态契约

- `FaFlowForm`、`FlowFormView`、`FormEdit` 统一显式传递 `mode`（新增/编辑/查看）、数据、提交回调和只读状态。查看态不应包含可触发修改的控件；编辑态必须真正调用更新 API，删除空的 `invokeUpdateTask` 占位逻辑。
- API 结果必须经过统一的业务状态判断；只有 HTTP 成功且业务 `status` 为成功时才能关闭抽屉、刷新列表或刷新待办数量。错误结果必须保留当前输入并展示错误信息。
- 保留的配置项必须有完整链路：类型定义、编辑器、保存接口、后端字段/存储、运行时消费和回显；例如 `submitterPermission` 要么完成持久化及运行时校验，要么在明确决策后删除，不能停留在前端孤立配置。
- 表单规则使用配置中的 `rules`，而非只读取 `required`。Checkbox/Switch 使用 `valuePropName="checked"`；Select 使用默认 `value` 绑定，不能使用 `valuePropName="checked"`。
- JSON 解析集中到带错误边界的工具函数，解析失败时显示可恢复的错误状态，不得让页面直接崩溃。子表统一生成稳定行键，并正确处理 `undefined`/空值清空；查看态和禁用态需贯穿到子表增删改控件。
- 流程编辑器状态按组件实例隔离，避免全局 Zustand 单例互相覆盖；缩略图只渲染静态预览，不重复挂载交互节点。树遍历须覆盖条件、并行、包容、路由及其分支；所有 Hook 必须在组件生命周期内保持固定调用顺序。

### 6. 以安全、兼容和回归测试作为合并门槛

修复不以“代码可以编译”作为唯一完成条件。每个工作项至少应有对应的单元测试或集成测试；涉及租户、动态 SQL、DDL、事务和任务权限的项目必须增加负向测试。

## Remediation Backlog

以下工作项按风险和依赖排序，后续可逐项修改并勾选。编号用于提交、评审和测试结果关联。

### P0：必须先完成的安全边界

- [x] **F-SEC-01 动态 SQL/DDL 收口**
  - 范围：`FlowFormBiz.java`、`FlowFormMapper.xml`、`FlowFormController.java`。
  - 修复：删除任意 SQL 片段和直接拼接用户值；引入元数据注册表、标识符白名单、预编译参数、排序/操作符白名单；DDL 权限与审计分离。
  - 验收：代码中不存在用户输入直达 `${sql}`/JDBC SQL 的路径；注入字符、越权表名/列名、非法排序字段均被拒绝；正常动态表单 CRUD 和字段变更仍可用。
  - 实施记录（2026-09-15）：新增动态表/字段标识符和 DDL 类型校验；DDL 变更仅允许已登记流程表单表并要求 `/admin/flow/manage/form` 权限；数据 CRUD 改为 `PreparedStatement`；分页查询改为参数化 count/data 查询；移除 `${sql}` Mapper 通路。租户条件、主子表归属和事务一致性仍分别由 `F-SEC-02`、`F-WF-01` 处理。

- [ ] **F-SEC-02 租户、系统字段和子表归属校验**
  - 范围：`FlowFormBiz` 的新增、更新、分页、详情、删除及 `updateFormData`。
  - 修复：租户条件由服务端上下文注入；系统字段不接受客户端覆盖；子表写操作校验主表 ID、外键和租户；统一事务边界。
  - 验收：跨租户 ID、跨主表子表 ID、伪造 `tenant_id`/`flow_instance_id` 均无法读取或修改；同一租户正常数据不受影响。

### P1：一致性、权限和跨数据库能力

- [ ] **F-WF-01 流程发布/启动一致性**
  - 范围：`FlowProcessBiz.publish`、`FlowProcessBiz.start`、表单保存和实例绑定入口。
  - 修复：统一命令服务；校验流程状态、使用范围、表单和业务归属；增加事务、幂等和跨资源补偿/重试机制。
  - 验收：任一阶段失败不会留下不可识别的孤儿记录；重复请求不会重复部署/启动；禁用流程、无权限用户和无效表单均被拒绝。

- [ ] **F-WF-02 任务办理语义完整**
  - 范围：`FlowTaskBiz.pass/reject`、`FlowTaskPassReqVo`、`FlowInstanceDeal`。
  - 修复：传递并持久化通过意见、驳回原因；按流程配置选择驳回路径；校验任务办理人、状态、实例归属和幂等。
  - 验收：审计记录包含意见/原因；不同驳回策略产生预期结果；重复提交和非办理人提交不会改变任务状态。

- [ ] **F-WF-03 处理器注册表替代任意反射**
  - 范围：`FaTaskActorProvider` 及流程模型中的 `nodeAssigneeCodePath`。
  - 修复：使用受控 handler code 注册表；审计并迁移历史类路径；未登记处理器不得执行。
  - 验收：模型不能调用任意类/Bean/方法；登记处理器可正常获取候选人；非法配置有明确错误。

- [ ] **F-WF-04 流程定义版本和模型校验**
  - 范围：`FlowProcessBiz.getByKey`、流程发布接口、模型解析/校验代码。
  - 修复：历史实例按实例绑定的部署 ID/版本/快照解析，不按 `processKey` 盲取最新记录；发布前校验节点、分支、路由和必要配置。
  - 验收：新旧版本并存时，历史详情、待办和办理均使用正确版本；非法或不完整模型不能发布。

- [ ] **F-DB-01 运行时 MySQL/PostgreSQL 方言适配**
  - 范围：`FlowFormBiz` 动态 DDL、字段值处理和 `FlowFormMapper.xml` 元数据查询。
  - 修复：建立数据库类型适配层，分别实现 DDL、元数据 SQL 和布尔/JSON/自增类型映射；不要只拆分版本迁移目录。
  - 验收：MySQL 5.7 与 PostgreSQL 18 分别完成建表、增删改字段、表单 CRUD、元数据读取和布尔/JSON 回显；两种数据库的 SQL 不混用。

- [ ] **F-FE-01 表单编辑/查看和提交结果契约**
  - 范围：`FaFlowForm.tsx`、`FlowFormView.tsx`、`FormEdit.tsx`、`FlowInstanceDeal.tsx`、`FlowAuditStart.tsx`、`FlowProcessEdit.tsx`。
  - 修复：实现真实更新调用；统一 mode/disabled/onFinish 契约；查看态移除所有变更入口；按业务状态决定关闭和刷新。
  - 验收：编辑能保存并回显；查看态不能新增、编辑、删除或触发修改 API；失败响应不会关闭页面或伪造成功提示。

### P2：可靠性、契约和运维质量

- [ ] **F-FE-02 表单配置语义和子表稳定性**
  - 范围：`FaFormEditorItem.tsx`、`FormItemDecoAlertProperty.tsx`、`FaFormSubTable.tsx`。
  - 修复：接入完整 `rules`；修正 Checkbox/Switch/Select 的值绑定；统一 `_key`/`id` 行键；正确清空空值；将 disabled 传递到所有子表操作。
  - 验收：required、pattern、长度等规则按配置生效；布尔值和 Select 回显正确；空子表可清空；重复渲染和增删不会出现行错位。

- [ ] **F-FE-03 编辑器实例隔离和树遍历**
  - 范围：`useWorkFlowStore.ts`、`FaWorkFlow.tsx`、`ZoomPanEditor.tsx`、`useNodeTreeData.ts`、`utils.ts`、`FaFormEditorItem.tsx`。
  - 修复：改为实例级 store/context；缩略图使用静态投影；补齐 routeNodes/分支遍历和删除逻辑；消除条件 Hook 调用。
  - 验收：同页两个编辑器互不覆盖；缩略图不产生第二套交互节点；任意分支节点均可查找、删除和回显；切换组件类型不触发 Hook 顺序错误。

- [ ] **F-ROBUST-01 解析、Mapper 和 API 契约修复**
  - 范围：`flow-pages` 中直接 `JSON.parse` 的调用、`FlowTaskFaMapper.xml`、`FlowFormMapper.java`、`flowForm.ts`。
  - 修复：集中安全解析并提供错误态；修正 `actorIds`/`actorId` 和 `processName` 参数引用；使用 MyBatis 的 `org.apache.ibatis.annotations.Param`；将前端删除路径改为后端真实的 `removeFormDataById/{flowFormId}/{id}`，或由双方统一后再变更。
  - 验收：非法模型/表单 JSON 可恢复；任务查询不出现参数绑定异常；删除请求命中真实接口；增加接口契约测试。

- [ ] **F-OPS-01 生产日志和提醒治理**
  - 范围：`FaTaskReminder.java`、`FaFlowLongConfig.java`、`FaTaskListener.java`。
  - 修复：移除测试提醒和 `System.out/err`；使用项目日志组件；提醒任务增加开关、租户/环境策略和幂等控制。
  - 验收：测试环境和生产环境均不会因组件加载自动发送测试提醒；日志包含可检索上下文且不泄露表单敏感值。

## Acceptance Gates

完成本 ADR 前，至少满足以下门槛：

1. **安全门槛**：动态 SQL 无任意片段执行；跨租户、跨主表、伪造系统字段和越权任务的负向测试通过。
2. **一致性门槛**：发布、启动、表单保存、实例绑定和任务办理的失败/重试行为有测试和可观测结果；无不可补偿的半成功状态。
3. **数据库门槛**：MySQL 5.7、PostgreSQL 18 各自通过动态表建表、字段变更、CRUD、元数据读取和类型回显测试。
4. **前端门槛**：编辑、查看、审批、表单校验、布尔控件、子表和双编辑器实例场景通过；业务失败不会被当成成功。
5. **工程门槛**：后端受影响模块编译通过；前端共享类型依赖和受影响 feature 的 TypeScript 检查通过；新增测试不依赖生产数据。

## Consequences

### Positive

- 动态表单从“任意 SQL 字符串”收敛为受控元数据和参数，降低注入、越权和误删风险。
- 租户、主子表归属和流程办理人形成统一服务端边界，减少跨数据域写入。
- 工作流引擎、本地业务状态和表单数据具备可重试、可补偿的一致性策略。
- MySQL 与 PostgreSQL 的运行时行为和迁移行为更可预测，前端编辑/查看契约更清晰。
- 后续问题可以按工作项编号关联代码提交、测试和发布记录。

### Negative

- 需要增加表单/字段注册元数据、权限校验、审计、测试和运维配置，短期实现成本会增加。
- 历史动态表、旧流程模型和旧版前端数据可能需要迁移或兼容读取。
- 事务、outbox/补偿和幂等机制会带来额外状态、监控和故障处理复杂度。
- 两种数据库需要分别维护运行时适配和测试矩阵。

### Neutral

- 本 ADR 不要求替换 FlowLong，也不要求一次性重写所有页面；可以保持现有 API 外形，在内部先接入统一命令服务。
- 已发布的版本 SQL 不回改；兼容修复通过新版本脚本、升级执行器或运行时适配完成。
- 默认不新增数据库外键，除非后续需求明确要求；数据归属约束首先由服务层和测试保证。

## Alternatives Considered

### 仅增加黑名单和字符串过滤

拒绝。黑名单无法覆盖 SQL 语法、编码、注释、标识符引用和未来新增字段；也无法解决租户/主子表归属问题。必须使用元数据注册、白名单和参数化组合。

### 继续用直接 JDBC，并为 PostgreSQL 复制一套字符串

拒绝。复制字符串会继续扩大 DDL、元数据和类型分支的维护面，且不能自然解决事务、权限和 SQL 注入边界。保留 JDBC 作为实现细节可以，但必须隐藏在受控适配层和命令服务之后。

### 只修前端查看态和提示，不改后端

拒绝。前端禁用不能替代服务端租户、任务办理人和动态 SQL 校验；恶意或旧客户端仍可直接调用接口。

### 继续使用全局 Zustand 流程编辑器 Store

拒绝。全局 Store 会让同页多实例、KeepAlive 或异步切换互相覆盖；实例级 context/store 的隔离成本可控，且更符合组件生命周期。

### 直接修改已发布迁移脚本

拒绝。已执行环境可能已经处于不同中间状态，修改历史脚本会破坏升级可追溯性。应新增安全、可重入的升级脚本或修复升级执行器。

## Risks and Rollback

- **历史元数据不合规**：切换注册表前先扫描已有表名、字段名、类型和租户归属；不合规数据进入迁移清单，不在运行时通过放宽校验兜底。
- **旧流程模型不可执行**：提供只读/人工迁移状态和明确错误码；不要为兼容而恢复任意反射执行。
- **跨资源事务失败**：先记录部署/启动幂等键和关联状态，再启用重试/补偿；补偿任务必须可观测、可暂停和可人工重放。
- **方言回归**：每次运行时 DDL/元数据变更必须同时执行 MySQL、PostgreSQL 的最小集成测试；任一数据库失败不得发布。
- **前端契约变更**：先保留兼容适配层并增加接口契约测试，确认新旧数据回显一致后再删除旧字段/路径。

## References

- `fa-flow/src/main/java/com/faber/api/flow/form/biz/FlowFormBiz.java`
- `fa-flow/src/main/resources/mapper/flow/form/FlowFormMapper.xml`
- `fa-flow/src/main/java/com/faber/api/flow/form/rest/FlowFormController.java`
- `fa-flow/src/main/java/com/faber/api/flow/manage/biz/FlowProcessBiz.java`
- `fa-flow/src/main/java/com/faber/api/flow/manage/biz/FlowTaskBiz.java`
- `fa-flow/src/main/java/com/faber/api/flow/manage/config/FaTaskActorProvider.java`
- `fa-flow/src/main/resources/mapper/flow/manage/FlowTaskFaMapper.xml`
- `fa-flow/src/main/resources/sql/fa-flow/mysql/`
- `fa-flow/src/main/resources/sql/fa-flow/postgre/`
- `frontend/apps/admin/features/fa-flow-pages/`
- `frontend/fa-ui/doc/frontend/form.md`
- `frontend/fa-ui/doc/frontend/table.md`
