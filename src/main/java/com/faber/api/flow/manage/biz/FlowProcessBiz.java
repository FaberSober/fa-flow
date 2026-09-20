package com.faber.api.flow.manage.biz;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.exception.FlowLongException;
import com.aizuda.bpm.engine.entity.FlwExtInstance;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwHisTask;
import com.aizuda.bpm.engine.entity.FlwHisTaskActor;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.faber.api.flow.core.enums.FaInstanceStateEnum;
import com.faber.api.flow.form.biz.FlowFormBiz;
import com.faber.api.flow.form.biz.FlowFormSqlUtils;
import com.faber.api.flow.form.entity.FlowForm;
import com.faber.api.flow.form.enums.FlowFormStatusEnum;
import com.faber.api.flow.form.enums.FlowFormTypeEnum;
import com.faber.api.flow.form.vo.config.FlowFormDataConfig;
import com.faber.api.flow.manage.entity.FlowProcess;
import com.faber.api.flow.manage.enums.FlowProcessFormTypeEnum;
import com.faber.api.flow.manage.mapper.FlowProcessMapper;
import com.faber.api.flow.manage.vo.req.FlowProcessStartReqVo;
import com.faber.api.flow.manage.vo.ret.FlowApprovalInfo;
import com.faber.api.flow.manage.vo.ret.FlowProcessApprovalVo;
import com.faber.core.context.BaseContextHandler;
import com.faber.core.exception.BuzzException;
import com.faber.core.utils.FaRedisUtils;
import com.faber.core.web.biz.BaseBiz;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;

/**
 * FLOW-流程定义
 *
 * @author xu.pengfei
 * @email 1508075252@qq.com
 * @date 2025-08-23 14:10:49
 */
@Service
public class FlowProcessBiz extends BaseBiz<FlowProcessMapper, FlowProcess> {

    private static final int PROCESS_STATE_ENABLED = 1;
    private static final int USE_SCOPE_ALL = 0;
    private static final int USE_SCOPE_ASSOCIATED_USER = 1;
    private static final int USE_SCOPE_DISABLED = 2;
    private static final Set<Integer> VALID_USE_SCOPES = Set.of(
            USE_SCOPE_ALL, USE_SCOPE_ASSOCIATED_USER, USE_SCOPE_DISABLED);
    private static final Set<String> START_OWNER_FIELDS = Set.of(
            "applyuserid", "applicantid", "ownerid", "createuserid", "creatorid", "userid");
    private static final String START_BUSINESS_KEY_PREFIX = "flow-start:";

    @Resource FlowLongEngine flowLongEngine;
    @Resource FlowFormBiz flowFormBiz;
    @Resource FaRedisUtils faRedisUtils;

    public FlowProcess getByKey(String key) {
        return lambdaQuery()
                .eq(FlowProcess::getProcessKey, key)
                .orderByDesc(FlowProcess::getUpdTime)
                .last("LIMIT 1")
                .one();
    }

