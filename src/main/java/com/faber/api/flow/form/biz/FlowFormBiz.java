package com.faber.api.flow.form.biz;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faber.api.flow.form.entity.FlowForm;
import com.faber.api.flow.form.entity.FlowFormTable;
import com.faber.api.flow.form.mapper.FlowFormMapper;
import com.faber.api.flow.form.vo.config.FlowFormConfig;
import com.faber.api.flow.form.vo.config.FlowFormDataConfig;
import com.faber.api.flow.form.vo.config.FlowFormItem;
import com.faber.api.flow.form.vo.req.CreateColumnReqVo;
import com.faber.api.flow.form.vo.req.CreateFormTableReqVo;
import com.faber.api.flow.form.vo.req.SaveFormDataReqVo;
import com.faber.api.flow.form.vo.ret.TableColumnVo;
import com.faber.api.flow.form.vo.ret.TableInfoVo;
import com.faber.api.flow.manage.biz.FlowCatagoryBiz;
import com.faber.api.flow.manage.entity.FlowCatagory;
import com.faber.core.exception.BuzzException;
import com.faber.core.service.FaFlowService;
import com.faber.core.vo.msg.TableRet;
import com.faber.core.vo.query.QueryParams;
import com.faber.core.vo.query.Sorter;
import com.faber.core.vo.utils.FaOption;
import com.faber.core.web.biz.BaseBiz;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.meta.MetaUtil;
import cn.hutool.db.meta.Table;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;

/**
 * FLOW-流程表单
 *
 * @author xu.pengfei
 * @email 1508075252@qq.com
 * @date 2025-12-16 15:43:41
 */
@Service
public class FlowFormBiz extends BaseBiz<FlowFormMapper,FlowForm> implements FaFlowService {

    @Resource DataSource dataSource;
    @Resource FlowFormTableBiz flowFormTableBiz;
    @Resource FlowCatagoryBiz flowCatagoryBiz;

