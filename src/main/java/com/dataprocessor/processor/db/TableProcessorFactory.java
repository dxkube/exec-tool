package com.dataprocessor.processor.db;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.db.stragy.DwdFeeProcessor;
import com.dataprocessor.processor.db.stragy.TblDimChargeProcessor;
import com.dataprocessor.processor.db.stragy.TblPatientProcessor;

import java.sql.SQLException;

/**
 * @Author: RedFlag
 * @CreateTime: 2025-08-20  15:01
 * @Description:
 */
public class TableProcessorFactory {
    public static DataProcessor createProcessor(String tableName, DatabaseManager dbManager) throws SQLException {
        switch (tableName) {
            case "tbl_patient_predict_log_mrhp":
                return new TblPatientProcessor(dbManager);
            case "dwd_fee_sum_group_mrhp":
                return new DwdFeeProcessor(dbManager);
            case "tbl_dim_charge_item_mapping_mrhp":
                return new TblDimChargeProcessor(dbManager);
            default:
                throw new IllegalArgumentException("Unsupported table: " + tableName);
        }
    }
}