    @Override
    public boolean save(FlowProcess entity) {
        // set default modelContent
        // JSONObject model = new JSONObject("""
        // {
        // "key": "",
        // "name": "",
        // "nodeConfig": {
        // "nodeName": "发起人",
        // "nodeKey": "flk1725161262899",
        // "type": 0,
        // "childNode": {
        // "nodeName": "审核人",
        // "nodeKey": "flk1724860316169",
        // "callProcess": null,
        // "type": 1,
        // "setType": 3,
        // "nodeAssigneeList": [
        // {
        // "id": "1",
        // "name": "超级管理员"
        // }
        // ],
        // "examineLevel": 1,
        // "directorLevel": 1,
        // "selectMode": 1,
        // "termAuto": false,
        // "term": 0,
        // "termMode": 1,
        // "examineMode": 2,
        // "directorMode": 0,
        // "typeOfApprove": 1,
        // "remind": false,
        // "approveSelf": 1
        // }
        // }
        // }
        // """);
        // model.set("key", entity.getProcessKey());
        // model.set("name", entity.getProcessName());

        // entity.setModelContent(model.toString());

        return super.save(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public FlowProcess publish(FlowProcess request) {
        if (request == null || request.getId() == null) {
            throw new BuzzException("流程定义 ID 不能为空");
        }

        requireCurrentUserId();
        String tenantScope = requireTenantScope();
        RLock lock = faRedisUtils.getLock("flow:publish:" + tenantScope + ":" + request.getId());
        boolean locked = acquireLock(lock, "发布流程");
        try {
            // 只使用服务端已保存的定义，避免客户端覆盖 processId、状态或模型字段。
            FlowProcess flowProcess = this.getById(request.getId());
            if (flowProcess == null) {
                throw new BuzzException("流程定义不存在，id=" + request.getId());
            }
            validateFlowProcess(flowProcess);

            FlwProcess deployedProcess = findEngineProcess(flowProcess.getProcessId());
            if (deployedProcess != null) {
                ensureEngineTenant(deployedProcess, tenantScope);
            }

            if (isSameActiveDeployment(flowProcess, deployedProcess)) {
                flowProcess.setProcessId(deployedProcess.getId());
                flowProcess.setProcessVersion(deployedProcess.getProcessVersion());
            } else {
                Long processId = flowLongEngine.processService().deploy(
                        null,
                        flowProcess.getModelContent(),
                        createFlowCreator(),
                        true,
                        process -> flowProcess.setProcessVersion(process.getProcessVersion()));
                if (processId == null) {
                    throw new BuzzException("发布流程失败：引擎未返回流程 ID");
                }
                flowProcess.setProcessId(processId);
            }

            flowProcess.setProcessState(PROCESS_STATE_ENABLED);
            if (!this.updateById(flowProcess)) {
                throw new BuzzException("发布流程失败：本地流程定义更新失败");
            }
            return flowProcess;
        } finally {
            releaseLockAfterTransaction(lock, locked);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public FlwInstance start(FlowProcessStartReqVo reqVo) {
        if (reqVo == null || reqVo.getProcessId() == null) {
            throw new BuzzException("流程定义 ID 不能为空");
        }

        String requestId = requireRequestId(reqVo.getRequestId());
        String userId = requireCurrentUserId();
        String tenantScope = requireTenantScope();
        String businessKey = buildStartBusinessKey(tenantScope, userId, reqVo.getProcessId(), requestId);
        RLock lock = faRedisUtils.getLock("flow:start:" + businessKey);
        boolean locked = acquireLock(lock, "启动流程");
        try {
            // 幂等重试应返回原结果，即使流程在首次请求后被停用或表单状态发生变化。
            FlwInstance existingInstance = findExistingInstance(businessKey);
            if (existingInstance != null) {
                return existingInstance;
            }

            FlowProcess flowProcess = requireStartableProcess(reqVo.getProcessId(), tenantScope);
            FlowForm flowForm = FlowProcessFormTypeEnum.CUSTOM == flowProcess.getFormType()
                    ? validateCustomForm(flowProcess)
                    : null;
            Map<String, Object> args = reqVo.getArgs() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(reqVo.getArgs());
            validateUseScope(flowProcess, flowForm, args, userId);

            if (flowForm != null) {
                try {
                    flowFormBiz.saveFormData(flowProcess.getFormId(), args);
                } catch (Exception e) {
                    throw new BuzzException("保存业务数据失败：" + e.getMessage());
                }
            }

            Optional<FlwInstance> instanceOptional = flowLongEngine.startInstanceById(
                    flowProcess.getProcessId(),
                    createFlowCreator(),
                    args,
                    false,
                    () -> FlwInstance.of(businessKey));
            FlwInstance instance = instanceOptional.orElseThrow(() -> new BuzzException("启动流程失败"));
            if (instance.getId() == null) {
                throw new BuzzException("启动流程失败：引擎未返回实例 ID");
            }

            if (flowForm != null) {
                Long formDataId = MapUtil.getLong(args, "id");
                if (formDataId == null) {
                    throw new BuzzException("保存业务数据后未获取到记录 ID");
                }
                flowFormBiz.updateDataFlowInstanceId(flowProcess.getFormId(), formDataId, instance.getId());
            }
            return instance;
        } finally {
            releaseLockAfterTransaction(lock, locked);
        }
    }

    // 流程实例-返回实例id
    @Transactional(rollbackFor = Exception.class)
    public FlwInstance retInstanceStart(FlowProcessStartReqVo reqVo) {
        return start(reqVo);
    }

    private FlowProcess requireStartableProcess(Long processId, String tenantScope) {
        FlowProcess flowProcess = this.getById(processId);
        if (flowProcess == null) {
            throw new BuzzException("流程定义不存在，id=" + processId);
        }
        validateFlowProcessBasics(flowProcess);
        if (!Objects.equals(PROCESS_STATE_ENABLED, flowProcess.getProcessState())) {
            throw new BuzzException("流程定义未启用，id=" + processId);
        }
        if (flowProcess.getProcessId() == null) {
            throw new BuzzException("流程定义尚未发布，id=" + processId);
        }

        FlwProcess deployedProcess;
        try {
            deployedProcess = flowLongEngine.processService().getProcessById(flowProcess.getProcessId());
        } catch (FlowLongException e) {
            throw new BuzzException("流程引擎部署不存在，processId=" + flowProcess.getProcessId());
        }
        if (deployedProcess == null) {
            throw new BuzzException("流程引擎部署不存在，processId=" + flowProcess.getProcessId());
        }
        ensureEngineTenant(deployedProcess, tenantScope);
        if (!Objects.equals(PROCESS_STATE_ENABLED, deployedProcess.getProcessState())) {
            throw new BuzzException("流程引擎定义未启用，processId=" + flowProcess.getProcessId());
        }
        if (!Objects.equals(flowProcess.getProcessKey(), deployedProcess.getProcessKey())) {
            throw new BuzzException("本地流程定义与引擎定义不匹配，id=" + processId);
        }
        if (!sameModel(flowProcess.getModelContent(), deployedProcess.getModelContent())) {
            throw new BuzzException("本地流程模型与引擎定义不匹配，id=" + processId);
        }
        return flowProcess;
    }

    private FlowForm validateFlowProcess(FlowProcess flowProcess) {
        validateFlowProcessBasics(flowProcess);
        if (FlowProcessFormTypeEnum.CUSTOM == flowProcess.getFormType()) {
            return validateCustomForm(flowProcess);
        }
        return null;
    }

    private void validateFlowProcessBasics(FlowProcess flowProcess) {
        if (flowProcess == null) {
            throw new BuzzException("流程定义不能为空");
        }
        if (StrUtil.isBlank(flowProcess.getProcessKey())) {
            throw new BuzzException("流程定义 key 不能为空");
        }
        if (StrUtil.isBlank(flowProcess.getProcessName())) {
            throw new BuzzException("流程名称不能为空");
        }
        if (StrUtil.isBlank(flowProcess.getModelContent())) {
            throw new BuzzException("流程模型不能为空");
        }
        if (flowProcess.getFormType() == null) {
            throw new BuzzException("流程表单类型不能为空");
        }
        if (!VALID_USE_SCOPES.contains(flowProcess.getUseScope())) {
            throw new BuzzException("流程使用范围不合法");
        }
    }

    private FlowForm validateCustomForm(FlowProcess flowProcess) {
        if (flowProcess.getFormId() == null) {
            throw new BuzzException("自定义流程表单 ID 不能为空");
        }
        FlowForm flowForm = flowFormBiz.getById(flowProcess.getFormId());
        if (flowForm == null) {
            throw new BuzzException("流程表单不存在，formId=" + flowProcess.getFormId());
        }
        if (flowForm.getStatus() != FlowFormStatusEnum.ENABLED) {
            throw new BuzzException("流程表单未启用，formId=" + flowProcess.getFormId());
        }
        if (flowForm.getType() != FlowFormTypeEnum.DESIGN) {
            throw new BuzzException("流程表单类型不支持启动自定义流程，formId=" + flowProcess.getFormId());
        }
        if (flowForm.getFlowProcessId() != null
                && !Objects.equals(flowForm.getFlowProcessId(), flowProcess.getId())) {
            throw new BuzzException("流程表单已关联其他流程，formId=" + flowProcess.getFormId());
        }
        if (flowForm.getConfig() == null) {
            throw new BuzzException("流程表单配置不能为空，formId=" + flowProcess.getFormId());
        }

        FlowFormDataConfig dataConfig = flowForm.getDataConfig();
        FlowFormDataConfig.Table mainTable = dataConfig == null ? null : dataConfig.getMain();
        if (mainTable == null || StrUtil.isBlank(mainTable.getTableName())
                || mainTable.getColumns() == null || mainTable.getColumns().isEmpty()) {
            throw new BuzzException("流程表单主表配置无效，formId=" + flowProcess.getFormId());
        }
        FlowFormSqlUtils.requireTableName(mainTable.getTableName());
        if (!Objects.equals(flowForm.getTableName(), mainTable.getTableName())) {
            throw new BuzzException("流程表单表名与数据配置不一致，formId=" + flowProcess.getFormId());
        }
        Set<String> configuredFields = new HashSet<>();
        for (FlowFormDataConfig.Column column : mainTable.getColumns()) {
            if (column == null) {
                throw new BuzzException("流程表单字段配置无效，formId=" + flowProcess.getFormId());
            }
            String field = FlowFormSqlUtils.requireIdentifier(column.getField(), "字段名");
            if (!configuredFields.add(field.toLowerCase(Locale.ROOT))) {
                throw new BuzzException("流程表单包含重复字段，formId=" + flowProcess.getFormId());
            }
        }
        return flowForm;
    }

    private void validateUseScope(FlowProcess flowProcess, FlowForm flowForm,
                                  Map<String, Object> args, String userId) {
        Integer useScope = flowProcess.getUseScope();
        if (Objects.equals(USE_SCOPE_ALL, useScope)) {
            return;
        }
        if (Objects.equals(USE_SCOPE_DISABLED, useScope)) {
            throw new BuzzException("当前流程不允许发起");
        }

        Set<String> configuredOwnerFields = flowForm == null ? null : getConfiguredOwnerFields(flowForm);
        Object ownerValue = null;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String fieldName = normalizeFieldName(entry.getKey());
            if (!START_OWNER_FIELDS.contains(fieldName)
                    || (configuredOwnerFields != null && !configuredOwnerFields.contains(fieldName))) {
                continue;
            }
            if (entry.getValue() != null) {
                ownerValue = entry.getValue();
                break;
            }
        }
        if (ownerValue == null || !Objects.equals(userId, String.valueOf(ownerValue).trim())) {
            throw new BuzzException("当前用户不在流程使用范围内");
        }
    }

    private Set<String> getConfiguredOwnerFields(FlowForm flowForm) {
        Set<String> fields = new HashSet<>();
        FlowFormDataConfig dataConfig = flowForm.getDataConfig();
        if (dataConfig == null || dataConfig.getMain() == null || dataConfig.getMain().getColumns() == null) {
            return fields;
        }
        for (FlowFormDataConfig.Column column : dataConfig.getMain().getColumns()) {
            if (column != null && StrUtil.isNotBlank(column.getField())) {
                fields.add(normalizeFieldName(column.getField()));
            }
        }
        return fields;
    }

    private String normalizeFieldName(String fieldName) {
        return fieldName == null ? "" : fieldName.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private FlwProcess findEngineProcess(Long processId) {
        if (processId == null) {
            return null;
        }
        try {
            return flowLongEngine.processService().getProcessById(processId);
        } catch (FlowLongException e) {
            if (StrUtil.containsIgnoreCase(e.getMessage(), "does not exist")) {
                return null;
            }
            throw e;
        }
    }

    private boolean isSameActiveDeployment(FlowProcess flowProcess, FlwProcess deployedProcess) {
        return deployedProcess != null
                && Objects.equals(PROCESS_STATE_ENABLED, deployedProcess.getProcessState())
                && Objects.equals(flowProcess.getProcessKey(), deployedProcess.getProcessKey())
                && sameModel(flowProcess.getModelContent(), deployedProcess.getModelContent());
    }

    private boolean sameModel(String first, String second) {
        try {
            return JSONUtil.parseObj(first).equals(JSONUtil.parseObj(second));
        } catch (RuntimeException e) {
            // 交给引擎的部署校验返回明确错误，不把不可解析模型当作幂等发布。
            return false;
        }
    }

    private void ensureEngineTenant(FlwProcess deployedProcess, String tenantScope) {
        if (isTenantEnabled() && !Objects.equals(tenantScope, deployedProcess.getTenantId())) {
            throw new BuzzException("流程引擎定义不属于当前租户");
        }
    }

    private FlowCreator createFlowCreator() {
        String userId = requireCurrentUserId();
        String userName = BaseContextHandler.getName();
        if (StrUtil.isBlank(userName)) {
            userName = userId;
        }
        FlowCreator creator = FlowCreator.of(userId, userName);
        if (isTenantEnabled()) {
            creator.tenantId(requireTenantId());
        }
        return creator;
    }

    private String requireCurrentUserId() {
        String userId = getCurrentUserId();
        if (StrUtil.isBlank(userId)) {
            throw new BuzzException("当前用户上下文为空");
        }
        return userId;
    }

    private String requireTenantId() {
        String tenantId = getCurrentTenantId();
        if (StrUtil.isBlank(tenantId)) {
            throw new BuzzException("当前租户上下文为空");
        }
        return tenantId;
    }

    private String requireTenantScope() {
        return isTenantEnabled() ? requireTenantId() : "global";
    }

    private String requireRequestId(String requestId) {
        if (StrUtil.isBlank(requestId) || requestId.length() > 64) {
            throw new BuzzException("requestId 不能为空且长度不能超过64");
        }
        return requestId.trim();
    }

    private String buildStartBusinessKey(String tenantScope, String userId, Long processId, String requestId) {
        String source = tenantScope + "|" + userId + "|" + processId + "|" + requestId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return START_BUSINESS_KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("系统不支持 SHA-256", e);
        }
    }

    private FlwInstance findExistingInstance(String businessKey) {
        Optional<List<FlwInstance>> activeOptional = flowLongEngine.queryService()
                .getInstancesByBusinessKey(businessKey);
        if (activeOptional.isPresent()) {
            for (FlwInstance instance : activeOptional.get()) {
                return instance;
            }
        }

        Optional<List<FlwHisInstance>> historyOptional = flowLongEngine.queryService()
                .getHisInstancesByBusinessKey(businessKey);
        if (historyOptional.isPresent()) {
            for (FlwHisInstance instance : historyOptional.get()) {
                return instance;
            }
        }
        return null;
    }

    private boolean acquireLock(RLock lock, String operation) {
        try {
            if (!lock.tryLock(0, TimeUnit.SECONDS)) {
                throw new BuzzException(operation + "正在处理中，请勿重复提交");
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BuzzException(operation + "失败：等待并发控制锁时被中断");
        }
    }

    private void releaseLockAfterTransaction(RLock lock, boolean locked) {
        if (!locked) {
            return;
        }
        Runnable unlock = () -> {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            unlock.run();
            return;
        }
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    unlock.run();
                }
            });
        } catch (IllegalStateException e) {
            unlock.run();
        }
    }

