package com.dataprocessor.util;

import com.dataprocessor.processor.TableConfig;
import com.dataprocessor.processor.TableEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableCache {
    private static final List<Map<String, String>> rows = new ArrayList<>();
    private static final Map<TableEnum, TableConfig> tableConfigs = new HashMap<>();

    public static void addRow(Map<String, String> row) {
        rows.add(row);
    }

    public static List<Map<String, String>> getRows() {
        return rows;
    }

    public static void addTableConfig(TableEnum tableEnum, TableConfig tableConfig) {
        tableConfigs.put(tableEnum, tableConfig);
    }

    public static TableConfig getTableConfig(TableEnum tableEnum) {
        return tableConfigs.get(tableEnum);
    }
}
