package com.faber.api.flow.form.vo.req;

import java.io.Serializable;

import com.faber.api.flow.form.vo.ret.TableColumnVo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateColumnReqVo implements Serializable {

    @NotBlank
    @Size(max = 64)
    private String tableName;

    /** 更新时提供原字段名，第一阶段禁止重命名。 */
    private String originalField;

    @NotNull
    @Valid
    private TableColumnVo column;

}