    /**
     * 根据任务ID获取当前流程审批详情，主要用于审批任务，用户根据指定任务ID查看当前流程实例详情
     * @param taskId
     * @return
     */
    public FlowApprovalInfo getApprovalInfoByTaskId(Long taskId) {
        FlwTask flwTask = flowLongEngine.queryService().getTask(taskId);
        if (flwTask == null) {
            return null;
        }
        Long instanceId = flwTask.getInstanceId();
        return getApprovalInfoById(instanceId, taskId);
    }

    /**
     * 获取当前流程审批详情
     * @param instanceId 流程实例ID
     * @return
     */
    public FlowApprovalInfo getApprovalInfoById(Long instanceId, Long taskId) {
        FlowApprovalInfo data = new FlowApprovalInfo();

        data.setInstanceId(instanceId);

        FlwHisInstance flwHisInstance = flowLongEngine.queryService().getHistInstance(instanceId);
        FlwExtInstance flwExtInstance = flowLongEngine.queryService().getExtInstance(instanceId);
        FlwProcess flwProcess = flowLongEngine.processService().getProcessById(flwHisInstance.getProcessId());
        FlowProcess flowProcess = this.getByKey(flwProcess.getProcessKey());

        // 获取当前流程实例的历史操作信息
        List<FlwHisTask> hisTasks = flowLongEngine.queryService().getHisTasksByInstanceId(instanceId).get();

        // 获取当前流程实例的当前操作信息
        List<FlwTask> tasks = flowLongEngine.queryService().getActiveTasksByInstanceId(instanceId).get();

        data.setInstanceState(FaInstanceStateEnum.fromValue(flwHisInstance.getInstanceState()));
        data.setCreateBy(flwHisInstance.getCreateBy());
        data.setCreateId(flwHisInstance.getCreateId());
        data.setCreateTime(flwHisInstance.getCreateTime());
        data.setFormContent(flwHisInstance.getVariable());
        data.setModelContent(flwExtInstance.getModelContent());

        data.setFlwProcess(flwProcess);
        data.setFlowProcess(flowProcess);

        Map<String, Object> renderNodes = new HashMap<>();

        // 渲染历史task
        for (FlwHisTask hisTask : hisTasks) {
            // renderNodes.put(hisTask.getTaskKey(), hisTask.getTaskState());
            // 这里需要考虑放1还是taskState
            renderNodes.put(hisTask.getTaskKey(), "0");
        }
        // 渲染当前task
        for (FlwTask task : tasks) {
            renderNodes.put(task.getTaskKey(), "1");
            if (taskId != null && task.getId().equals(taskId)) {
                data.setCurrentTask(task);
            }
        }

        data.setRenderNodes(renderNodes);

        // 构建流程审批历史记录
        List<FlowProcessApprovalVo> processApprovals = buildProcessApprovals(hisTasks, tasks);
        data.setProcessApprovals(processApprovals);

        return data;
    }

