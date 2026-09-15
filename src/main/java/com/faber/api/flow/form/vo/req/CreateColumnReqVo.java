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

    @NotNull
    @Valid
    private TableColumnVo column;

}
