package com.faber.api.flow.form.vo.ret;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * 表列信息
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableColumnVo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** 字段名 */
    private String field;
    
    /** 数据类型（如 varchar(255)） */
    private String type;
    
    /** 纯类型（如 varchar） */
    private String dataType;
    
    /** 字符长度（varchar 专用） */
    private Integer length;
    
    /** 数字精度 */
    private Integer precision;
    
    /** 小数位数 */
    private Integer scale;
    
    /** 是否可空（YES/NO） */
    private String nullable;
    
    /** 默认值 */
    private String defaultValue;
    
    /** 键类型（PRI 表示主键） */
    private String key;
    
    /** 自增等（auto_increment） */
    private String extra;
    
    /** 字段注释（最重要，用于表单 label） */
    private String comment;

    /** 数据库已有的默认表达式暂不支持在设计器中编辑。 */
    private Boolean defaultExpression;

    public Integer getLength() {
        if (length != null) return length;
        if (type != null && ("varchar".equalsIgnoreCase(dataType) || "char".equalsIgnoreCase(dataType)
                || "binary".equalsIgnoreCase(dataType) || "varbinary".equalsIgnoreCase(dataType))) {
            Matcher matcher = Pattern.compile("\\((\\d+)\\)").matcher(type);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        }
        return null;
    }
}
