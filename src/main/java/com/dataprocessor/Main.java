package com.dataprocessor;

import com.alibaba.fastjson.JSON;
import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.CommonProcessor;
import com.dataprocessor.processor.TableConfig;
import com.dataprocessor.processor.TableEnum;
import com.dataprocessor.processor.csv.CostMappingCsvProcessor;
import com.dataprocessor.processor.csv.TurkeyCsvProcessor;
import com.dataprocessor.processor.db.DataProcessor;
import com.dataprocessor.processor.db.TableProcessorFactory;
import com.dataprocessor.processor.excel.CostMappingExcelProcessor;
import com.dataprocessor.processor.FileProcessor;
import com.dataprocessor.processor.excel.TurkeyExcelProcessor;
import com.dataprocessor.util.TableCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (args.length != 0){
            args[0] = "-table=dwd_fee_sum_group_mrhp";
        }

        if (args[0].startsWith("-csv")){
            processFile( args);
        }else if (args[0].startsWith("-table")){
            processTable( args);
        } else {
            throw new RuntimeException("请提供正确的参数");
        }
    }
    public static void processTable(String[] args) throws  Exception{
        Properties config = loadConfig();
        String id= UUID.randomUUID().toString();

        DatabaseManager dbManager = new DatabaseManager(
                config.getProperty("db.url"),
                config.getProperty("db.user"),
                config.getProperty("db.password"),
                Integer.parseInt(config.getProperty("db.batchSize", "100")),id
        );
        Map<String, List<Map<String, Object>>> configYaml = loadConfigYaml();
        for (Map<String, Object> tableConfigMap : configYaml.get("tables")) {
            TableConfig tableConfig = JSON.parseObject(JSON.toJSONString(tableConfigMap), TableConfig.class);
            TableCache.addTableConfig(TableEnum.valueOf(tableConfig.getName()),tableConfig);
        }
        String tableName = args[0].split("=")[1];
        DataProcessor processor = TableProcessorFactory.createProcessor(tableName, dbManager);
        processor.process();
    }
    private static void processFile(String[] args){
        String id= UUID.randomUUID().toString();
        String filePath;
        if (args.length < 1) {
            logger.error("请提供文件路径作为参数");
//            filePath="D:\turkey-data\华润武钢总医院_202409";  51导入的
            //todo fix 南京市儿童医院_202409
            //filePath="D:\turkey-data\南京市儿童医院_202409";  xlsx解析有问题
            filePath = "D:\\turkey-data\\武汉市汉口医院_202409";
        } else {
            filePath = args[0].split("=")[1];
        }
        System.out.println(filePath);

        File file = new File(filePath);
        if (!file.exists()) {
            logger.error("文件不存在: {}", filePath);
            System.exit(1);
            return;
        }
        if (!file.isDirectory()) {
            logger.error("请提供文件路径作为参数");
            System.exit(1);
        }

        Properties config = loadConfig();

        Map<String, List<Map<String, Object>>> configYaml = loadConfigYaml();
        for (Map<String, Object> tableConfigMap : configYaml.get("tables")) {
            TableConfig tableConfig = JSON.parseObject(JSON.toJSONString(tableConfigMap), TableConfig.class);
            TableCache.addTableConfig(TableEnum.valueOf(tableConfig.getName()),tableConfig);
        }

        try {
            // 创建数据库管理器
            DatabaseManager dbManager = new DatabaseManager(
                    config.getProperty("db.url"),
                    config.getProperty("db.user"),
                    config.getProperty("db.password"),
                    Integer.parseInt(config.getProperty("db.batchSize", "100")),id
            );
            File[] files = file.listFiles();
            File file1 = Arrays.asList(files).stream().filter(f -> f.getName().contains("费用映射")).findFirst().orElse(null);
            List<File> files2 = Arrays.asList(files).stream().filter(f -> !f.getName().contains("费用映射")).collect(Collectors.toList());
            assert file1 != null;
            processCostMappingFile(file1, dbManager);
            files2.forEach(f -> {
                try {
                    turkeyProcessFile(f, dbManager);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (Exception e) {
            logger.error("处理过程中发生错误", e);
            System.exit(1);
        }
    }

    private static void processCostMappingFile(File file, DatabaseManager dbManager) throws Exception {
        // 根据文件类型选择相应的处理器
        FileProcessor processor;
        String filePath = file.getAbsolutePath();
        if (filePath.toLowerCase().endsWith(".csv")) {
            processor = new CostMappingCsvProcessor(filePath, dbManager, new CommonProcessor(dbManager));
        } else if (filePath.toLowerCase().endsWith(".xlsx") ||
                filePath.toLowerCase().endsWith(".xls")) {
            processor = new CostMappingExcelProcessor(filePath, dbManager,new CommonProcessor(dbManager));
        } else {
            logger.error("不支持的文件类型: {}", filePath);
            System.exit(1);
            return;
        }
        // 处理文件
        logger.info("开始处理文件: {}", filePath);
        processor.process();
        logger.info("文件处理完成");
    }

    private static void turkeyProcessFile(File file, DatabaseManager dbManager) throws Exception {
        // 根据文件类型选择相应的处理器
        FileProcessor processor;
        String filePath = file.getAbsolutePath();
        if (filePath.toLowerCase().endsWith(".csv")) {
            processor = new TurkeyCsvProcessor(filePath, dbManager,new CommonProcessor(dbManager));
        } else if (filePath.toLowerCase().endsWith(".xlsx") ||
                filePath.toLowerCase().endsWith(".xls")) {
            processor = new TurkeyExcelProcessor(filePath, dbManager,new CommonProcessor(dbManager));
        } else {
            logger.error("不支持的文件类型: {}", filePath);
            System.exit(1);
            return;
        }
        // 处理文件
        logger.info("开始处理文件: {}", filePath);
        processor.process();
        logger.info("文件处理完成");
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (InputStream input = Main.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.warn("无法找到config.properties文件，使用默认配置");
                return properties;
            }
            properties.load(input);
        } catch (IOException e) {
            logger.warn("加载配置文件时出错", e);
        }
        return properties;
    }

    //yaml 读取
    private static Map<String, List<Map<String, Object>>> loadConfigYaml() {
        Map<String, List<Map<String, Object>>> properties = new HashMap<>();
        Yaml yaml = new Yaml();
        try (InputStream input = Main.class.getClassLoader()
                .getResourceAsStream("tableConfig.yaml")) {
            if (input == null) {
                logger.warn("无法找到config.yaml文件，使用默认配置");
                return properties;
            }
            properties=yaml.load(input);
        } catch (IOException e) {
            logger.warn("加载配置文件时出错", e);
        }
        return properties;
    }

} 