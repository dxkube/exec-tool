package com.dataprocessor.processor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class TableConfig {
    private String name;
    private String description;
    private List<ColumnConfig> columns;

    public List<String> getAllColumns() {
        List<String> allColumns = new ArrayList<>();
        if (columns == null) {
            return allColumns;
        }
        for (ColumnConfig column : columns) {
            if (column.getColumns() != null) {
                allColumns.addAll(column.getColumns().stream().map(ColumnConfig::getName).collect(Collectors.toList()));
            } else {
                allColumns.add(column.getName());
            }
        }
        return allColumns;
    }
}

