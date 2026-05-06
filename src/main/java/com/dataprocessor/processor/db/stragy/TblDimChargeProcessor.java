package com.dataprocessor.processor.db.stragy;

import com.alibaba.fastjson.JSON;
import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.TableEnum;
import com.dataprocessor.processor.db.DataProcessor;
import com.dataprocessor.util.HospitalCache;
import com.dataprocessor.util.TableCache;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @Author: RedFlag
 * @CreateTime: 2025-08-20  15:06
 * @Description: tbl_dim_charge_item_mapping_mrhp -> cost_mapping_aggregation
 */
@Slf4j
public class TblDimChargeProcessor implements DataProcessor {
    private DatabaseManager dbManager;
    public TblDimChargeProcessor(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        HospitalCache.init(dbManager);
    }

    @Override
    public void process() {
        log.info("开始执行 ods.tbl_dim_charge_item_mapping 清洗");
        String sql = "SELECT * FROM ods.tbl_dim_charge_item_mapping_mrhp WHERE DATE(upload_time) = CURRENT_DATE - INTERVAL '1 day'";
        List<Object> params = new ArrayList<>();

        List<Map<String, Object>> queryResult = dbManager.querySql(sql, params);
        if (queryResult.isEmpty()) {
            log.warn("查询结果为空");
            return;
        }

        for (Map<String, Object> data : queryResult) {
            Map<String, String> row = new HashMap<>();
            data.forEach((k, v) -> row.put(k, Objects.toString(v, null)));

            String hosCode = row.getOrDefault("clean_hospital_code", "");
            row.put("hos_code", hosCode);

            String batchName = row.get("hos_name") + "_" + new SimpleDateFormat("yyyyMM").format(new Date());
            row.put("batch_name", batchName);
            row.put("batch_id", row.get("config_id"));
            Long createdAt = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            row.put("created_at", sdf.format(createdAt));
            cleanRow(row);
            handleCostMappingMrhpRow(row);
        }
        dbManager.executeAllBatch();

    }
    public void handleCostMappingMrhpRow(Map<String, String> row) {
        try {
//            TableCache.addRow(row);
            dbManager.addBatch(TableEnum.cost_mapping_aggregation, row);
        } catch (SQLException e) {
            log.error("费用映射聚合表:Failed to add batch to database", e);
            throw new RuntimeException(e);
        }
    }

    public void cleanRow(Map<String, String> row) {
        String[] columns = TableEnum.cost_mapping_aggregation.getColumns();
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