    /**
     * 构建流程审批历史记录列表
     * 
     * @param hisTasks 历史任务列表
     * @param tasks    当前活跃任务列表
     * @return 审批历史记录列表
     */
    private List<FlowProcessApprovalVo> buildProcessApprovals(List<FlwHisTask> hisTasks, List<FlwTask> tasks) {
        List<FlowProcessApprovalVo> approvals = new ArrayList<>();

        // 处理历史任务（已完成的审批）
        for (FlwHisTask hisTask : hisTasks) {
            FlowProcessApprovalVo approval = new FlowProcessApprovalVo();
            approval.setId(hisTask.getId());
            approval.setCreateId(hisTask.getCreateId());
            approval.setCreateBy(hisTask.getCreateBy());
            approval.setCreateTime(hisTask.getCreateTime());
            approval.setInstanceId(hisTask.getInstanceId());
            approval.setTaskId(hisTask.getId());
            approval.setTaskName(hisTask.getTaskName());
            approval.setTaskKey(hisTask.getTaskKey());
            approval.setType(1); // 1表示已完成的审批任务
            setTaskAction(approval, hisTask);
            
            approvals.add(approval);
        }

        // 处理当前活跃任务（待处理的审批）
        for (FlwTask task : tasks) {
            FlowProcessApprovalVo approval = new FlowProcessApprovalVo();
            approval.setId(task.getId());
            approval.setCreateId(task.getCreateId());
            approval.setCreateBy(task.getCreateBy());
            approval.setCreateTime(task.getCreateTime());
            approval.setInstanceId(task.getInstanceId());
            approval.setTaskId(task.getId());
            approval.setTaskName(task.getTaskName());
            approval.setType(-1); // -1表示当前待处理的任务

            // 获取当前任务的审批人信息
            List<FlwTaskActor> taskActors = flowLongEngine.queryService().getTaskActorsByTaskId(task.getId());
            if (taskActors != null && !taskActors.isEmpty()) {
                FlowProcessApprovalVo.FlowProcessApprovalContentVo content = new FlowProcessApprovalVo.FlowProcessApprovalContentVo();
                List<FlowProcessApprovalVo.NodeUserVo> nodeUserList = taskActors.stream()
                        .map(actor -> {
                            FlowProcessApprovalVo.NodeUserVo nodeUser = new FlowProcessApprovalVo.NodeUserVo();
                            nodeUser.setId(actor.getActorId());
                            nodeUser.setName(actor.getActorName());
                            return nodeUser;
                        })
                        .collect(Collectors.toList());
                content.setNodeUserList(nodeUserList);
                approval.setContent(content);
            }

            approvals.add(approval);
        }

        return approvals;
    }

