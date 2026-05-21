package com.dataprocessor.processor.db.stragy;

import com.alibaba.fastjson.JSON;
import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.TableEnum;
import com.dataprocessor.processor.db.DataProcessor;
import com.dataprocessor.util.HospitalCache;
import com.dataprocessor.util.StringToUUID;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: RedFlag
 * @CreateTime: 2025-08-20  15:05
 * @Description: dwd_fee_sum_group_mrhp -> group_param.fee_list (JSONB)
 * 映射结果不再写入 cost_detail，改为按 (pid, batch_id) 分组后批量更新 group_param.fee_list
 */
@Slf4j
public class DwdFeeProcessor implements DataProcessor {

    private static final int BATCH_COMMIT_SIZE = 5000;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter BATCH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String UPDATE_FEE_LIST_SQL =
            "UPDATE ods.group_param " +
                    "SET fee_list = ? " +
                    "WHERE pid = ? AND batch_id = ?";

    private final DatabaseManager dbManager;
    private final String querySql = "SELECT pid,\n" +
            "       item_code,\n" +
            "       item_name,\n" +
            "       item_class_code,\n" +
            "       insurance_code,\n" +
            "       amount,\n" +
            "       price,\n" +
            "       clean_hospital_code,\n" +
            "       config_id,\n" +
            "       upload_time\n" +
            "FROM ods.dwd_fee_sum_group_mrhp\n" +
            "WHERE upload_time >= ?\n" +
            "  AND upload_time < ?\n" +
            "ORDER BY pid, config_id, upload_time;";

    private final String countSql =
            "SELECT COUNT(*) AS total\n" +
                    "FROM ods.dwd_fee_sum_group_mrhp\n" +
                    "WHERE upload_time >= ?\n" +
                    "  AND upload_time < ?";

    private final String mappingSql = "SELECT \n" +
            "       hos_project_code, \n" +
            "       charge_type_code_hs, \n" +
            "       charge_type_name_hs, \n" +
            "       insurance_name, \n" +
            "       ins_nation_project_code, \n" +
            "       ins_nation_project_name, \n" +
            "       mija_project_code, \n" +
            "       mija_project_name \n" +
            "FROM (\n" +
            "    SELECT *,\n" +
            "           ROW_NUMBER() OVER (\n" +
            "               PARTITION BY hos_project_code, charge_type_code_hs\n" +
            "           ) AS rn\n" +
            "    FROM ods.tbl_dim_charge_item_mapping_mrhp\n" +
            ") t\n" +
            "WHERE rn = 1;\n";

    private final Map<String, Map<String, Object>> mappingCache = new ConcurrentHashMap<>();
    private final String batchDate;

    private String currentGroupKey = "";
    private final List<Map<String, String>> currentGroupItems = new ArrayList<>();
    private final List<Object[]> pendingGroupUpdates = new ArrayList<>(BATCH_COMMIT_SIZE);

    public DwdFeeProcessor(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.batchDate = LocalDate.now().format(BATCH_FORMATTER);
        HospitalCache.init(dbManager);
        preloadMappingCache();
    }

    private void preloadMappingCache() {
        log.info("开始加载映射缓存数据");
        long startTime = System.currentTimeMillis();

        List<Map<String, Object>> mappings = dbManager.querySql(mappingSql, null);
        for (Map<String, Object> mapping : mappings) {
            String key = buildMappingKey(
                    mapping.get("hos_project_code"),
                    mapping.get("charge_type_code_hs")
            );
            mappingCache.put(key, mapping);
        }

        log.info("映射缓存加载完成，共加载 {} 条记录，耗时 {}ms",
                mappingCache.size(), System.currentTimeMillis() - startTime);
    }

    private String buildMappingKey(Object itemCode, Object itemClassCode) {
        return safeToString(itemCode) + "|" + safeToString(itemClassCode);
    }

    @Override
    public void process() {
        log.info("开始执行 dwd_fee_sum_group 数据清洗");
        long startTime = System.currentTimeMillis();

        LocalDate today = LocalDate.now();
        LocalDateTime rangeStart = today.minusDays(1).atStartOfDay();
        LocalDateTime rangeEnd = today.atStartOfDay();
        int totalRows = getTotalRowCount(rangeStart, rangeEnd);
        if (totalRows == 0) {
            log.info("没有需要处理的数据");
            return;
        }

        log.info("发现 {} 条数据需要处理", totalRows);

        int[] processedRows = {0};
        try {
            dbManager.querySqlStream(
                    querySql,
                    Arrays.asList(Timestamp.valueOf(rangeStart), Timestamp.valueOf(rangeEnd)),
                    rawRow -> {
                        processRow(rawRow);
                        processedRows[0]++;
                        if (processedRows[0] % 100000 == 0) {
                            log.info("已处理 {} / {} 条记录", processedRows[0], totalRows);
                        }
                    }
            );
        } catch (Exception e) {
            log.error("流式处理数据时发生异常", e);
            throw new RuntimeException("数据处理失败", e);
        }

        flushCurrentGroup();
        flushGroupUpdates();

        long endTime = System.currentTimeMillis();
        log.info("dwd_fee_sum_group 数据清洗完成，共处理 {} 条记录，耗时 {}ms，平均速度 {} 条/秒",
                processedRows[0],
                (endTime - startTime),
                (int) (processedRows[0] * 1000.0 / (endTime - startTime + 1)));
    }

