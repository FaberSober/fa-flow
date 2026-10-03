package com.faber.api.flow.form.biz;

import java.util.List;
import java.util.Map;

/** 使用已保存的查询配置，兼容未配置查询方式的旧表单。 */
final class FlowFormQueryUtils {
    private FlowFormQueryUtils() {}

    private static Map<?, ?> getQueryConfig(Map<String, Object> tableConfig, String field) {
        if (tableConfig != null && tableConfig.get("query") instanceof Map<?, ?> query
                && query.get("columns") instanceof Iterable<?> columns) {
            for (Object column : columns) {
                if (column instanceof Map<?, ?> config && field.equalsIgnoreCase(String.valueOf(config.get("field")))) return config;
            }
        }
        return Map.of();
    }

    static boolean isExactQuery(Map<String, Object> tableConfig, String field) {
        return "eq".equals(getQueryConfig(tableConfig, field).get("queryType"));
    }

    static void appendConfiguredCondition(StringBuilder sql, List<Object> params, String fieldExpression,
                                          String castType, Map<String, Object> tableConfig, String field, Object value) {
        Map<?, ?> config = getQueryConfig(tableConfig, field);
        String type = String.valueOf(config.get("queryType"));
        boolean multiple = "in".equals(type) || Boolean.TRUE.equals(config.get("multiple"));
        if (!multiple) {
            appendTextCondition(sql, params, fieldExpression, castType, "eq".equals(type), String.valueOf(value));
            return;
        }
        List<String> values = new java.util.ArrayList<>();
        if (value instanceof Iterable<?> items) {
            for (Object item : items) if (item != null && !String.valueOf(item).isBlank()) values.add(String.valueOf(item));
        } else if (value != null) {
            for (String item : String.valueOf(value).split("[,，]")) if (!item.isBlank()) values.add(item.trim());
        }
        values = values.stream().distinct().toList();
        if (values.isEmpty()) return;
        String expression = "CAST(" + fieldExpression + " AS " + castType + ")";
        if ("in".equals(type) || "eq".equals(type)) {
            sql.append(" AND ").append(expression).append(" IN (")
                    .append(String.join(",", java.util.Collections.nCopies(values.size(), "?"))).append(")");
            params.addAll(values);
        } else {
            sql.append(" AND (");
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) sql.append(" OR ");
                sql.append(expression).append(" LIKE ?");
                params.add("%" + values.get(index) + "%");
            }
            sql.append(")");
        }
    }

    // 字段表达式及方言来自调用方，业务值始终使用参数绑定。
    static void appendTextCondition(StringBuilder sql, List<Object> params, String fieldExpression,
                                    String castType, boolean exact, String value) {
        sql.append(" AND CAST(").append(fieldExpression).append(" AS ").append(castType)
                .append(exact ? ") = ?" : ") LIKE ?");
        params.add(exact ? value : "%" + value + "%");
    }
}
