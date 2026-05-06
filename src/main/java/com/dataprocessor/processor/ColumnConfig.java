package com.dataprocessor.processor;

import lombok.Data;

import java.util.List;

@Data
public class ColumnConfig {
    private String name;
    private String type;
    private String source;
    private Boolean encrypt;
    private List<ColumnConfig> columns;
}
