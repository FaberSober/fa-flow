package com.faber.api.flow.form.vo.ret;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class TableInfoVo implements Serializable {
    
    /** 当前数据源类型：mysql / postgre */
    private String databaseType;

    /** 表名 */
    private String tableName;
    
    /** 表注释 */
    private String tableComment;

    /** 主键 */
    private String pkField;

    private Boolean exist;

    private List<TableColumnVo> columns;
    
}
