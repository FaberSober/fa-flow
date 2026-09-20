package com.faber.api.flow.manage.biz;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.NodeSetType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import com.alibaba.fastjson2.JSONObject;
import com.faber.api.flow.manage.mapper.FlowTaskFaMapper;
import com.faber.api.flow.manage.vo.req.FlowTaskPageReqVo;
import com.faber.api.flow.manage.vo.ret.FlowHisInstanceRet;
import com.faber.api.flow.manage.vo.ret.FlowTaskCountRet;
import com.faber.api.flow.manage.vo.ret.FlowTaskRet;
import com.faber.core.context.BaseContextHandler;
import com.faber.core.exception.BuzzException;
import com.faber.core.vo.msg.TableRet;
import com.faber.core.vo.query.BasePageQuery;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;

@Service
public class FlowTaskBiz {

    static final String TASK_ACTION_VARIABLE = "_faFlowTaskAction";
    static final String COMMENT_VARIABLE = "comment";
    static final String REASON_VARIABLE = "reason";

    @Resource FlowTaskFaMapper flowTaskFaMapper;
    @Resource FlowLongEngine flowLongEngine;

    /**
     * 查询个人名下待审批的task列表
     */
    public TableRet<FlowTaskRet> pagePendingApproval(BasePageQuery<FlowTaskPageReqVo> query) {
        query.getQuery().setActorId(BaseContextHandler.getUserId());
        query.getQuery().setActorType(0);
        PageInfo<FlowTaskRet> info = PageHelper.startPage(query.getCurrent(), query.getPageSize())
                .doSelectPageInfo(() -> flowTaskFaMapper.queryTask(query.getQuery(), query.getSorter()));
        return new TableRet<>(info);
    }

    /**
     * 查询待认领的task列表
     */
    public TableRet<FlowTaskRet> pagePendingClaim(BasePageQuery<FlowTaskPageReqVo> query) {
        query.getQuery().setActorId(BaseContextHandler.getUserId());
        // 设置参与者类型为角色和部门类型
        query.getQuery().setActorType(null); // 不设置单一类型，由SQL中处理IN(1,2)
        PageInfo<FlowTaskRet> info = PageHelper.startPage(query.getCurrent(), query.getPageSize())
                .doSelectPageInfo(() -> flowTaskFaMapper.queryClaimTask(query.getQuery(), query.getSorter()));
        return new TableRet<>(info);
    }

    /**
     * 查询我申请的流程task列表
     */
    public TableRet<FlowHisInstanceRet> pageMyApplications(BasePageQuery<FlowTaskPageReqVo> query) {
        query.getQuery().setCreateId(BaseContextHandler.getUserId());
        PageInfo<FlowHisInstanceRet> info = PageHelper.startPage(query.getCurrent(), query.getPageSize())
                .doSelectPageInfo(() -> flowTaskFaMapper.queryHisInstance(query.getQuery(), query.getSorter()));
        return new TableRet<>(info);
    }

    /**
     * 查询历史流程实例列表
     */
    public TableRet<FlowHisInstanceRet> pageHisInstances(BasePageQuery<FlowTaskPageReqVo> query) {
        // 如果需要查询当前用户的历史流程，可以设置 createId
        // query.getQuery().setCreateId(BaseContextHandler.getUserId());
        
        PageInfo<FlowHisInstanceRet> info = PageHelper.startPage(query.getCurrent(), query.getPageSize())
                .doSelectPageInfo(() -> flowTaskFaMapper.queryHisInstance(query.getQuery(), query.getSorter()));
        return new TableRet<>(info);
    }

    public void pass(Long taskId, String comment) {
        FlwTask flwTask = requireActionableTask(taskId);
        FlowCreator flowCreator = currentFlowCreator();
        Map<String, Object> variables = buildTaskVariables(flwTask, COMMENT_VARIABLE, comment);
        if (!flowLongEngine.executeTask(taskId, flowCreator, variables)) {
            throw new BuzzException("任务办理失败");
        }
    }

    public void reject(Long taskId, String reason) {
        FlwTask flwTask = requireActionableTask(taskId);
        FlowCreator flowCreator = currentFlowCreator();
        Map<String, Object> variables = buildTaskVariables(flwTask, REASON_VARIABLE, reason);
        // false 表示由引擎读取当前节点的 rejectStrategy，避免固定终止流程。
        if (flowLongEngine.executeRejectTask(flwTask, null, flowCreator, variables, false).isEmpty()) {
            throw new BuzzException("任务驳回失败");
        }
    }

    public void claim(Long taskId) {
        FlwTask flwTask = requireActionableTask(taskId);
        FlowCreator flowCreator = currentFlowCreator();
        NodeModel nodeModel = flowLongEngine.taskService().getTaskModel(taskId);
        if (nodeModel == null) {
            throw new BuzzException("当前任务节点不存在");
        }
        Integer setType = nodeModel.getSetType();
        if (NodeSetType.department.eq(setType)) {
            flowLongEngine.taskService().claimDepartment(taskId, flowCreator);
        } else if (NodeSetType.role.eq(setType)) {
            flowLongEngine.taskService().claimRole(taskId, flowCreator);
        } else {
            throw new BuzzException("当前任务不支持认领操作");
        }
    }

