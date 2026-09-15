package com.faber.api.flow.form.biz;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.faber.core.exception.BuzzException;

import cn.hutool.json.JSONUtil;

/**
 * 动态表单 SQL 构造与 JDBC 执行的安全辅助方法。
 *
 * <p>JDBC 参数只能绑定值，不能绑定表名、列名等标识符。因此所有进入 SQL
 * 结构的标识符必须先经过这里的白名单校验；业务值应继续通过这里的
 * {@link #bindParameters(PreparedStatement, List)} 使用 PreparedStatement 参数绑定。</p>
 */
public final class FlowFormSqlUtils {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    private FlowFormSqlUtils() {
    }

    public static String requireTableName(String tableName) {
        if (tableName == null || tableName.isBlank() || !tableName.startsWith("ff_")) {
            throw new BuzzException("表名必须以ff_开头，并且只能包含字母、数字和下划线");
        }
        return requireIdentifier(tableName, "表名");
    }

    public static String requireIdentifier(String identifier, String label) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new BuzzException(label + "格式不合法，只允许字母、数字和下划线，且必须以字母或下划线开头");
        }
        return identifier;
    }

    public static String quoteIdentifier(String identifier, String label) {
        return "`" + requireIdentifier(identifier, label) + "`";
    }

    /**
     * DDL 中的字符串字面量不能在所有数据库驱动中使用参数占位符，因此仅用于
     * DDL 注释/默认值，并在这里统一转义。数据 CRUD 不得使用此方法拼接业务值。
     */
    public static String quoteDdlLiteral(String value, String label) {
        if (value == null) {
            return "NULL";
        }
        if (value.indexOf('\0') >= 0) {
            throw new BuzzException(label + "不能包含空字符");
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("'", "''")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\u001a", "\\Z");
        return "'" + escaped + "'";
    }

    public static String placeholders(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("占位符数量必须大于0");
        }
        StringBuilder result = new StringBuilder(count * 3 - 1);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append('?');
        }
        return result.toString();
    }

    /**
     * 执行不返回结果集的参数化 SQL。
     */
    public static int executeUpdate(Connection conn, String sql, List<?> params) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            bindParameters(statement, params);
            return statement.executeUpdate();
        }
    }

    /**
     * 执行插入 SQL 并读取数据库生成的主键。
     */
    public static Long executeInsert(Connection conn, String sql, List<?> params) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParameters(statement, params);
            int affectedRows = statement.executeUpdate();
            if (affectedRows == 0) {
                throw new BuzzException("保存表单数据失败");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new BuzzException("保存表单数据后未获取到记录 ID");
                }
                Object key = keys.getObject(1);
                if (key == null) {
                    throw new BuzzException("保存表单数据后未获取到记录 ID");
                }
                if (key instanceof Number number) {
                    return number.longValue();
                }
                return Long.valueOf(String.valueOf(key));
            }
        }
    }

    /**
     * 执行返回单个 long 值的查询。
     */
    public static long queryForLong(Connection conn, String sql, List<?> params) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            bindParameters(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0L;
                }
                return resultSet.getLong(1);
            }
        }
    }

    /**
     * 执行参数化查询并将结果集转换为列名到值的 Map 列表。
     */
    public static List<Map<String, Object>> queryForMaps(Connection conn, String sql, List<?> params)
            throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            bindParameters(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                int columnCount = metadata.getColumnCount();
                List<Map<String, Object>> result = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metadata.getColumnLabel(i), resultSet.getObject(i));
                    }
                    result.add(row);
                }
                return result;
            }
        }
    }

    /**
     * 为 PreparedStatement 绑定参数。集合、数组和 Map 类型按 JSON 字符串写入，
     * 以保持动态表单字段的原有存储方式。
     */
    public static void bindParameters(PreparedStatement statement, List<?> params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value == null) {
                statement.setObject(i + 1, null);
            } else if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
                statement.setObject(i + 1, JSONUtil.toJsonStr(value));
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    /**
     * 执行不带业务参数的 DDL。
     */
    public static void executeDdl(DataSource dataSource, String sql) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement statement = conn.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
