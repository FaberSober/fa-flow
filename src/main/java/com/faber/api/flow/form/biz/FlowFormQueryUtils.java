package com.faber.api.flow.form.biz;

import java.util.List;
import java.util.Map;

/** 使用已保存的查询配置，兼容未配置查询方式的旧表单。 */
final class FlowFormQueryUtils {
    private FlowFormQueryUtils() {}

    static boolean isExactQuery(Map<String, Object> tableConfig, String field) {
        if (tableConfig == null || !(tableConfig.get("query") instanceof Map<?, ?> query)
                || !(query.get("columns") instanceof Iterable<?> columns)) return false;
        for (Object column : columns) {
            if (column instanceof Map<?, ?> config && field.equalsIgnoreCase(String.valueOf(config.get("field")))) {
                return "eq".equals(config.get("queryType"));
            }
        }
        return false;
    }

    // 字段表达式及方言来自调用方，业务值始终使用参数绑定。
    static void appendTextCondition(StringBuilder sql, List<Object> params, String fieldExpression,
                                    String castType, boolean exact, String value) {
        sql.append(" AND CAST(").append(fieldExpression).append(" AS ").append(castType)
                .append(exact ? ") = ?" : ") LIKE ?");
        params.add(exact ? value : "%" + value + "%");
    }
}
