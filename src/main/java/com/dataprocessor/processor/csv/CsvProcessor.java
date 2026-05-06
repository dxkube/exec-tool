package com.dataprocessor.processor.csv;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.CommonProcessor;
import com.dataprocessor.processor.FileProcessor;
import com.dataprocessor.util.EncryptionUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * CSV文件处理器，流式读取并处理CSV文件
 */
public abstract class CsvProcessor extends FileProcessor {
    private static final Logger logger = LoggerFactory.getLogger(CsvProcessor.class);
    
    public CsvProcessor(String filePath, DatabaseManager dbManager, CommonProcessor commonProcessor) {
        super(filePath, dbManager,commonProcessor);
    }

    @Override
    public void process() throws Exception {
        long startTime = System.currentTimeMillis();
        long recordCount = 0;
        
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "GBK"));
             CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {
            
            // 获取列名
            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            String[] headers = new String[headerMap.size()];
            for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                headers[entry.getValue()] = entry.getKey();
            }
            
            // 准备数据库
            prepareTable(this.dbManager);
            
            // 处理每一行数据
            for (CSVRecord record : csvParser) {
                Map<String, String> row = new HashMap<>();
                
                // 遍历每个字段
                for (int i = 0; i < headers.length; i++) {
                    String fieldName = headers[i];
                    String value = record.get(i);
                    
                    // 如果需要加密该字段
                    //todo 加密
//                    if (Arrays.asList(encryptFields).contains(fieldName)) {
//                        value = EncryptionUtil.encrypt(value, encryptKey);
//                    }
                    
                    row.put(fieldName, value);
                }
                
                // 添加到数据库批处理
//                dbManager.addBatch(row);
                handleRow(row);
                recordCount++;
                // 定期输出处理状态
                if (recordCount % 10000 == 0) {
                    logger.info("已处理 {} 条记录", recordCount);
                }
            }
            
            // 确保剩余的批处理被提交
            dbManager.executeAllBatch();
            
            long endTime = System.currentTimeMillis();
            logger.info("处理完成。共处理 {} 条记录，耗时 {} 秒", 
                    recordCount, (endTime - startTime) / 1000.0);
        }
    }
} 