package com.dataprocessor.processor.db.stragy;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.TableEnum;
import com.dataprocessor.processor.db.DataProcessor;
import com.dataprocessor.util.HospitalCache;
import com.dataprocessor.util.TableCache;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: RedFlag
 * @CreateTime: 2025-08-20  15:05
 * @Description: dwd_fee_sum_group_mrhp -> cost_detail (多线程优化版)
 */
@Slf4j
public class DwdFeeProcessor implements DataProcessor {

    private static final int PAGE_SIZE = 100000;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final int BATCH_COMMIT_SIZE = 100000;

    private final DatabaseManager dbManager;
    private final String querySqlTemplate = "SELECT *\n" +
            "FROM ods.dwd_fee_sum_group_mrhp\n" +
            "WHERE upload_time >= CURRENT_DATE - INTERVAL '1 day'\n" +
            "  AND upload_time < CURRENT_DATE\n" +
            "ORDER BY upload_time\n" +
            "LIMIT %d OFFSET %d;";

    private final String countSql =
            "SELECT COUNT(*) AS total FROM ods.dwd_fee_sum_group_mrhp ";

    private final String mappingSql = "SELECT \n" +
            "       hos_project_code, \n" +
            "       charge_type_code_hs, \n" +
            "       charge_type_name_hs, \n" +
            "       insurance_name, \n" +
            "       ins_nation_project_code, \n" +
            "       ins_nation_project_name, \n" +
            "       mija_project_code, \n" +
            "       mija_project_name \n" +
            "FROM (\n" +
            "    SELECT *,\n" +
            "           ROW_NUMBER() OVER (\n" +
            "               PARTITION BY hos_project_code, charge_type_code_hs\n" +
            "           ) AS rn\n" +
            "    FROM ods.tbl_dim_charge_item_mapping_mrhp\n" +
            ") t\n" +
            "WHERE rn = 1;\n";

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            THREAD_POOL_SIZE,
            THREAD_POOL_SIZE,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                private final AtomicInteger threadNum = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "dwd-fee-processor-" + threadNum.getAndIncrement());
                    thread.setDaemon(false);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final Map<String, Map<String, Object>> mappingCache = new ConcurrentHashMap<>();
    private final String batchDate;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 用于跟踪处理进度和控制批量提交
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final Object batchLock = new Object();

    public DwdFeeProcessor(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.batchDate = new SimpleDateFormat("yyyyMM").format(new Date());
        HospitalCache.init(dbManager);
        preloadMappingCache();
    }

    private void preloadMappingCache() {
        log.info("开始加载映射缓存数据");
        long startTime = System.currentTimeMillis();

        List<Map<String, Object>> mappings = dbManager.querySql(mappingSql, null);
        for (Map<String, Object> mapping : mappings) {
            String key = buildMappingKey(
                    mapping.get("hos_project_code"),
                    mapping.get("charge_type_code_hs")
            );
            mappingCache.put(key, mapping);
        }

        log.info("映射缓存加载完成，共加载 {} 条记录，耗时 {}ms",
                mappingCache.size(), System.currentTimeMillis() - startTime);
    }

    private String buildMappingKey(Object itemCode, Object itemClassCode) {
        return safeToString(itemCode) + "|" + safeToString(itemClassCode);
    }

    @Override
    public void process() {
        log.info("开始执行 dwd_fee_sum_group 数据清洗");
        long startTime = System.currentTimeMillis();

        // 获取总记录数
        int totalRows = getTotalRowCount();
        if (totalRows == 0) {
            log.info("没有需要处理的数据");
            return;
        }

        log.info("发现 {} 条数据需要处理，将使用 {} 个线程并行处理", totalRows, THREAD_POOL_SIZE);

        // 计算总页数
        int totalPages = (totalRows + PAGE_SIZE - 1) / PAGE_SIZE;
        log.info("将分页处理为 {} 个任务", totalPages);
        List<Future<?>> futures = new ArrayList<>(totalPages);

        // 提交所有分页任务
        for (int page = 0; page < totalPages; page++) {
            final int currentPage = page;
            Future<?> future = executor.submit(() -> processPage(currentPage));
            futures.add(future);
        }

        // 等待所有任务完成
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("处理分页时发生异常", e);
                // 中断所有线程并退出
                executor.shutdownNow();
                throw new RuntimeException("数据处理失败", e);
            }
        }

        // 提交剩余的批次数据
        dbManager.executeAllBatch();

        // 关闭线程池
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        long endTime = System.currentTimeMillis();
        log.info("dwd_fee_sum_group 数据清洗完成，共处理 {} 条记录，耗时 {}ms，平均速度 {} 条/秒",
                totalRows,
                (endTime - startTime),
                (int)(totalRows * 1000.0 / (endTime - startTime + 1)));
    }

    /**
     * 获取总记录数
     */
    private int getTotalRowCount() {
        try {
            List<Map<String, Object>> result = dbManager.querySql(countSql, null);
            if (result.isEmpty()) {
                return 0;
            }
            Object totalObj = result.get(0).get("total");
            return totalObj instanceof Number ? ((Number) totalObj).intValue() : 0;
        } catch (Exception e) {
            log.error("获取总记录数失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理指定页码的数据
     */
    private void processPage(int page) {
        int offset = page * PAGE_SIZE;
        String querySql = String.format(querySqlTemplate, PAGE_SIZE, offset);

        try {
            List<Map<String, Object>> dwdFeeRows = dbManager.querySql(querySql, null);
            if (dwdFeeRows.isEmpty()) {
                return;
            }

            log.info("线程 {} 开始处理第 {} 页数据，共 {} 条",
                    Thread.currentThread().getName(), page, dwdFeeRows.size());

            for (Map<String, Object> rawRow : dwdFeeRows) {
                processRow(rawRow);
                int count = processedCount.incrementAndGet();
                if (count % BATCH_COMMIT_SIZE == 0) {
                    synchronized (batchLock) {
                        dbManager.executeAllBatch();
                    }
                    log.info("已处理 {} 条记录", count);
                }
            }

            log.debug("线程 {} 完成第 {} 页数据处理",
                    Thread.currentThread().getName(), page);

        } catch (Exception e) {
            log.error("处理第 {} 页数据时发生异常", page, e);
            throw new RuntimeException("处理分页数据失败", e);
        }
    }

    /**
     * 处理单条记录
     */
    private void processRow(Map<String, Object> rawRow) {
        Map<String, String> row = new HashMap<>();
        rawRow.forEach((k, v) -> row.put(k, safeToString(v)));

        row.put("feecode", safeToString(rawRow.get("item_code")));
        row.put("feename", safeToString(rawRow.get("item_name")));
        row.put("itemclasscode", safeToString(rawRow.get("item_class_code")));
        row.put("medicalInsurancecode", safeToString(rawRow.get("insurance_code")));
        row.put("orgcode", "");
        row.put("number", safeToString(rawRow.get("amount")));
        row.put("fee", safeToString(rawRow.get("price")));
        row.put("source_flag", "");
        row.put("hos_code", safeToString(rawRow.get("clean_hospital_code")));

        String hosCode = row.getOrDefault("clean_hospital_code", "");
        String hosName = Optional.ofNullable(HospitalCache.getHospitalName(hosCode)).orElse(hosCode);
        row.put("batch_name", hosName + "_" + batchDate);
        row.put("batch_id", row.get("config_id"));

        String mappingKey = buildMappingKey(row.get("item_code"), row.get("item_class_code"));
        Map<String, Object> mapping = mappingCache.get(mappingKey);

        if (mapping != null) {
            row.put("mapping_mark", "1");
            row.put("charge_type_name_hs", safeToString(mapping.get("charge_type_name_hs")));
            row.put("insurance_name", safeToString(mapping.get("insurance_name")));
            row.put("ins_nation_project_code", safeToString(mapping.get("ins_nation_project_code")));
            row.put("ins_nation_project_name", safeToString(mapping.get("ins_nation_project_name")));
            row.put("mija_project_code", safeToString(mapping.get("mija_project_code")));
            row.put("mija_project_name", safeToString(mapping.get("mija_project_name")));
        } else {
            row.put("mapping_mark", "0");
            row.put("charge_type_name_hs", "");
            row.put("insurance_name", "");
            row.put("ins_nation_project_code", row.get("feecode"));
            row.put("ins_nation_project_name", row.get("feename"));
            row.put("mija_project_code", row.get("feecode"));
            row.put("mija_project_name", row.get("feecode"));
        }

        row.put("created_at", sdf.format(System.currentTimeMillis()));
        cleanRow(row);

        try {
            synchronized (batchLock) {
//                TableCache.addRow(row);
                dbManager.addBatch(TableEnum.cost_detail, row);
            }
        } catch (SQLException e) {
            log.error("添加记录到批量处理时失败", e);
            throw new RuntimeException(e);
        }
    }

    private String safeToString(Object obj) {
        return Objects.toString(obj, "");
    }

    public void cleanRow(Map<String, String> row) {
        String[] columns = TableEnum.cost_detail.getColumns();
        Set<String> columnSet = new HashSet<>(Arrays.asList(columns));
        Iterator<Map.Entry<String, String>> iterator = row.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if ("hos_code".equals(entry.getKey())){
                continue;
            }
            if (!columnSet.contains(entry.getKey())) {
                iterator.remove();
            }
        }
    }
}
