package com.dataprocessor.db;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dataprocessor.processor.ColumnConfig;
import com.dataprocessor.processor.TableConfig;
import com.dataprocessor.processor.TableEnum;
import com.dataprocessor.util.EncryptionUtil;
import com.dataprocessor.util.StringToUUID;
import com.dataprocessor.util.TableCache;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * 负责数据库连接和数据插入的管理器
 */
public class DatabaseManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    @FunctionalInterface
    public interface RowHandler {
        void handle(Map<String, Object> row) throws SQLException;
    }

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int batchSize;
    private Connection connection;
    private Map<TableEnum, PreparedStatementInfo> preparedStatements;
//    private String importId;
    private final String encryptKey = "asdad1111";

    public DatabaseManager(String dbUrl, String dbUser, String dbPassword,
                           int batchSize, String importId) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.batchSize = batchSize;
        this.preparedStatements = new HashMap<>();
//        this.importId = importId;

    }

    public void clean() {
        this.preparedStatements = new HashMap<>();
    }

    /**
     * 准备数据库表（如果不存在则创建）
     */
    public void prepareTable(TableEnum... tables) throws SQLException {
        // 建立数据库连接
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            connection.setAutoCommit(false);
        }
        for (TableEnum table : tables) {
            // todo 检查表是否存在，不存在则创建
//            try (Statement stmt = connection.createStatement()) {
//                // 检查表是否存在
//                DatabaseMetaData meta = connection.getMetaData();
//                ResultSet rs = meta.getTables(null, null, table.name(), null);
//
//                if (!rs.next()) {
//                    // 表不存在，创建表
//                    throw new SQLException("表 " + table.name() + " 不存在");
//                }
//            }
            List<String> columns = TableCache.getTableConfig(table).getAllColumns();
            //没有配置columns，导入时候实时用列名
            if (Objects.nonNull(columns)&& !columns.isEmpty()) {
                // 准备插入语句
                StringJoiner placeholders = new StringJoiner(", ");
                for (int i = 0; i < columns.size(); i++) {
                    placeholders.add("?");
                }

                StringJoiner columnList = new StringJoiner(", ");
                for (String column : columns) {
                    columnList.add(column);
                }
                if(!columns.contains("batch_id")){
                    columns.add("batch_id");
                    columnList.add("batch_id");
                    placeholders.add("?");
                }
                String insertSQL = "INSERT INTO " + table.name() + "(" + columnList +
                        ") VALUES (" + placeholders + ")";

                preparedStatements.put(table, new PreparedStatementInfo(connection.prepareStatement(insertSQL), columns));
            }


        }
    }

    private PreparedStatementInfo buildPreparedStatementInfoByRow(TableEnum table, Map<String, String> row) throws SQLException {
        StringJoiner placeholders = new StringJoiner(", ");
        StringJoiner columnList = new StringJoiner(", ");
        List<String> columns = new ArrayList<>();
        row.forEach((key, value) -> {
            placeholders.add("?");
            columnList.add(key);
            columns.add(key);
        });
        if(!columns.contains("batch_id")){
            columns.add("batch_id");
            columnList.add("batch_id");
            placeholders.add("?");
        }
        String insertSQL = "INSERT INTO " + table.name() + "(" + columnList +
                ") VALUES (" + placeholders + ")";

        PreparedStatementInfo preparedStatementInfo = new PreparedStatementInfo(connection.prepareStatement(insertSQL), columns);
        preparedStatements.put(table, preparedStatementInfo);
        return preparedStatementInfo;
    }

    /**
     * 添加一行数据到批处理
     */
    public void addBatch(TableEnum table, Map row) throws SQLException {
        logger.debug("添加批处理数据到 {}: {}", table.name(), row);
        TableConfig tableConfig = TableCache.getTableConfig(table);
        String hosCode = (String) row.get("hos_code");
        String batchId = (String) row.get("batch_id");
        String importId = StringToUUID.generateUUID(hosCode + "_" + batchId);
        row.put("batch_id", importId);
        if ("cost_detail".equals(tableConfig.getName())){
            row.remove("hos_code");
        }
        if (Objects.isNull(preparedStatements.get(table))) {
            preparedStatements.put(table, buildPreparedStatementInfoByRow(table, row));
        }
        PreparedStatementInfo preparedStatementInfo = preparedStatements.get(table);
        for (int i = 0; i < preparedStatementInfo.getColumns().size(); i++) {
            if (row.containsKey(preparedStatementInfo.getColumns().get(i))) {
                String name = preparedStatementInfo.getColumns().get(i);
                Object value =  row.getOrDefault(name, null);

                if(Objects.nonNull(tableConfig.getColumns())){
                    ColumnConfig columnConfig = tableConfig.getColumns().stream().filter(config -> config.getName().equals(name)).findFirst().orElse(null);
                    if (Objects.nonNull(columnConfig) && Boolean.TRUE.equals(columnConfig.getEncrypt()) && Objects.nonNull(value)) {
                        logger.info("列 {} 加密", name);
                        value = EncryptionUtil.encrypt((String) value, encryptKey);
                    }
                }
                if("".equals(value)){
                    value = null;
                }
                if ("null".equals(value)) {
                    value = null;
                    preparedStatementInfo.getPreparedStatement().setNull(i + 1, Types.VARCHAR);
                }else{
                    preparedStatementInfo.getPreparedStatement().setObject(i + 1, value);
                }
            } else {
                logger.info("列 {} 不存在", preparedStatementInfo.getColumns().get(i));
                preparedStatementInfo.getPreparedStatement().setObject(i + 1, null);
            }
        }
        preparedStatementInfo.getPreparedStatement().addBatch();
        preparedStatementInfo.setBatchCount(preparedStatementInfo.getBatchCount()+1);

        // 如果达到批处理大小，则执行批处理
        if (preparedStatementInfo.getBatchCount() >= batchSize) {
            executeBatch(table);
        }
    }

    /**
     * 执行批处理，将数据提交到数据库
     */
    public void executeBatch(TableEnum table) {
        PreparedStatementInfo preparedStatementInfo = preparedStatements.get(table);
        if(preparedStatementInfo.getBatchCount()>0){
            try {
                logger.info("执行批处理{}.{}，批处理大小：{}",connection.getSchema(),table.name(), preparedStatementInfo.getBatchCount());
                preparedStatementInfo.getPreparedStatement().executeBatch();
                connection.commit();
            } catch (SQLException e) {
                logger.error("执行批处理时出错", e);
            }
            preparedStatementInfo.setBatchCount(0);
        }
    }

    public void executeAllBatch() {
            preparedStatements.keySet().forEach(this::executeBatch);
    }

    /**
     * Batch-executes parameterized UPDATE statements in a single round-trip.
     *
     * Each element in paramsList is an Object[] of [param1, param2, ...] bound
     * positionally to the placeholders in sql.
     *
     * Using PreparedStatement.addBatch() + executeBatch() avoids N individual
     * network round-trips and lets PostgreSQL pipeline the statements efficiently.
     */
    public void batchUpdateFeeList(String sql, List<Object[]> paramsList) throws SQLException {
        if (paramsList.isEmpty()) {
            return;
        }
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            connection.setAutoCommit(false);
        }
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (Object[] params : paramsList) {
                // params[0] is a JSON array string that must reach PostgreSQL as jsonb.
                // setString() sends it as text, which causes "operator does not exist: jsonb || text".
                // PGobject with type "jsonb" makes the driver send the correct OID.
                org.postgresql.util.PGobject jsonbValue = new org.postgresql.util.PGobject();
                jsonbValue.setType("jsonb");
                jsonbValue.setValue((String) params[0]);
                stmt.setObject(1, jsonbValue);
                // remaining params (pid, batch_id) are plain strings
                for (int i = 1; i < params.length; i++) {
                    stmt.setString(i + 1, (String) params[i]);
                }
                stmt.addBatch();
            }
            stmt.executeBatch();
            connection.commit();
            logger.info("批量更新完成，共 {} 行", paramsList.size());
        } catch (SQLException e) {
            logger.error("批量更新失败，SQL: {}", sql, e);
            throw e;
        }
    }
    /**
     * 执行查询 SQL 并返回结果
     * @param sql 查询语句
     * @param params 参数列表（可为 null 或空）
     * @return 查询结果，每行是一个 Map，键为列名，值为对应数据
     * @throws SQLException SQL 执行异常
     */
    @SneakyThrows
    public List<Map<String, Object>> querySql(String sql, List<Object> params) {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            connection.setAutoCommit(false);
        }

        List<Map<String, Object>> resultList = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setFetchSize(Math.max(batchSize, 1000));
            if (CollectionUtils.isNotEmpty( params)) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param == null) {
                        stmt.setNull(i + 1, Types.NULL);
                    } else {
                        stmt.setObject(i + 1, param);
                    }
                }
            }

            logger.info("执行查询 SQL: {}", sql);
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    resultList.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("查询 SQL 失败: {}", sql, e);
            throw e;
        }

        return resultList;
    }

    /**
     * 以流式方式执行查询，适合超大结果集，避免一次性加载到内存。
     * 使用独立只读连接，避免与批量写入共用连接导致游标被打断。
     */
    @SneakyThrows
    public void querySqlStream(String sql, List<Object> params, RowHandler rowHandler) {
        try (Connection queryConnection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            queryConnection.setAutoCommit(false);
            queryConnection.setReadOnly(true);

            try (PreparedStatement stmt = queryConnection.prepareStatement(sql)) {
                stmt.setFetchSize(Math.max(batchSize, 1000));
                if (CollectionUtils.isNotEmpty(params)) {
                    for (int i = 0; i < params.size(); i++) {
                        Object param = params.get(i);
                        if (param == null) {
                            stmt.setNull(i + 1, Types.NULL);
                        } else {
                            stmt.setObject(i + 1, param);
                        }
                    }
                }

                logger.info("流式执行查询 SQL: {}", sql);
                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnLabel(i);
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        rowHandler.handle(row);
                    }
                }
            } finally {
                queryConnection.rollback();
            }
        } catch (SQLException e) {
            logger.error("流式查询 SQL 失败: {}", sql, e);
            throw e;
        }
    }



    @Override
    public void close() throws Exception {
        try {
            preparedStatements.values().forEach(preparedStatementInfo -> {
                try {
                    preparedStatementInfo.getPreparedStatement().close();
                } catch (SQLException e) {
                    logger.error("关闭预处理语句时出错", e);
                }
            });

            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.error("关闭数据库连接时出错", e);
            throw e;
        }
    }
}


class PreparedStatementInfo {
    private final PreparedStatement preparedStatement;
    private final List<String> columns;
    private int batchCount = 0;

    public PreparedStatementInfo(PreparedStatement preparedStatement, List<String> columns) {
        this.preparedStatement = preparedStatement;
        this.columns = columns;
        this.batchCount = 0;
    }
    public int getBatchCount() {
        return batchCount;
    }
    public void setBatchCount(int batchCount) {
        this.batchCount = batchCount;
    }
    public PreparedStatement getPreparedStatement() {
        return preparedStatement;
    }

    public List<String> getColumns() {
        return columns;
    }
}
