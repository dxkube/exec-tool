package com.dataprocessor.processor.csv;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.CommonProcessor;

import java.util.Map;

public class CostMappingCsvProcessor extends CsvProcessor{
    public CostMappingCsvProcessor(String filePath, DatabaseManager dbManager,CommonProcessor commonProcessor) {
        super(filePath, dbManager,commonProcessor);
    }

    @Override
    public void prepareTable(DatabaseManager databaseManager) {
        commonProcessor.prepareCostMappingTable(databaseManager);
    }

    @Override
    public void handleRow(Map<String, String> row)  {
        commonProcessor.handleCostMappingRow(row);
    }

}