    private FlwTask requireActionableTask(Long taskId) {
        if (taskId == null) {
            throw new BuzzException("任务ID不能为空");
        }

        FlwTask flwTask = flowLongEngine.queryService().getTask(taskId);
        if (flwTask == null) {
            throw new BuzzException("任务不存在或已处理");
        }
        if (flwTask.getInstanceId() == null) {
            throw new BuzzException("任务未关联流程实例");
        }

        FlwInstance flwInstance = flowLongEngine.queryService().getInstance(flwTask.getInstanceId());
        if (flwInstance == null || !Objects.equals(flwTask.getInstanceId(), flwInstance.getId())) {
            throw new BuzzException("流程实例不存在或已结束");
        }

        String userId = requireCurrentUserId();
        FlwTaskActor actor = flowLongEngine.taskService().isAllowed(flwTask, userId);
        if (actor == null) {
            throw new BuzzException("当前用户无权办理该任务");
        }

        String tenantId = BaseContextHandler.getTenantId();
        if (StrUtil.isNotBlank(tenantId)
                && (!Objects.equals(tenantId, flwTask.getTenantId())
                || !Objects.equals(tenantId, flwInstance.getTenantId()))) {
            throw new BuzzException("任务不属于当前租户");
        }
        return flwTask;
    }

    private FlowCreator currentFlowCreator() {
        String userId = requireCurrentUserId();
        String userName = BaseContextHandler.getName();
        if (StrUtil.isBlank(userName)) {
            userName = userId;
        }
        FlowCreator flowCreator = FlowCreator.of(userId, userName);
        String tenantId = BaseContextHandler.getTenantId();
        if (StrUtil.isNotBlank(tenantId)) {
            flowCreator.tenantId(tenantId);
        }
        return flowCreator;
    }

    private String requireCurrentUserId() {
        String userId = BaseContextHandler.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new BuzzException("当前用户上下文为空");
        }
        return userId;
    }

    private Map<String, Object> buildTaskVariables(FlwTask flwTask, String actionName, String actionValue) {
        FlwInstance flwInstance = flowLongEngine.queryService().getInstance(flwTask.getInstanceId());
        Map<String, Object> variables = new HashMap<>();
        if (flwInstance != null && StrUtil.isNotBlank(flwInstance.getVariable())) {
            try {
                JSONObject instanceVariables = JSONObject.parseObject(flwInstance.getVariable());
                if (instanceVariables != null) {
                    variables.putAll(instanceVariables);
                }
            } catch (RuntimeException e) {
                throw new BuzzException("流程实例变量格式错误");
            }
        }

        String normalizedActionValue = normalizeActionValue(actionValue);
        if (normalizedActionValue != null) {
            Map<String, Object> action = new HashMap<>();
            action.put(actionName, normalizedActionValue);
            variables.put(TASK_ACTION_VARIABLE, action);
        }
        return variables;
    }

    private String normalizeActionValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
    
    /**
     * 查询我收到的任务列表
     */
    public TableRet<FlowTaskRet> pageMyReceived(BasePageQuery<FlowTaskPageReqVo> query) {
        query.getQuery().setActorId(BaseContextHandler.getUserId());
        query.getQuery().setActorType(0);
        query.getQuery().setTaskType(2); // 设置任务类型为抄送任务
        PageInfo<FlowTaskRet> info = PageHelper.startPage(query.getCurrent(), query.getPageSize())
                .doSelectPageInfo(() -> flowTaskFaMapper.queryHisTask(query.getQuery(), query.getSorter()));
        return new TableRet<>(info);
    }

    /**
     * 查询我已审批的任务列表
     */
    public TableRet<FlowHisInstanceRet> pageMyApproved(BasePageQuery<FlowTaskPageReqVo> query) {
        query.getQuery().setActorId(BaseContextHandler.getUserId());
        query.getQuery().setActorType(0); // 设置参与者类型为用户
        PageInfo<FlowHisInstanceRet> info = PageHelper.startPage(query.getCurrent(), query.getPageSize())
                .doSelectPageInfo(() -> flowTaskFaMapper.queryMyApproved(query.getQuery(), query.getSorter()));
        return new TableRet<>(info);
    }

    /**
     * 查询我的流程任务数量
     */
    public FlowTaskCountRet getMyTaskCount() {
        String currentUserId = BaseContextHandler.getUserId();
        
        FlowTaskCountRet countRet = new FlowTaskCountRet();
        
        // 查询待审批任务数量
        Integer pendingCount = flowTaskFaMapper.countPendingApproval(currentUserId);
        countRet.setPendingApprovalCount(pendingCount != null ? pendingCount : 0);
        
        // 查询我的申请数量
        Integer myAppCount = flowTaskFaMapper.countMyApplications(currentUserId);
        countRet.setMyApplicationCount(myAppCount != null ? myAppCount : 0);
        
        // 查询我收到的任务数量
        Integer myReceivedCount = flowTaskFaMapper.countMyReceived(currentUserId);
        countRet.setMyReceivedCount(myReceivedCount != null ? myReceivedCount : 0);
        
        // 查询认领任务数量
        Integer claimCount = flowTaskFaMapper.countClaimTasks(currentUserId);
        countRet.setClaimTaskCount(claimCount != null ? claimCount : 0);
        
        // 查询已审批任务数量
        Integer auditedCount = flowTaskFaMapper.countAudited(currentUserId);
        countRet.setAuditedCount(auditedCount != null ? auditedCount : 0);
        
        return countRet;
    }

}
