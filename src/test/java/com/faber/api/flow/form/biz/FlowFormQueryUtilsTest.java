package com.faber.api.flow.form.biz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 可用 javac/java 独立运行，不启动 Spring 或数据库。 */
public class FlowFormQueryUtilsTest {
    public static void main(String[] args) {
        Map<String, Object> config = Map.of("query", Map.of("columns", List.of(
                Map.of("field", "order_no", "queryType", "eq"),
                Map.of("field", "customer_name", "queryType", "like"))));
        check(FlowFormQueryUtils.isExactQuery(config, "order_no"), "eq configuration");
        check(!FlowFormQueryUtils.isExactQuery(config, "customer_name"), "like configuration");
        check(!FlowFormQueryUtils.isExactQuery(null, "order_no"), "legacy fallback");
        for (String cast : List.of("CHAR", "TEXT")) {
            StringBuilder sql = new StringBuilder();
            List<Object> params = new ArrayList<>();
            FlowFormQueryUtils.appendTextCondition(sql, params, "t.order_no", cast, true, "A%_'");
            check(sql.toString().equals(" AND CAST(t.order_no AS " + cast + ") = ?"), "exact predicate");
            check(params.equals(List.of("A%_'")), "exact bound value");
            sql.setLength(0);
            params.clear();
            FlowFormQueryUtils.appendTextCondition(sql, params, "t.customer_name", cast, false, "test");
            check(sql.toString().endsWith(") LIKE ?"), "like predicate");
            check(params.equals(List.of("%test%")), "like bound value");
        }
        for (String cast : List.of("CHAR", "TEXT")) {
            for (String type : List.of("in", "eq", "like")) {
                Map<String, Object> multipleConfig = Map.of("query", Map.of("columns", List.of(
                        Map.of("field", "order_no", "queryType", type, "multiple", true))));
                StringBuilder sql = new StringBuilder();
                List<Object> params = new ArrayList<>();
                FlowFormQueryUtils.appendConfiguredCondition(sql, params, "t.order_no", cast, multipleConfig,
                        "order_no", List.of("A", "B", "A"));
                check(!sql.toString().contains("A" + "'"), "parameter binding");
                if ("like".equals(type)) {
                    check(sql.toString().equals(" AND (CAST(t.order_no AS " + cast + ") LIKE ? OR CAST(t.order_no AS " + cast + ") LIKE ?)"), "multiple like predicate");
                    check(params.equals(List.of("%A%", "%B%")), "multiple like values");
                } else {
                    check(sql.toString().equals(" AND CAST(t.order_no AS " + cast + ") IN (?,?)"), "multiple exact predicate");
                    check(params.equals(List.of("A", "B")), "multiple exact values");
                }
                sql.setLength(0);
                params.clear();
                FlowFormQueryUtils.appendConfiguredCondition(sql, params, "t.order_no", cast, multipleConfig, "order_no", List.of());
                check(sql.isEmpty() && params.isEmpty(), "empty selection does not filter");
                FlowFormQueryUtils.appendConfiguredCondition(sql, params, "t.order_no", cast, multipleConfig, "order_no", "A,B，A");
                check(params.size() == 2, "legacy comma defaults");
            }
        }
        Map<String, Object> rangeConfig = Map.of("query", Map.of("columns", List.of(
                Map.of("field", "amount", "queryType", "number_range", "multiple", true))));
        for (String cast : List.of("CHAR", "TEXT")) {
            StringBuilder sql = new StringBuilder();
            List<Object> params = new ArrayList<>();
            FlowFormQueryUtils.appendConfiguredCondition(sql, params, "t.amount", cast, rangeConfig, "amount", List.of("0", "10.50"));
            check(sql.toString().equals(" AND t.amount >= ? AND t.amount <= ?"), "numeric range without text casting");
            check(params.equals(List.of(new java.math.BigDecimal("0"), new java.math.BigDecimal("10.50"))), "decimal binding");
            sql.setLength(0); params.clear();
            FlowFormQueryUtils.appendConfiguredCondition(sql, params, "t.amount", cast, rangeConfig, "amount", ",5");
            check(sql.toString().equals(" AND t.amount <= ?") && params.size() == 1, "upper bound only");
            sql.setLength(0); params.clear();
            FlowFormQueryUtils.appendConfiguredCondition(sql, params, "t.amount", cast, rangeConfig, "amount", "-1,");
            check(sql.toString().equals(" AND t.amount >= ?"), "lower bound only");
            sql.setLength(0); params.clear();
            FlowFormQueryUtils.appendConfiguredCondition(sql, params, "t.amount", cast, rangeConfig, "amount", java.util.Arrays.asList(null, null));
            check(sql.isEmpty() && params.isEmpty(), "cleared range");
            for (String invalid : List.of("10,1", "abc,2", "1,2,3")) {
                boolean rejected = false;
                try { FlowFormQueryUtils.appendConfiguredCondition(sql, params, "t.amount", cast, rangeConfig, "amount", invalid); }
                catch (com.faber.core.exception.BuzzException e) { rejected = true; }
                check(rejected && sql.isEmpty() && params.isEmpty(), "invalid range rejected before adding SQL");
            }
        }
        System.out.println("FlowFormQueryUtils checks passed for MySQL/PostgreSQL predicates");
    }

    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
}
