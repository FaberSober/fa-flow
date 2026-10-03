package com.faber.api.flow.form.biz;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.List;
import com.faber.core.exception.BuzzException;
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
        if ("number_range".equals(type)) {
            List<?> bounds = value instanceof List<?> items ? items : java.util.Arrays.asList(String.valueOf(value).split("[,，]", -1));
            if (bounds.size() > 2) throw new BuzzException("数值区间最多填写最小值和最大值");
            BigDecimal min = numericBound(bounds.isEmpty() ? null : bounds.get(0));
            BigDecimal max = numericBound(bounds.size() < 2 ? null : bounds.get(1));
            if (min != null && max != null && min.compareTo(max) > 0) throw new BuzzException("最小值不能大于最大值");
            if (min != null) { sql.append(" AND ").append(fieldExpression).append(" >= ?"); params.add(min); }
            if (max != null) { sql.append(" AND ").append(fieldExpression).append(" <= ?"); params.add(max); }
            return;
        }
        if ("date_range".equals(type)) {
            List<?> bounds = value instanceof List<?> items ? items : java.util.Arrays.asList(String.valueOf(value).split("[,，]", -1));
            if (bounds.size() > 2) throw new BuzzException("日期区间最多填写开始日期和结束日期");
            LocalDate start = dateBound(bounds.isEmpty() ? null : bounds.get(0));
            LocalDate end = dateBound(bounds.size() < 2 ? null : bounds.get(1));
            if (start != null && end != null && start.isAfter(end)) throw new BuzzException("开始日期不能晚于结束日期");
            // 上界采用次日零点之前，包含结束当天的全部时间和小数秒。
            if (start != null) { sql.append(" AND ").append(fieldExpression).append(" >= ?"); params.add(java.sql.Date.valueOf(start)); }
            if (end != null) { sql.append(" AND ").append(fieldExpression).append(" < ?"); params.add(java.sql.Date.valueOf(end.plusDays(1))); }
            return;
        }
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

    private static BigDecimal numericBound(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return new BigDecimal(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { throw new BuzzException("数值区间请输入有效数字"); }
    }

    private static LocalDate dateBound(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return LocalDate.parse(String.valueOf(value).trim()); }
        catch (DateTimeException e) { throw new BuzzException("日期区间请使用有效的 YYYY-MM-DD 日期"); }
    }

    // 字段表达式及方言来自调用方，业务值始终使用参数绑定。
    static void appendTextCondition(StringBuilder sql, List<Object> params, String fieldExpression,
                                    String castType, boolean exact, String value) {
        sql.append(" AND CAST(").append(fieldExpression).append(" AS ").append(castType)
                .append(exact ? ") = ?" : ") LIKE ?");
        params.add(exact ? value : "%" + value + "%");
    }
}
