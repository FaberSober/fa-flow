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
        System.out.println("FlowFormQueryUtils checks passed for MySQL/PostgreSQL predicates");
    }

    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
}
