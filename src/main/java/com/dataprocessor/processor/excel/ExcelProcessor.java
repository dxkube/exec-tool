package com.dataprocessor.processor.excel;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.CommonProcessor;
import com.dataprocessor.processor.FileProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Excel文件处理器，流式读取并处理Excel文件
 */
public abstract class ExcelProcessor extends FileProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ExcelProcessor.class);

    public ExcelProcessor(String filePath, DatabaseManager dbManager, CommonProcessor commonProcessor) {
        super(filePath, dbManager,commonProcessor);
    }

} 