package com.dataprocessor.processor.excel;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.CommonProcessor;
import com.dataprocessor.processor.SheetHandler;
import com.dataprocessor.processor.TableEnum;
import com.dataprocessor.util.TableCache;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.hssf.usermodel.HSSFDataFormatter;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


public class CostMappingExcelProcessor extends ExcelProcessor{
    private static final Logger logger = LoggerFactory.getLogger(CostMappingExcelProcessor.class);
    public CostMappingExcelProcessor(String filePath, DatabaseManager dbManager, CommonProcessor commonProcessor) {
        super(filePath, dbManager,commonProcessor);
    }

    @Override
    public void handleRow(Map<String, String> row) {
        commonProcessor.handleCostMappingRow(row);
    }
    @Override
    public void process() throws Exception {
        long startTime = System.currentTimeMillis();

        String fileExt = filePath.substring(filePath.lastIndexOf(".")).toLowerCase();

        if (fileExt.equals(".xlsx")) {
            System.out.println("开始解析Excel文件"+filePath);
            try (OPCPackage pkg = OPCPackage.open(new File(filePath))) {
                XSSFReader reader = new XSSFReader(pkg);
                SharedStrings sst = reader.getSharedStringsTable();

                // 创建XML读取器
                XMLReader parser = XMLReaderFactory.createXMLReader();

                // 创建内容处理器
                SheetHandler handler = new SheetHandler((SharedStringsTable) sst, dbManager, encryptFields, encryptKey,this::prepareTable,this::handleRow);
                parser.setContentHandler(handler);

                // 获取第一个工作表并解析
                InputStream sheetStream = reader.getSheetsData().next();
                InputSource sheetSource = new InputSource(sheetStream);

                logger.info("开始解析Excel文件");
                parser.parse(sheetSource);
                sheetStream.close();

                long endTime = System.currentTimeMillis();
                logger.info("处理完成。共处理 {} 条记录，耗时 {} 秒",
                        handler.getRecordCount(), (endTime - startTime) / 1000.0);
            }
        } else if (fileExt.equals(".xls")) {
            prepareTable(super.dbManager);
            try (InputStream fis = new FileInputStream(filePath);
                  HSSFWorkbook workbook = new HSSFWorkbook(fis)) {
                // 处理第一个sheet
                HSSFSheet sheet = workbook.getSheetAt(0);
                logger.info("开始解析Excel文件");

                // 使用 HSSFDataFormatter 统一处理单元格值
                HSSFDataFormatter formatter = new HSSFDataFormatter();

                // 存储表头
                Row headerRow = sheet.getRow(0);
                int lastCellNum = headerRow.getLastCellNum();
                String[] headers = new String[lastCellNum];

                // 提取表头
                for (int i = 0; i < lastCellNum; i++) {
                    Cell cell = headerRow.getCell(i);
                    headers[i] = formatter.formatCellValue(cell);
                }

                int recordCount = 0;

                // 遍历数据行（从第1行开始）
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // 跳过表头行
                    Map<String, String> rowData = new HashMap<>();

                    for (int i = 0; i < lastCellNum; i++) {
                        if(Strings.isNotBlank(headers[i])){
                            Cell cell = row.getCell(i);
                            String value = formatter.formatCellValue(cell);
                            rowData.put(headers[i], value);
                        }
                    }

                    // 调用 handleRow 方法处理当前行
                    handleRow(rowData);
                    recordCount++;
                }

                long endTime = System.currentTimeMillis();
                logger.info("处理完成。共处理 {} 条记录，耗时 {} 秒",
                        recordCount, (endTime - startTime) / 1000.0);
            }
        }



    }

    public void prepareTable(DatabaseManager databaseManager) {
       commonProcessor.prepareCostMappingTable(databaseManager);
    }
}
