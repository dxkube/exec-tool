package com.dataprocessor.processor.db.stragy;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.TableEnum;
import com.dataprocessor.processor.db.DataProcessor;
import com.dataprocessor.util.HospitalCache;
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
 * @Description: dwd_fee_sum_group_mrhp -> cost_detail
 */
@Slf4j
public class DwdFeeProcessor implements DataProcessor {

    private static final int QUERY_WINDOW_HOURS = 1;
    private static final int BATCH_COMMIT_SIZE = 1000;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter BATCH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

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
            "ORDER BY upload_time, pid;";

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

        // 获取总记录数
        LocalDate today = LocalDate.now();
        LocalDateTime rangeStart = today.minusDays(1).atStartOfDay();
        LocalDateTime rangeEnd = today.atStartOfDay();
        int totalRows = getTotalRowCount(rangeStart, rangeEnd);
        if (totalRows == 0) {
            log.info("没有需要处理的数据");
            return;
        }

        log.info("发现 {} 条数据需要处理，将按 {} 小时时间窗口顺序扫描", totalRows, QUERY_WINDOW_HOURS);

        int processedRows = 0;
        for (LocalDateTime windowStart = rangeStart;
             windowStart.isBefore(rangeEnd);
             windowStart = windowStart.plusHours(QUERY_WINDOW_HOURS)) {
            LocalDateTime windowEnd = windowStart.plusHours(QUERY_WINDOW_HOURS);
            if (windowEnd.isAfter(rangeEnd)) {
                windowEnd = rangeEnd;
            }
            processedRows += processWindow(windowStart, windowEnd, processedRows);
        }

        dbManager.executeAllBatch();

        long endTime = System.currentTimeMillis();
        log.info("dwd_fee_sum_group 数据清洗完成，共处理 {} 条记录，耗时 {}ms，平均速度 {} 条/秒",
                processedRows,
                (endTime - startTime),
                (int)(processedRows * 1000.0 / (endTime - startTime + 1)));
    }

    /**
     * 获取总记录数
     */
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
     * 按时间窗口处理数据，避免大 OFFSET 扫描
     */
    private int processWindow(LocalDateTime windowStart, LocalDateTime windowEnd, int processedBase) {
        try {
            log.info("开始流式处理时间窗口 {} ~ {}", windowStart, windowEnd);

            int[] windowProcessed = {0};
            dbManager.querySqlStream(
                    querySql,
                    Arrays.asList(Timestamp.valueOf(windowStart), Timestamp.valueOf(windowEnd)),
                    rawRow -> {
                        processRow(rawRow);
                        windowProcessed[0]++;
                        int count = processedBase + windowProcessed[0];
                        if (count % BATCH_COMMIT_SIZE == 0) {
                            dbManager.executeAllBatch();
                            log.info("已处理 {} 条记录", count);
                        }
                    }
            );

            if (windowProcessed[0] == 0) {
                return 0;
            }

            log.info("完成时间窗口 {} ~ {} 的处理，共 {} 条",
                    windowStart, windowEnd, windowProcessed[0]);

            return windowProcessed[0];
        } catch (Exception e) {
            log.error("处理时间窗口 {} ~ {} 时发生异常", windowStart, windowEnd, e);
            throw new RuntimeException("处理时间窗口数据失败", e);
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

        try {
            dbManager.addBatch(TableEnum.cost_detail, row);
        } catch (SQLException e) {
            log.error("添加记录到批量处理时失败", e);
            throw new RuntimeException(e);
        }
    }

    private String safeToString(Object obj) {
        return Objects.toString(obj, "");
    }

    public void cleanRow(Map<String, String> row) {
        String[] columns = TableEnum.cost_detail.getColumns();
        Set<String> columnSet = new HashSet<>(Arrays.asList(columns));
        Iterator<Map.Entry<String, String>> iterator = row.entrySet().iterator();

        while(iterator.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry)iterator.next();
            if (!"hos_code".equals(entry.getKey()) && !columnSet.contains(entry.getKey())) {
                iterator.remove();
            }
        }

    }
}
