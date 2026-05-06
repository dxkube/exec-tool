package com.dataprocessor.processor;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.util.EncryptionUtil;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 用于处理Excel工作表的SAX内容处理器
 */
public class SheetHandler extends DefaultHandler {
    private static final Logger logger = LoggerFactory.getLogger(SheetHandler.class);
    
    private SharedStringsTable sst;
    private DatabaseManager dbManager;
    private String[] encryptFields;
    private String encryptKey;
    
    private String lastContents;
    private boolean nextIsString;
    private boolean inRow;
    private int currentRow = 0;
    private int currentCol = 0;
    private List<String> headers = new ArrayList<>();
    private Map<String, String> rowData;
    private long recordCount = 0;
    private Consumer<Map<String, String>> rowProcesses;
    private Consumer<DatabaseManager> prepareTable;
    
    public SheetHandler(SharedStringsTable sst, DatabaseManager dbManager, 
                        String[] encryptFields, String encryptKey, Consumer<DatabaseManager> prepareTable, Consumer<Map<String, String>> rowProcesses) {
        this.sst = sst;
        this.dbManager = dbManager;
        this.encryptFields = encryptFields;
        this.encryptKey = encryptKey;
        this.prepareTable = prepareTable;
        this.rowProcesses = rowProcesses;
    }
    
    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
        // 清空上次的内容
        lastContents = "";
        
        // 处理行开始
        if (name.equals("row")) {
            inRow = true;
            int rowNum = Integer.parseInt(attributes.getValue("r"));
            currentRow = rowNum;
            currentCol = 0;
            
            if (currentRow > 1) { // 不是表头行
                rowData = new HashMap<>();
            }
        }
        
        // 处理单元格
        else if (name.equals("c")) {
            String cellType = attributes.getValue("t");
            if (cellType != null && cellType.equals("s")) {
                nextIsString = true;
            } else {
                nextIsString = false;
            }
            
            // 获取单元格的列索引
            String cellReference = attributes.getValue("r");
            if (cellReference != null) {
                // 从A1、B1等引用中提取列索引
                String col = cellReference.replaceAll("\\d+", "");
                currentCol = convertColStringToIndex(col);
            }
        }
    }
    
    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        // 处理单元格值
        if (name.equals("v")) {
            String value = "";
            
            if (nextIsString) {
                int idx = Integer.parseInt(lastContents);
                value = new XSSFRichTextString(sst.getItemAt(idx).getString()).toString();
            } else {
                value = lastContents;
            }
            
            // 处理表头行
            if (currentRow == 1) {
                // 扩展headers列表直到可以存储当前列
                while (headers.size() <= currentCol) {
                    headers.add("");
                }
                headers.set(currentCol, value);
            }
            // 处理数据行
            else if (currentRow > 1 && !headers.isEmpty()) {
                if (currentCol < headers.size()) {
                    String fieldName = headers.get(currentCol);

//                    // 如果需要加密该字段
//                    if (Arrays.asList(encryptFields).contains(fieldName) && !value.isEmpty()) {
//                        value = EncryptionUtil.encrypt(value, encryptKey);
//                    }

                    rowData.put(fieldName, value);
                }
            }
        }
        // 处理行结束
        else if (name.equals("row")) {
            if (currentRow == 1) {
                try {
                    prepareAllTable();
                    // 准备数据库表

                } catch (Exception e) {
                    logger.error("准备数据库表时出错", e);
                    throw new SAXException(e);
                }
            }
            // 处理数据行
            else if (currentRow > 1 && !rowData.isEmpty()) {
                try {
                    processRowData();
                    recordCount++;
                    
                    if (recordCount % 10000 == 0) {
                        logger.info("已处理 {} 条记录", recordCount);
                    }
                } catch (Exception e) {
                    logger.error("添加数据到批处理时出错", e);
                    throw new SAXException(e);
                }
            }
            
            inRow = false;
        }
        // 在解析完成时确保提交剩余的批处理
        else if (name.equals("sheetData")) {
            try {
                dbManager.executeAllBatch();
                logger.info("总共处理了 {} 条记录", recordCount);
            } catch (Exception e) {
                logger.error("执行批处理时出错", e);
                throw new SAXException(e);
            }
        }
    }
    private void prepareAllTable(){
        //dbManager.prepareTable();
        prepareTable.accept(dbManager);
    }

    public void processRowData() throws SQLException {
        rowProcesses.accept(rowData);
    }

    
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        lastContents += new String(ch, start, length);
    }
    
    /**
     * 将列引用(A,B,C...AA,AB等)转换为索引(0,1,2...)
     */
    private int convertColStringToIndex(String colRef) {
        int result = 0;
        for (int i = 0; i < colRef.length(); i++) {
            result = result * 26 + (colRef.charAt(i) - 'A' + 1);
        }
        return result - 1; // 0-based index
    }
    
    public long getRecordCount() {
        return recordCount;
    }
} 