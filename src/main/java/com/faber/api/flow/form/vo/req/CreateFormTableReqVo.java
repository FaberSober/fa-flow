package com.faber.api.flow.form.vo.req;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFormTableReqVo implements Serializable {

    @NotBlank
    @Size(max = 64)
    private String tableName;

    @Size(max = 255)
    private String comment;

}
