package com.dataprocessor.processor;

import com.dataprocessor.db.DatabaseManager;

import java.util.Map;

/**
 * 文件处理器接口，定义了处理文件的通用方法
 */
public abstract class FileProcessor {
    protected String filePath;
    protected DatabaseManager dbManager;
    protected String[] encryptFields;
    protected String encryptKey;
    protected CommonProcessor commonProcessor;
    
    public FileProcessor(String filePath, DatabaseManager dbManager,CommonProcessor commonProcessor) {
        this.filePath = filePath;
        this.dbManager = dbManager;
        this.commonProcessor=commonProcessor;
    }
    
    /**
     * 处理文件的抽象方法，子类需要实现
     */
    public abstract void process() throws Exception;

    public abstract void prepareTable(DatabaseManager databaseManager);

    public abstract void handleRow(Map<String, String> row) ;
    
    public void setEncryptFields(String[] encryptFields) {
        this.encryptFields = encryptFields;
    }
    
    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }
} 