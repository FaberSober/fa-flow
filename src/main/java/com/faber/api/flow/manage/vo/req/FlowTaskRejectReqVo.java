package com.faber.api.flow.manage.vo.req;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程任务审批拒绝请求对象
 * 
 * @author xu.pengfei
 * @date 2025-01-02
 */
@Data
public class FlowTaskRejectReqVo implements Serializable {

    /**
     * 任务ID - 必填参数
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 拒绝原因 - 可选参数
     */
    @Size(max = 2000, message = "拒绝原因长度不能超过2000个字符")
    private String reason;

}
