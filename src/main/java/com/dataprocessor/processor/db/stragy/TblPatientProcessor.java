package com.dataprocessor.processor.db.stragy;

import com.alibaba.fastjson.JSON;
import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.TableEnum;
import com.dataprocessor.processor.db.DataProcessor;
import com.dataprocessor.util.HospitalCache;
import com.dataprocessor.util.TableCache;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * @Author: RedFlag
 * @CreateTime: 2025-08-20  14:35
 * @Description: tbl_patient_predict_log_mrhp -> group_param
 */
@Slf4j
public class TblPatientProcessor implements DataProcessor {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter IPRDID_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter BATCH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private DatabaseManager dbManager;

    public TblPatientProcessor(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        HospitalCache.init(dbManager);
    }
    @Override

    public void process() {
        log.info("开始执行 ods.tbl_patient_predict_log_mrhp 清洗");

        String sql = "SELECT pid, py_group_param, clean_hospital_code, settle_date, config_id, source_flag " +
                "FROM ods.tbl_patient_predict_log_mrhp " +
                "WHERE DATE(upload_time) = CURRENT_DATE - INTERVAL '1 day'";
        List<Object> params = new ArrayList<>();
        List<Map<String, Object>> queryResult = dbManager.querySql(sql, params);

        if (queryResult.isEmpty()) {
            log.warn("查询结果为空");
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<Map<String, String>>> futures = new ArrayList<>();

        for (Map<String, Object> data : queryResult) {
            futures.add(executor.submit(() -> processRow(data)));
        }

        List<Map<String, String>> processedRows = new ArrayList<>();
        for (Future<Map<String, String>> future : futures) {
            try {
                Map<String, String> row = future.get();
                if (row != null) {
                    processedRows.add(row);
                }
            } catch (Exception e) {
                log.error("处理数据失败", e);
            }
        }

        executor.shutdown();

        for (Map<String, String> row : processedRows) {
            cleanRow(row);
            handleGroupParamMrhpRow(row);
        }

        dbManager.executeAllBatch();
    }


    private Map<String, String> processRow(Map<String, Object> data) {
        Map<String, String> row = new HashMap<>();
        data.forEach((k, v) -> row.put(k, Objects.toString(v, null)));

        Object pyGroupParam = data.get("py_group_param");
        Map<String, Object> paramMap = null;
        Map<String, Object> dipGroupPatientInfoMap = null;

        if (pyGroupParam != null) {
            try {
                paramMap = object2Json(pyGroupParam);
                Object dipInfo = paramMap.get("dipGroupPatientInfoDTO");
                if (dipInfo != null) {
                    dipGroupPatientInfoMap = object2Json(dipInfo);
                }
            } catch (Exception e) {
                log.error("解析 py_group_param 失败", e);
            }
        }

        // 提取特定字段 zdcode、sscode、age、czdcode、csscode、daynum、sex、xsetz、lyfs
        String[] specialFields = {"zdcode", "sscode", "age", "czdcode", "csscode", "daynum", "sex", "xsetz", "lyfs"};

        for (String field : specialFields) {
            // 优先从paramMap中获取，如果不存在则从dipGroupPatientInfoMap中获取
            Object value = null;
            if (paramMap != null && paramMap.containsKey(field)) {
                value = paramMap.get(field);
            } else if (dipGroupPatientInfoMap != null && dipGroupPatientInfoMap.containsKey(field)) {
                value = dipGroupPatientInfoMap.get(field);
            }

            if (value != null) {
                row.put(field, Objects.toString(value, null));
            }
        }
        if (paramMap != null){
            row.put("py_group_param", JSON.toJSONString(paramMap));
        }

        String hosCode = row.getOrDefault("clean_hospital_code", "");
        row.put("hos_code", hosCode);

        String hosName = Optional.ofNullable(HospitalCache.getHospitalName(hosCode)).orElse(hosCode);
        row.put("hos_name", hosName);

        String batchName = hosName + "_" + LocalDateTime.now().format(BATCH_FORMATTER);
        row.put("batch_name", batchName);

        try {
            LocalDateTime settleDate = LocalDateTime.parse(row.get("settle_date"), DATE_FORMATTER);
            row.put("iprdid", settleDate.format(IPRDID_FORMATTER));
        } catch (Exception e) {
            log.warn("settle_date 格式错误: {}", row.get("settle_date"));
            row.put("iprdid", "");
        }

        row.put("batch_id", row.get("config_id"));
        row.put("created_at", LocalDateTime.now().format(DATE_FORMATTER));

        return row;
    }

    public static Map<String, Object> object2Json(Object obj) {
        return JSON.parseObject(obj.toString(), Map.class);
    }

    public void handleGroupParamMrhpRow(Map<String, String> row) {
        try {
//            TableCache.addRow(row);
            dbManager.addBatch(TableEnum.group_param, row);
        } catch (SQLException e) {
            log.error("费用映射聚合表:Failed to add batch to database", e);
            throw new RuntimeException(e);
        }
    }

    public void cleanRow(Map<String, String> row) {
        String[] columns = TableEnum.group_param.getColumns();
        Set<String> columnSet = new HashSet<>(Arrays.asList(columns));
        Iterator<Map.Entry<String, String>> iterator = row.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (!columnSet.contains(entry.getKey())) {
                iterator.remove();
            }
        }
    }
}
