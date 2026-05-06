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
        logger.info("添加批处理数据: {}", row);
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