package com.dataprocessor.processor.excel;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.CommonProcessor;
import com.dataprocessor.processor.SheetHandler;
import com.dataprocessor.processor.TableEnum;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Map;

public class TurkeyExcelProcessor extends ExcelProcessor{
    private static final Logger logger = LoggerFactory.getLogger(TurkeyExcelProcessor.class);
    public TurkeyExcelProcessor(String filePath, DatabaseManager dbManager, CommonProcessor commonProcessor) {
        super(filePath, dbManager,commonProcessor);
    }


    @Override
    public void handleRow(Map<String, String> row) {
    commonProcessor.handleTurkeyRow(row);
    }

    @Override
    public void process() throws Exception {
        long startTime = System.currentTimeMillis();

        try (OPCPackage pkg = OPCPackage.open(new File(filePath))) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();

            // 创建XML读取器
            XMLReader parser = XMLReaderFactory.createXMLReader();

            // 创建内容处理器
            SheetHandler handler = new SheetHandler((SharedStringsTable) sst, dbManager, encryptFields, encryptKey,this::prepareTable,this::handleRow);
            parser.setContentHandler(handler);

            // 获取第一个工作表并解析
            InputStream sheetStream = reader.getSheet("rId1");
            InputSource sheetSource = new InputSource(sheetStream);

            logger.info("开始解析Excel文件");
            parser.parse(sheetSource);
            sheetStream.close();

            long endTime = System.currentTimeMillis();
            logger.info("处理完成。共处理 {} 条记录，耗时 {} 秒",
                    handler.getRecordCount(), (endTime - startTime) / 1000.0);
        }
    }

    public void prepareTable(DatabaseManager databaseManager) {
        commonProcessor.prepareTurkeyTable(databaseManager);
    }


}
