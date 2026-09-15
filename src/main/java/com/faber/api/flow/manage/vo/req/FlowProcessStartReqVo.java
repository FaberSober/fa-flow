package com.faber.api.flow.manage.vo.req;

import java.io.Serializable;
import java.util.Map;

import com.alibaba.excel.annotation.ExcelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class FlowProcessStartReqVo implements Serializable {

    @NotNull
    @ExcelProperty("流程定义ID")
    private Long processId;

    /**
     * 同一逻辑提交请求的幂等键。客户端重试时必须复用该值。
     */
    @NotBlank
    @Size(max = 64)
    private String requestId;

    @Deprecated
    @ExcelProperty("流程定义 key 唯一标识")
    private String processKey;

    /**
     * 流程实例的业务数据
     */
    private Map<String, Object> args;

}
