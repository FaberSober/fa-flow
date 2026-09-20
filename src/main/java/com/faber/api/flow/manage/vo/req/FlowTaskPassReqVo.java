package com.faber.api.flow.manage.vo.req;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程任务审批通过请求对象
 * 
 * @author xu.pengfei
 * @date 2025-01-02
 */
@Data
public class FlowTaskPassReqVo implements Serializable {

    /**
     * 任务ID - 必填参数
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 审批意见 - 可选参数
     */
    @Size(max = 2000, message = "审批意见长度不能超过2000个字符")
    private String comment;

}