    private void setTaskAction(FlowProcessApprovalVo approval, FlwHisTask hisTask) {
        Map<String, Object> variables = hisTask.variableMap();
        if (variables == null) {
            return;
        }
        Object actionValue = variables.get(FlowTaskBiz.TASK_ACTION_VARIABLE);
        if (!(actionValue instanceof Map<?, ?> action)) {
            return;
        }
        approval.setComment(toStringValue(action.get(FlowTaskBiz.COMMENT_VARIABLE)));
        approval.setReason(toStringValue(action.get(FlowTaskBiz.REASON_VARIABLE)));
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    // public void deployById(Integer id) {
    // FlowProcess flowProcess = this.getById(id);
    // flowProcess.setProcessState(1);

    // // deploy flowlong process
    // flowLongEngine.processService().deploy(
    // null,
    // flowProcess.getModelContent(),
    // FlowCreator.of(getCurrentUserId(), BaseContextHandler.getName()),
    // true,
    // process -> {
    // flowProcess.setProcessId(process.getId());
    // flowProcess.setProcessVersion(process.getProcessVersion());

    // this.updateById(flowProcess);
    // }
    // );
    // }

    // public void activeById(Integer id) {
    // lambdaUpdate()
    // .eq(FlowProcess::getId, id)
    // .set(FlowProcess::getProcessState, 1)
    // .update();
    // }

    // public void deactiveById(Integer id) {
    // lambdaUpdate()
    // .eq(FlowProcess::getId, id)
    // .set(FlowProcess::getProcessState, 0)
    // .update();
    // }

}