    private static final Set<String> DATA_TYPES_LENGTH = Set.of(
            "varchar", "char", "varbinary", "binary", "int", "bigint",
            "tinyint", "smallint", "mediumint");
    private static final Set<String> DATA_TYPES_PRECISION = Set.of("decimal", "numeric");
    private static final Set<String> ALLOWED_DATA_TYPES = Set.of(
            "varchar", "char", "varbinary", "binary", "int", "bigint", "tinyint",
            "smallint", "mediumint", "decimal", "numeric", "float", "double", "text",
            "datetime", "date", "timestamp", "json");
    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "id", "tenant_id", "flow_instance_id", "crt_time", "crt_user",
            "upd_time", "upd_user", "deleted");
    private static final int MAX_DDL_TEXT_LENGTH = 255;
    private static final int MAX_QUERY_VALUE_LENGTH = 1000;
    private static final int MAX_QUERY_CONDITIONS = 50;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_BATCH_SIZE = 1000;

    @Override
    public void decorateOne(FlowForm i) {
        FlowCatagory flowCatagory = flowCatagoryBiz.getByIdWithCache(i.getCatagoryId());
        if (flowCatagory != null) {
            i.setCatagoryName(flowCatagory.getName());
        }
    }

    public void createFormTable(CreateFormTableReqVo reqVo) throws SQLException {
        String tableName = FlowFormSqlUtils.requireTableName(reqVo.getTableName());
        String comment = reqVo.getComment();
        validateDdlText(comment, "表注释");

        // 创建基础表
        String createTableSql = String.format(
                "CREATE TABLE %s (\n" +
                "  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',\n" +
                "  `flow_instance_id` bigint(20) DEFAULT NULL COMMENT '流程实例ID',\n" +
                "  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',\n" +
                "  `crt_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `crt_user` varchar(32) NOT NULL COMMENT '创建用户ID',\n" +
                "  `upd_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `upd_user` varchar(32) DEFAULT NULL COMMENT '更新用户ID',\n" +
                "  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=%s",
                FlowFormSqlUtils.quoteIdentifier(tableName, "表名"),
                FlowFormSqlUtils.quoteDdlLiteral(comment == null ? "" : comment, "表注释")
        );

        FlowFormSqlUtils.executeDdl(dataSource, createTableSql);
    }

    public TableInfoVo queryTableStructure(String tableName) throws SQLException {
        tableName = FlowFormSqlUtils.requireTableName(tableName);
        TableInfoVo tableInfo = new TableInfoVo();
        tableInfo.setTableName(tableName);

        // 使用 Hutool 的 MetaUtil 来获取表结构
        Table tableMeta = MetaUtil.getTableMeta(dataSource, tableName);
        if (tableMeta == null) {
            tableInfo.setExist(false);
            return tableInfo;
        }
        tableInfo.setExist(true);
        
        // 尝试从Hutool获取表注释
        String tableComment = tableMeta.getComment();
        // 如果Hutool获取不到，使用SQL查询获取
        if (tableComment == null || tableComment.isEmpty()) {
            tableComment = baseMapper.getTableComment(tableName);
        }
        tableInfo.setTableComment(tableComment);
        
        // getPkNames() 返回 Set<String>，需要转换为 String
        // 通常表只有一个主键，取第一个
        Set<String> pkNames = tableMeta.getPkNames();
        String pkField = (pkNames != null && !pkNames.isEmpty()) ? pkNames.iterator().next() : null;
        tableInfo.setPkField(pkField);
        
        // List<TableColumnVo> columns = new ArrayList<>();
        // for (Column column : tableMeta.getColumns()) {
        //     TableColumnVo tableColumnVo = new TableColumnVo();
        //     tableColumnVo.setField(column.getName());
        //     tableColumnVo.setDataType(column.getTypeName());
        //     tableColumnVo.setComment(column.getComment());
        //     columns.add(tableColumnVo);
        // }
        // tableInfo.setColumns(columns);


        List<TableColumnVo> columns = baseMapper.getTableColumns(tableName);
        tableInfo.setColumns(columns);

        // 获取主键字段
        // String pkField = null;
        // for (TableColumnVo column : columns) {
        //     if ("PRI".equalsIgnoreCase(column.getKey())) {
        //         pkField = column.getField();
        //         break;
        //     }
        // }
        // tableInfo.setPkField(pkField);

        return tableInfo;
    }

    public void createColumn(CreateColumnReqVo reqVo) throws SQLException {
        String tableName = requireManagedTableName(reqVo.getTableName());
        TableColumnVo column = reqVo.getColumn();
        ensureTableExists(tableName);
        String columnName = validateMutableColumn(column);

        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ")
                .append(FlowFormSqlUtils.quoteIdentifier(tableName, "表名"))
                .append(" ADD COLUMN ")
                .append(FlowFormSqlUtils.quoteIdentifier(columnName, "字段名"))
                .append(' ')
                .append(buildColumnDefinition(column));

        FlowFormSqlUtils.executeDdl(dataSource, sb.toString());
    }


    public void updateColumn(CreateColumnReqVo reqVo) throws SQLException {
        String tableName = requireManagedTableName(reqVo.getTableName());
        TableColumnVo column = reqVo.getColumn();
        ensureTableExists(tableName);
        String columnName = validateMutableColumn(column);
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ")
                .append(FlowFormSqlUtils.quoteIdentifier(tableName, "表名"))
                .append(" MODIFY COLUMN ")
                .append(FlowFormSqlUtils.quoteIdentifier(columnName, "字段名"))
                .append(' ')
                .append(buildColumnDefinition(column));
        FlowFormSqlUtils.executeDdl(dataSource, sb.toString());
    }

    public void deleteColumn(com.faber.api.flow.form.vo.req.DeleteColumnReqVo reqVo) throws SQLException {
        String tableName = requireManagedTableName(reqVo.getTableName());
        ensureTableExists(tableName);
        String columnName = FlowFormSqlUtils.requireIdentifier(reqVo.getColumn(), "字段名");
        if (SYSTEM_FIELDS.contains(columnName.toLowerCase(Locale.ROOT))) {
            throw new BuzzException("系统字段不允许删除: " + columnName);
        }

        String sql = "ALTER TABLE " + FlowFormSqlUtils.quoteIdentifier(tableName, "表名")
                + " DROP COLUMN " + FlowFormSqlUtils.quoteIdentifier(columnName, "字段名");
        FlowFormSqlUtils.executeDdl(dataSource, sql);
    }

    public SaveFormDataReqVo saveFormData(SaveFormDataReqVo reqVo) throws SQLException {
        saveFormData(reqVo.getFormId(), reqVo.getFormData());
        return reqVo;
    }

    public SaveFormDataReqVo updateFormData(SaveFormDataReqVo reqVo) throws SQLException {
        updateFormData(reqVo.getFormId(), reqVo.getFormData());
        return reqVo;
    }

    public Map<String, Object> updateFormData(Integer formId, Map<String, Object> formData) throws SQLException {
        FlowForm flowForm = getById(formId);
        if (flowForm == null) {
            throw new BuzzException("表单不存在,formId=" + formId);
        }
        if (formData == null) {
            throw new BuzzException("表单数据不能为空");
        }

        // 检查是否有 id 字段
        if (!formData.containsKey("id")) {
            throw new BuzzException("更新数据必须包含 id 字段");
        }

        // 解析表单布局,获取子表配置
        Map<String, Object> configMap = flowForm.getConfig();
        FlowFormConfig flowFormConfig = JSONUtil.toBean(JSONUtil.toJsonStr(configMap), FlowFormConfig.class);

        // 获取并校验主表配置。表名来自已保存的流程表单元数据，不信任请求中的表名。
        FlowFormDataConfig.Table mainTable = requireMainTableConfig(flowForm);

        // 更新主表数据
        try (Connection conn = dataSource.getConnection()) {
            Long mainTableId = update(conn, mainTable, formData);

            // 更新子表数据
            List<FlowFormItem> subTableItems = flowFormConfig.getAllSubTableItems();
            for (FlowFormItem item : subTableItems) {
                String subTableName = item.getSubtable_tableName();
                List<Map<String, Object>> tableData = (List<Map<String, Object>>) formData.get(item.getName());

                // 从数据库查找子表配置
                FlowFormTable flowFormTable = flowFormTableBiz.getLinkTable(flowForm.getId(), subTableName);

                if (flowFormTable == null) {
                    throw new BuzzException("子表配置不存在: " + subTableName);
                }

                String fkField = FlowFormSqlUtils.requireIdentifier(flowFormTable.getForeignKey(), "子表外键字段");
                FlowFormDataConfig.Table tableConfig = validateTableConfig(flowFormTable.getDataConfig(), "子表配置");

                // 跳过空数据
                if (tableData == null || tableData.isEmpty()) {
                    // 删除所有子表数据（没有传数据表示清空）
                    FlowFormSqlUtils.executeUpdate(conn,
                            "UPDATE " + quoteTable(tableConfig.getTableName())
                                    + " SET `deleted` = ?, `upd_time` = CURRENT_TIMESTAMP, `upd_user` = ?"
                                    + " WHERE " + quoteColumn(fkField) + " = ? AND `deleted` = ?",
                            Arrays.asList(true, getCurrentUserId(), mainTableId, false));
                    continue;
                }

                // 收集所有有id的数据和没有id的数据
                List<Long> updateIds = new ArrayList<>();
                List<Map<String, Object>> insertDataList = new ArrayList<>();
                List<Map<String, Object>> updateDataList = new ArrayList<>();

                for (Map<String, Object> rowData : tableData) {
                    if (rowData == null) {
                        throw new BuzzException("子表数据行不能为空");
                    }
                    // 清理前端临时字段（以_开头的字段）
                    rowData.entrySet().removeIf(entry -> entry.getKey() != null && entry.getKey().startsWith("_"));

                    Object idObj = rowData.get("id");
                    if (idObj != null) {
                        // 有id，需要更新
                        Long id = parseRecordId(idObj);
                        updateIds.add(id);
                        updateDataList.add(rowData);
                    } else {
                        // 没有id，需要插入
                        insertDataList.add(rowData);
                    }
                }

                // 1. 删除不在更新列表中的数据
                StringBuilder deleteSql = new StringBuilder()
                        .append("UPDATE ").append(quoteTable(tableConfig.getTableName()))
                        .append(" SET `deleted` = ?, `upd_time` = CURRENT_TIMESTAMP, `upd_user` = ?")
                        .append(" WHERE ").append(quoteColumn(fkField)).append(" = ?");
                List<Object> deleteParams = new ArrayList<>(Arrays.asList(true, getCurrentUserId(), mainTableId));
                if (!updateIds.isEmpty()) {
                    deleteSql.append(" AND `id` NOT IN (")
                            .append(FlowFormSqlUtils.placeholders(updateIds.size()))
                            .append(')');
                    deleteParams.addAll(updateIds);
                }
                deleteSql.append(" AND `deleted` = ?");
                deleteParams.add(false);
                FlowFormSqlUtils.executeUpdate(conn, deleteSql.toString(), deleteParams);

                // 2. 更新已有数据
                for (Map<String, Object> rowData : updateDataList) {
                    update(conn, tableConfig, rowData);
                }

                // 3. 插入新数据
                for (Map<String, Object> rowData : insertDataList) {
                    rowData.put(fkField, mainTableId);
                    save(conn, tableConfig, rowData);
                }
            }
        }

        return formData;
    }

    public Map<String, Object> saveFormData(Integer formId, Map<String, Object> formData) throws SQLException {
        FlowForm flowForm = getById(formId);
        if (flowForm == null) {
            throw new BuzzException("表单不存在，formId=" + formId);
        }
        if (formData == null) {
            throw new BuzzException("表单数据不能为空");
        }

        // 解析表单布局，获取子表配置
        Map<String, Object> configMap = flowForm.getConfig();
        FlowFormConfig flowFormConfig = JSONUtil.toBean(JSONUtil.toJsonStr(configMap), FlowFormConfig.class);

        // 获取并校验主表配置。表名来自已保存的流程表单元数据，不信任请求中的表名。
        FlowFormDataConfig.Table mainTable = requireMainTableConfig(flowForm);

        // 保存主表数据
        try (Connection conn = dataSource.getConnection()) {
            Long mainTableId = save(conn, mainTable, formData);

            // 保存子表数据
            List<FlowFormItem> subTableItems = flowFormConfig.getAllSubTableItems();
            for (FlowFormItem item : subTableItems) {
                String subTableName = item.getSubtable_tableName();
                List<Map<String, Object>> tableData = (List<Map<String, Object>>) formData.get(item.getName());

                // 跳过空数据
                if (tableData == null || tableData.isEmpty()) {
                    continue;
                }

                // 从数据库查找子表配置
                FlowFormTable flowFormTable = flowFormTableBiz.getLinkTable(flowForm.getId(), subTableName);

                if (flowFormTable == null) {
                    throw new BuzzException("子表配置不存在: " + subTableName);
                }

                String fkField = FlowFormSqlUtils.requireIdentifier(flowFormTable.getForeignKey(), "子表外键字段");
                FlowFormDataConfig.Table tableConfig = validateTableConfig(flowFormTable.getDataConfig(), "子表配置");

                // 添加fkField字段并生成参数化 insert SQL
                for (Map<String, Object> rowData : tableData) {
                    if (rowData == null) {
                        throw new BuzzException("子表数据行不能为空");
                    }
                    rowData.entrySet().removeIf(entry -> entry.getKey() != null && entry.getKey().startsWith("_"));
                    rowData.put(fkField, mainTableId);
                    save(conn, tableConfig, rowData);
                }
            }
        }

        return formData;
    }

    /**
     * 保存单表数据
     * 
     * @param conn 数据库连接
     * @param tableConfig 表配置
     * @param data 要保存的数据
     * @param fkField 外键字段名（子表使用，主表传null）
     * @param fkValue 外键值（子表使用，主表传null）
     * @return 主键ID（主表）或null（子表）
     * @throws SQLException
     */
    private Long save(Connection conn, FlowFormDataConfig.Table tableConfig, Map<String, Object> data) throws SQLException {
        validateTableConfig(tableConfig, "表配置");
        if (data == null) {
            throw new BuzzException("表单数据不能为空");
        }

        String userId = getCurrentUserId();
        String tableName = tableConfig.getTableName();

        List<String> fields = new ArrayList<>();
        List<String> values = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (FlowFormDataConfig.Column column : tableConfig.getColumns()) {
            String field = FlowFormSqlUtils.requireIdentifier(column.getField(), "字段名");

            // 系统字段由服务端维护，客户端不能覆盖。
            if (SYSTEM_FIELDS.contains(field.toLowerCase(Locale.ROOT))) {
                if ("crt_time".equalsIgnoreCase(field) || "upd_time".equalsIgnoreCase(field)) {
                    fields.add(quoteColumn(field));
                    values.add("CURRENT_TIMESTAMP");
                } else if ("crt_user".equalsIgnoreCase(field) || "upd_user".equalsIgnoreCase(field)) {
                    fields.add(quoteColumn(field));
                    values.add("?");
                    params.add(userId);
                } else if ("deleted".equalsIgnoreCase(field)) {
                    fields.add(quoteColumn(field));
                    values.add("?");
                    params.add(false);
                }
                continue;
            }

            if (data.containsKey(field)) {
                Object value = data.get(field);
                if (value != null) {
                    fields.add(quoteColumn(field));
                    values.add("?");
                    params.add(value);
                }
            }
        }

        if (fields.isEmpty()) {
            throw new BuzzException("没有可保存的表单字段");
        }

        String sql = "INSERT INTO " + quoteTable(tableName)
                + " (" + String.join(", ", fields) + ") VALUES ("
                + String.join(", ", values) + ")";
        Long id = FlowFormSqlUtils.executeInsert(conn, sql, params);
        data.put("id", id);
        return id;
    }

    /**
     * 更新单表数据
     * 
     * @param conn 数据库连接
     * @param tableConfig 表配置
     * @param data 要更新的数据(必须包含id字段)
     * @return 主键ID
     * @throws SQLException
     */
    private Long update(Connection conn, FlowFormDataConfig.Table tableConfig, Map<String, Object> data) throws SQLException {
        validateTableConfig(tableConfig, "表配置");
        if (data == null) {
            throw new BuzzException("表单数据不能为空");
        }

        String userId = getCurrentUserId();
        String tableName = tableConfig.getTableName();

        // 获取ID
        Object idObj = data.get("id");
        Long id = parseRecordId(idObj);

        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (FlowFormDataConfig.Column column : tableConfig.getColumns()) {
            String field = FlowFormSqlUtils.requireIdentifier(column.getField(), "字段名");

            if ("upd_time".equalsIgnoreCase(field)) {
                assignments.add(quoteColumn(field) + " = CURRENT_TIMESTAMP");
                continue;
            }
            if ("upd_user".equalsIgnoreCase(field)) {
                assignments.add(quoteColumn(field) + " = ?");
                params.add(userId);
                continue;
            }
            if (SYSTEM_FIELDS.contains(field.toLowerCase(Locale.ROOT))) {
                continue;
            }

            if (data.containsKey(field)) {
                Object value = data.get(field);
                // 只更新非null值的字段
                if (value != null) {
                    assignments.add(quoteColumn(field) + " = ?");
                    params.add(value);
                }
            }
        }

        // 确保至少更新 upd_time 和 upd_user
        if (assignments.stream().noneMatch(item -> item.startsWith(quoteColumn("upd_time")))) {
            assignments.add(quoteColumn("upd_time") + " = CURRENT_TIMESTAMP");
        }
        if (assignments.stream().noneMatch(item -> item.startsWith(quoteColumn("upd_user")))) {
            assignments.add(quoteColumn("upd_user") + " = ?");
            params.add(userId);
        }

        String sql = "UPDATE " + quoteTable(tableName) + " SET " + String.join(", ", assignments)
                + " WHERE " + quoteColumn("id") + " = ? AND " + quoteColumn("deleted") + " = false";
        params.add(id);
        int affectedRows = FlowFormSqlUtils.executeUpdate(conn, sql, params);

        if (affectedRows == 0) {
            throw new BuzzException("数据不存在或已被删除,id=" + id);
        }
        
        return id;
    }

    /**
     * 更新业务数据的流程ID
     * @param formId
     * @param formDataId
     * @param flowInstanceId
     */
    public void updateDataFlowInstanceId(Integer formId, Long formDataId, Long flowInstanceId) {
        try {
            // 获取formId的配置
            FlowForm flowForm = getById(formId);
            if (flowForm == null) {
                throw new BuzzException("表单不存在，formId=" + formId);
            }

            FlowFormDataConfig.Table mainTable = requireMainTableConfig(flowForm);
            String tableName = mainTable.getTableName();

            // 判断主表是否有flow_instance_id字段
            TableInfoVo tableInfo = queryTableStructure(tableName);
            if (tableInfo.getExist() == null || !tableInfo.getExist()) {
                throw new BuzzException("表不存在，tableName=" + tableName);
            }

            // 检查是否有flow_instance_id字段
            boolean hasFlowInstanceIdField = tableInfo.getColumns().stream()
                    .anyMatch(column -> "flow_instance_id".equals(column.getField()));

            // 如果有，更新为flowInstanceId
            if (hasFlowInstanceIdField) {
                String sql = "UPDATE " + quoteTable(tableName)
                        + " SET " + quoteColumn("flow_instance_id") + " = ?, "
                        + quoteColumn("upd_time") + " = CURRENT_TIMESTAMP, "
                        + quoteColumn("upd_user") + " = ?"
                        + " WHERE " + quoteColumn("id") + " = ? AND "
                        + quoteColumn("deleted") + " = false";

                int affectedRows;
                try (Connection conn = dataSource.getConnection()) {
                    affectedRows = FlowFormSqlUtils.executeUpdate(conn, sql,
                            Arrays.asList(flowInstanceId, getCurrentUserId(), formDataId));
                }
                if (affectedRows == 0) {
                    throw new BuzzException("数据不存在或已被删除，id=" + formDataId);
                }
            }
            // 如果没有flow_process_id字段，不更新，不抛出异常
        } catch (SQLException e) {
            throw new BuzzException("更新业务数据流程ID失败: " + e.getMessage());
        }
    }

    /**
     * 自定义表单分页查询
     * @param query
     * @return
     */
    public TableRet<Map<String, Object>> pageFormData(QueryParams query) {
        if (query == null || query.getFlowFormId() == null) throw new BuzzException("FlowFormId is NULL.");
        if (query.getCurrent() < 1) throw new BuzzException("当前页码必须大于0");
        if (query.getPageSize() < 1 || query.getPageSize() > MAX_PAGE_SIZE) {
            throw new BuzzException("每页条数必须在1到" + MAX_PAGE_SIZE + "之间");
        }
        FlowForm flowForm = this.getById(query.getFlowFormId());
        if (flowForm == null) throw new BuzzException("FlowForm Not Found." + query.getFlowFormId());
        FlowFormDataConfig.Table mainTable = requireMainTableConfig(flowForm);

        String tableName = mainTable.getTableName();
        TableInfoVo tableInfo;
        try {
            tableInfo = queryTableStructure(tableName);
        } catch (SQLException e) {
            throw new BuzzException("查询表结构失败: " + e.getMessage());
        }
        if (!Boolean.TRUE.equals(tableInfo.getExist())) {
            throw new BuzzException("表不存在，tableName=" + tableName);
        }

        Set<String> readableFields = new HashSet<>();
        if (tableInfo.getColumns() != null) {
            for (TableColumnVo column : tableInfo.getColumns()) {
                readableFields.add(FlowFormSqlUtils.requireIdentifier(column.getField(), "字段名").toLowerCase(Locale.ROOT));
            }
        }
        boolean hasFlowInstanceIdField = flowForm.getFlowProcessId() != null
                && readableFields.contains("flow_instance_id");

        StringBuilder fromSql = new StringBuilder(" FROM ")
                .append(quoteTable(tableName))
                .append(" t ");

        if (hasFlowInstanceIdField) {
            fromSql.append("LEFT JOIN flw_his_instance fhi ON t.")
                    .append(quoteColumn("flow_instance_id"))
                    .append(" = fhi.id ");
        }

        StringBuilder whereSql = new StringBuilder(" WHERE t.")
                .append(quoteColumn("deleted"))
                .append(" = false ");
        List<Object> params = new ArrayList<>();

        // 只允许查询实际存在于该动态表中的字段，字段名本身不作为值拼接。
        if (query.getQuery() != null && !query.getQuery().isEmpty()) {
            if (query.getQuery().size() > MAX_QUERY_CONDITIONS) {
                throw new BuzzException("查询条件不能超过" + MAX_QUERY_CONDITIONS + "个");
            }
            for (Map.Entry<String, Object> entry : query.getQuery().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value == null) continue;
                String field = normalizeQueryField(key);
                if (!readableFields.contains(field.toLowerCase(Locale.ROOT))) {
                    throw new BuzzException("不允许查询字段: " + key);
                }
                String textValue = String.valueOf(value);
                if (textValue.length() > MAX_QUERY_VALUE_LENGTH) {
                    throw new BuzzException("查询值长度超过限制");
                }
                whereSql.append(" AND t.")
                        .append(quoteColumn(field))
                        .append(" LIKE ?");
                params.add("%" + textValue + "%");
            }
        }

        StringBuilder orderSql = new StringBuilder();
        List<Sorter> sorterList = query.getSorterInfo();
        if (!sorterList.isEmpty()) {
            orderSql.append(" ORDER BY ");
            for (int i = 0; i < sorterList.size(); i++) {
                Sorter sorter = sorterList.get(i);
                String field = normalizeQueryField(sorter.getField());
                if (!readableFields.contains(field.toLowerCase(Locale.ROOT))) {
                    throw new BuzzException("不允许排序字段: " + sorter.getField());
                }
                if (i > 0) {
                    orderSql.append(", ");
                }
                orderSql.append("t.")
                        .append(quoteColumn(field))
                        .append(sorter.isAsc() ? " ASC" : " DESC");
            }
        }

        String fromWhereSql = fromSql.toString() + whereSql;
        String countSql = "SELECT COUNT(*)" + fromWhereSql;
        String selectSql = "SELECT t.*"
                + (hasFlowInstanceIdField ? ", fhi.current_node_name, fhi.current_node_key, fhi.instance_state " : " ")
                + fromWhereSql
                + orderSql
                + " LIMIT ? OFFSET ?";

        try (Connection conn = dataSource.getConnection()) {
            long total = FlowFormSqlUtils.queryForLong(conn, countSql, params);
            List<Object> pageParams = new ArrayList<>(params);
            pageParams.add(query.getPageSize());
            pageParams.add((long) (query.getCurrent() - 1) * query.getPageSize());
            List<Map<String, Object>> rows = FlowFormSqlUtils.queryForMaps(conn, selectSql, pageParams);

            Page<Map<String, Object>> page = new Page<>(query.getCurrent(), query.getPageSize(), total);
            page.setRecords(rows);
            return new TableRet<>(page);
        } catch (SQLException e) {
            throw new BuzzException("查询表单数据失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询表单数据详情
     * @param flowFormId 表单ID
     * @param id 数据ID
     * @return 表单数据详情（包含主表数据和子表数据）
     * @throws SQLException
     */
    public Map<String, Object> getFormDataDetailById(Integer flowFormId, String id) throws SQLException {
        // 查询流程表单配置
        FlowForm flowForm = getById(flowFormId);
        if (flowForm == null) {
            throw new BuzzException("表单不存在，formId=" + flowFormId);
        }

        FlowFormDataConfig.Table mainTable = requireMainTableConfig(flowForm);
        String mainTableName = mainTable.getTableName();
        Long recordId = parseRecordId(id);

        // 查询主表数据
        String sql = "SELECT * FROM " + quoteTable(mainTableName)
                + " WHERE " + quoteColumn("id") + " = ? AND " + quoteColumn("deleted") + " = false";

        try (Connection conn = dataSource.getConnection()) {
            List<Map<String, Object>> list = FlowFormSqlUtils.queryForMaps(conn, sql, List.of(recordId));

            if (list == null || list.isEmpty()) {
                throw new BuzzException("数据不存在，id=" + id);
            }

            Map<String, Object> mainData = list.get(0);

            // 解析表单布局，获取子表配置
            Map<String, Object> configMap = flowForm.getConfig();
            FlowFormConfig flowFormConfig = JSONUtil.toBean(JSONUtil.toJsonStr(configMap), FlowFormConfig.class);

            // 查询子表数据
            List<FlowFormItem> subTableItems = flowFormConfig.getAllSubTableItems();
            for (FlowFormItem item : subTableItems) {
                String subTableName = item.getSubtable_tableName();

                // 从数据库查找子表配置
                FlowFormTable flowFormTable = flowFormTableBiz.getLinkTable(flowForm.getId(), subTableName);

                if (flowFormTable == null) {
                    throw new BuzzException("子表配置不存在: " + subTableName);
                }

                String fkField = FlowFormSqlUtils.requireIdentifier(flowFormTable.getForeignKey(), "子表外键字段");
                FlowFormDataConfig.Table tableConfig = validateTableConfig(flowFormTable.getDataConfig(), "子表配置");

                // 查询子表数据
                String subSql = "SELECT * FROM " + quoteTable(tableConfig.getTableName())
                        + " WHERE " + quoteColumn(fkField) + " = ? AND " + quoteColumn("deleted") + " = false";
                List<Map<String, Object>> subList = FlowFormSqlUtils.queryForMaps(conn, subSql, List.of(recordId));

                // 将子表数据添加到主数据中
                mainData.put(item.getName(), subList);
            }

            return mainData;
        }
    }

    public void removeFormDataById(Integer flowFormId, String id) throws SQLException {
        this.removeFormDataByIds(flowFormId, Arrays.asList(id));
    }

    public void removeFormDataByIds(Integer flowFormId, List<String> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            throw new BuzzException("ids is NULL.");
        }
        // 查询流程表单配置
        FlowForm flowForm = getById(flowFormId);
        if (flowForm == null) {
            throw new BuzzException("表单不存在，formId=" + flowFormId);
        }

        FlowFormDataConfig.Table mainTable = requireMainTableConfig(flowForm);
        String mainTableName = mainTable.getTableName();
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BuzzException("单次最多删除" + MAX_BATCH_SIZE + "条数据");
        }
        List<Long> recordIds = ids.stream().map(this::parseRecordId).toList();

        // 组装参数化软删除 SQL
        String sql = "UPDATE " + quoteTable(mainTableName)
                + " SET " + quoteColumn("deleted") + " = ?, "
                + quoteColumn("upd_time") + " = CURRENT_TIMESTAMP, "
                + quoteColumn("upd_user") + " = ? WHERE "
                + quoteColumn("id") + " IN (" + FlowFormSqlUtils.placeholders(recordIds.size()) + ") AND "
                + quoteColumn("deleted") + " = false";
        List<Object> params = new ArrayList<>();
        params.add(true);
        params.add(getCurrentUserId());
        params.addAll(recordIds);

        int affectedRows;
        try (Connection conn = dataSource.getConnection()) {
            affectedRows = FlowFormSqlUtils.executeUpdate(conn, sql, params);
        }
        if (affectedRows == 0) {
            throw new BuzzException("数据不存在或已被删除，id=" + ids);
        }

        // TODO 删除子表数据
    }

    public List<FaOption<String>> getFlowMenuList() {
        List<FlowForm> formList = lambdaQuery().orderByAsc(FlowForm::getSort).list();
        List<FaOption<String>> list = new ArrayList<>();
        for (FlowForm flowForm : formList) {
            FaOption<String> option = new FaOption<String>();
            option.setId("/admin/flow/view/form/" + flowForm.getId());
            option.setName(flowForm.getName());
            list.add(option);
        }
        return list;
    }

    private FlowFormDataConfig.Table requireMainTableConfig(FlowForm flowForm) {
        if (flowForm == null) {
            throw new BuzzException("流程表单不存在");
        }
        FlowFormDataConfig dataConfig = flowForm.getDataConfig();
        if (dataConfig == null || dataConfig.getMain() == null) {
            throw new BuzzException("表单数据配置不存在，formId=" + flowForm.getId());
        }

        String registeredTableName = FlowFormSqlUtils.requireTableName(flowForm.getTableName());
        FlowFormDataConfig.Table mainTable = validateTableConfig(dataConfig.getMain(), "主表配置");
        if (!registeredTableName.equals(mainTable.getTableName())) {
            throw new BuzzException("流程表单表名与数据配置不一致");
        }
        return mainTable;
    }

    private FlowFormDataConfig.Table validateTableConfig(FlowFormDataConfig.Table tableConfig, String label) {
        if (tableConfig == null) {
            throw new BuzzException(label + "不能为空");
        }
        String tableName = FlowFormSqlUtils.requireTableName(tableConfig.getTableName());
        if (tableConfig.getColumns() == null || tableConfig.getColumns().isEmpty()) {
            throw new BuzzException(label + "字段配置不能为空");
        }

        Set<String> fields = new HashSet<>();
        for (FlowFormDataConfig.Column column : tableConfig.getColumns()) {
            if (column == null) {
                throw new BuzzException(label + "包含空字段配置");
            }
            String field = FlowFormSqlUtils.requireIdentifier(column.getField(), "字段名");
            if (!fields.add(field.toLowerCase(Locale.ROOT))) {
                throw new BuzzException(label + "包含重复字段: " + field);
            }
        }
        tableConfig.setTableName(tableName);
        return tableConfig;
    }

    private String requireManagedTableName(String tableName) throws SQLException {
        String validTableName = FlowFormSqlUtils.requireTableName(tableName);
        boolean registered = lambdaQuery()
                .eq(FlowForm::getTableName, validTableName)
                .count() > 0;
        if (!registered) {
            registered = flowFormTableBiz.lambdaQuery()
                    .eq(FlowFormTable::getTableName, validTableName)
                    .count() > 0;
        }
        if (!registered) {
            throw new BuzzException("表未注册为流程表单数据表: " + validTableName);
        }
        return validTableName;
    }

    private void ensureTableExists(String tableName) throws SQLException {
        TableInfoVo tableInfo = queryTableStructure(tableName);
        if (!Boolean.TRUE.equals(tableInfo.getExist())) {
            throw new BuzzException("表不存在，tableName=" + tableName);
        }
    }

    private String validateMutableColumn(TableColumnVo column) {
        if (column == null) {
            throw new BuzzException("字段配置不能为空");
        }
        String field = FlowFormSqlUtils.requireIdentifier(column.getField(), "字段名");
        if (SYSTEM_FIELDS.contains(field.toLowerCase(Locale.ROOT))) {
            throw new BuzzException("系统字段不允许修改: " + field);
        }
        buildColumnDefinition(column);
        return field;
    }

    private String buildColumnDefinition(TableColumnVo column) {
        String dataType = column.getDataType();
        if (dataType == null) {
            throw new BuzzException("字段类型不能为空");
        }
        dataType = dataType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_DATA_TYPES.contains(dataType)) {
            throw new BuzzException("不支持的字段类型: " + dataType);
        }

        StringBuilder definition = new StringBuilder(dataType);
        if (DATA_TYPES_LENGTH.contains(dataType) && column.getLength() != null) {
            validateRange(column.getLength(), 1, 65535, "字段长度");
            definition.append('(').append(column.getLength()).append(')');
        } else if (DATA_TYPES_PRECISION.contains(dataType)
                && column.getPrecision() != null && column.getScale() != null) {
            validateRange(column.getPrecision(), 1, 65, "字段精度");
            validateRange(column.getScale(), 0, 30, "字段小数位");
            if (column.getScale() > column.getPrecision()) {
                throw new BuzzException("字段小数位不能大于字段精度");
            }
            definition.append('(').append(column.getPrecision()).append(',')
                    .append(column.getScale()).append(')');
        }

        String nullable = column.getNullable();
        if (nullable == null || "YES".equalsIgnoreCase(nullable)) {
            definition.append(" NULL");
        } else if ("NO".equalsIgnoreCase(nullable)) {
            definition.append(" NOT NULL");
        } else {
            throw new BuzzException("nullable 只能是 YES 或 NO");
        }

        if (column.getDefaultValue() != null) {
            validateDdlText(column.getDefaultValue(), "默认值");
            definition.append(" DEFAULT ")
                    .append(FlowFormSqlUtils.quoteDdlLiteral(column.getDefaultValue(), "默认值"));
        }
        if (column.getComment() != null) {
            validateDdlText(column.getComment(), "字段注释");
            definition.append(" COMMENT ")
                    .append(FlowFormSqlUtils.quoteDdlLiteral(column.getComment(), "字段注释"));
        }
        return definition.toString();
    }

    private void validateDdlText(String value, String label) {
        if (value != null && value.length() > MAX_DDL_TEXT_LENGTH) {
            throw new BuzzException(label + "长度不能超过" + MAX_DDL_TEXT_LENGTH);
        }
    }

    private void validateRange(Integer value, int min, int max, String label) {
        if (value == null || value < min || value > max) {
            throw new BuzzException(label + "必须在" + min + "到" + max + "之间");
        }
    }

    private String normalizeQueryField(String field) {
        return FlowFormSqlUtils.requireIdentifier(StrUtil.toUnderlineCase(field), "查询字段");
    }

    private String quoteTable(String tableName) {
        return FlowFormSqlUtils.quoteIdentifier(
                FlowFormSqlUtils.requireTableName(tableName), "表名");
    }

    private String quoteColumn(String columnName) {
        return FlowFormSqlUtils.quoteIdentifier(columnName, "字段名");
    }

    private Long parseRecordId(Object id) {
        if (id == null) {
            throw new BuzzException("记录 ID 不能为空");
        }
        try {
            long value = Long.parseLong(String.valueOf(id));
            if (value <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new BuzzException("记录 ID 必须是正整数");
        }
    }

}