    private int getTotalRowCount(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        try {
            List<Map<String, Object>> result = dbManager.querySql(
                    countSql,
                    Arrays.asList(Timestamp.valueOf(rangeStart), Timestamp.valueOf(rangeEnd))
            );
            if (result.isEmpty()) {
                return 0;
            }
            Object totalObj = result.get(0).get("total");
            return totalObj instanceof Number ? ((Number) totalObj).intValue() : 0;
        } catch (Exception e) {
            log.error("获取总记录数失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理单条记录
     */
    private void processRow(Map<String, Object> rawRow) {
        Map<String, String> row = new HashMap<>();
        rawRow.forEach((k, v) -> row.put(k, safeToString(v)));

        row.put("feecode", safeToString(rawRow.get("item_code")));
        row.put("feename", safeToString(rawRow.get("item_name")));
        row.put("itemclasscode", safeToString(rawRow.get("item_class_code")));
        row.put("medicalInsurancecode", safeToString(rawRow.get("insurance_code")));
        row.put("orgcode", "");
        row.put("number", safeToString(rawRow.get("amount")));
        row.put("fee", safeToString(rawRow.get("price")));
        row.put("source_flag", "");
        row.put("hos_code", safeToString(rawRow.get("clean_hospital_code")));

        String hosCode = row.getOrDefault("clean_hospital_code", "");
        String hosName = Optional.ofNullable(HospitalCache.getHospitalName(hosCode)).orElse(hosCode);
        row.put("batch_name", hosName + "_" + batchDate);
        row.put("batch_id", row.get("config_id"));

        String mappingKey = buildMappingKey(row.get("item_code"), row.get("item_class_code"));
        Map<String, Object> mapping = mappingCache.get(mappingKey);

        if (mapping != null) {
            row.put("mapping_mark", "1");
            row.put("charge_type_name_hs", safeToString(mapping.get("charge_type_name_hs")));
            row.put("insurance_name", safeToString(mapping.get("insurance_name")));
            row.put("ins_nation_project_code", safeToString(mapping.get("ins_nation_project_code")));
            row.put("ins_nation_project_name", safeToString(mapping.get("ins_nation_project_name")));
            row.put("mija_project_code", safeToString(mapping.get("mija_project_code")));
            row.put("mija_project_name", safeToString(mapping.get("mija_project_name")));
        } else {
            row.put("mapping_mark", "0");
            row.put("charge_type_name_hs", "");
            row.put("insurance_name", "");
            row.put("ins_nation_project_code", row.get("feecode"));
            row.put("ins_nation_project_name", row.get("feename"));
            row.put("mija_project_code", row.get("feecode"));
            row.put("mija_project_name", row.get("feecode"));
        }

        row.put("created_at", LocalDateTime.now().format(DATETIME_FORMATTER));
        cleanRow(row);

        collectFeeRow(row);
    }

    private String safeToString(Object obj) {
        return Objects.toString(obj, "");
    }

   private void collectFeeRow(Map<String, String> cleanedRow) {
        Map<String, String> feeItem = buildFeeItem(cleanedRow);
        String groupKey = feeItem.get("pid") + "|" + feeItem.get("batch_id");

        if (!groupKey.equals(currentGroupKey)) {
            flushCurrentGroup();
            currentGroupKey = groupKey;
        }
        currentGroupItems.add(feeItem);
    }
    private void flushCurrentGroup() {
        if (currentGroupItems.isEmpty()) {
            return;
        }

        String[] keys = currentGroupKey.split("\\|", 2);
        String pid = keys[0];
        String batchId = keys[1];
        String feeListJson = JSON.toJSONString(currentGroupItems);
        pendingGroupUpdates.add(new Object[]{feeListJson, pid, batchId});
        currentGroupItems.clear();

        if (pendingGroupUpdates.size() >= BATCH_COMMIT_SIZE) {
            flushGroupUpdates();
        }
    }

    /**
     * Executes one JDBC batch UPDATE for all accumulated groups, then clears the queue.
     */
    private void flushGroupUpdates() {
        if (pendingGroupUpdates.isEmpty()) {
            return;
        }

        try {
            dbManager.batchUpdateFeeList(UPDATE_FEE_LIST_SQL, pendingGroupUpdates);
            log.info("已写入 fee_list：{} 组", pendingGroupUpdates.size());
        } catch (SQLException e) {
            log.error("批量更新 group_param.fee_list 失败", e);
            throw new RuntimeException("批量更新 fee_list 失败", e);
        }
        pendingGroupUpdates.clear();
    }

    /**
     * Creates the JSON-ready copy of a cleaned row:
     * - Replaces raw batch_id (config_id) with the UUID that group_param.batch_id holds.
     * The UUID is derived from hosCode + "_" + rawBatchId, identical to what DatabaseManager
     * computes when writing group_param rows, so the UPDATE WHERE clause matches correctly.
     * - Removes hos_code (internal routing field, not part of the fee payload).
     */
    private Map<String, String> buildFeeItem(Map<String, String> cleanedRow) {
        String hosCode = cleanedRow.get("hos_code");
        String rawBatchId = cleanedRow.get("batch_id");
        String uuidBatchId = StringToUUID.generateUUID(hosCode + "_" + rawBatchId);

        Map<String, String> feeItem = new LinkedHashMap<>(cleanedRow);
        feeItem.put("batch_id", uuidBatchId);
        feeItem.remove("hos_code");
        return feeItem;
    }

    public void cleanRow(Map<String, String> row) {
        String[] columns = TableEnum.cost_detail.getColumns();
        Set<String> columnSet = new HashSet<>(Arrays.asList(columns));
        Iterator<Map.Entry<String, String>> iterator = row.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry) iterator.next();
            if (!"hos_code".equals(entry.getKey()) && !columnSet.contains(entry.getKey())) {
                iterator.remove();
            }
        }
    }
}
