package com.dataprocessor.processor;

import com.alibaba.fastjson.JSONObject;
import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.util.TableCache;
import org.apache.logging.log4j.util.Strings;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonProcessor {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CommonProcessor.class);
    private DatabaseManager databaseManager;

    public CommonProcessor(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void prepareCostMappingTable(DatabaseManager databaseManager) {
        try {
            databaseManager.clean();
            databaseManager.prepareTable(TableEnum.cost_mapping_aggregation);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleCostMappingRow(Map<String, String> row) {
        try {
            TableCache.addRow(row);
            databaseManager.addBatch(TableEnum.cost_mapping_aggregation, row);
        } catch (SQLException e) {
            logger.error("费用映射聚合表:Failed to add batch to database", e);
            throw new RuntimeException(e);
        }
    }

    public void prepareTurkeyTable(DatabaseManager databaseManager) {
        try {
            databaseManager.clean();
            databaseManager.prepareTable(TableEnum.group_param, TableEnum.cost_detail);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleTurkeyRow(Map<String, String> row) {
        logger.info("处理TurkeyCsvProcessor:{}", row);
        handBase(row);
        if (row.get("SOURCE_FLAG") == null) {
            Integer type = Integer.parseInt(row.get("SOURCE_FLAG"));
            switch (type) {
                case 1: //病案
                    handleBA(row);
                    break;
                case 2: //清单
                    handleQD(row);
                    break;
                default:
                    break;
            }

        }
    }

    public void handBase(Map<String, String> row) {
        //分组入参
        handOne(TableEnum.group_param, row);
        //费用明细
        handOne(TableEnum.cost_detail, row);
    }

    public void handOne(TableEnum tableEnum, Map<String, String> row) {
        TableConfig config = TableCache.getTableConfig(tableEnum);
        Map<String, Object> param = new HashMap<>();

        config.getColumns().forEach(columnConfig -> {
            handColumn(columnConfig, param, row);
        });


        ColumnConfig listConfig = config.getColumns().stream().filter(columnConfig -> "list".equals(columnConfig.getType())).findFirst().orElse(null);
        if (TableEnum.group_param.equals(tableEnum)) {
            param.put("IRPDID", Strings.isBlank((String) param.get("SETTLE_DATE")) ? null : ((String) param.get("SETTLE_DATE")).split(" ")[0]);
        }

        if (listConfig != null) {

            List<Map> list = (List<Map>) getFiled(row, listConfig.getName());
            if (list == null) {
                return;
            }
            //处理list  一行转换成多条
            for (Map map : list) {
                Map<String, Object> subParam = param;
                for (ColumnConfig columnConfig : listConfig.getColumns()) {
                    handColumn(columnConfig, subParam, map);
                }
                // 费用映射匹配
                if (TableEnum.cost_detail.equals(tableEnum)) {
                    String orgCode = (String) subParam.get("orgCode");
                    String feeCode = (String) subParam.get("feeCode");
                    String itemClassCode = (String) subParam.get("itemClassCode");
                    Map<String, String> fyysMap = TableCache.getRows().stream().filter(fyysMap1 -> fyysMap1.containsKey("ORG_CODE") && fyysMap1.get("ORG_CODE").equals(orgCode)
                            && fyysMap1.containsKey("HOS_PROJECT_CODE") && fyysMap1.get("HOS_PROJECT_CODE").equals(feeCode)
                            && fyysMap1.containsKey("CHARGE_TYPE_CODE_HS") && fyysMap1.get("CHARGE_TYPE_CODE_HS").equals(itemClassCode)
                    ).findFirst().orElse(null);
                    if (fyysMap != null) {
                        subParam.put("mapping_mark", 1);
                        subParam.put("CHARGE_TYPE_NAME_HS", fyysMap.get("CHARGE_TYPE_NAME_HS"));
                        subParam.put("INSURANCE_NAME", fyysMap.get("INSURANCE_NAME"));
                        subParam.put("INS_NATION_PROJECT_CODE", fyysMap.get("INS_NATION_PROJECT_CODE"));
                        subParam.put("INS_NATION_PROJECT_NAME", fyysMap.get("INS_NATION_PROJECT_NAME"));
                        subParam.put("MIJA_PROJECT_CODE", fyysMap.get("MIJA_PROJECT_CODE"));
                        subParam.put("MIJA_PROJECT_NAME", fyysMap.get("MIJA_PROJECT_NAME"));
                    } else {
                        subParam.put("mapping_mark", 0);
                        subParam.put("CHARGE_TYPE_NAME_HS", "");
                        subParam.put("INSURANCE_NAME", "");
                        subParam.put("INS_NATION_PROJECT_CODE", subParam.get("feeCode"));
                        subParam.put("INS_NATION_PROJECT_NAME", subParam.get("feeName"));
                        subParam.put("MIJA_PROJECT_CODE", subParam.get("feeCode"));
                        subParam.put("MIJA_PROJECT_NAME", subParam.get("feeName"));
                    }
                }
                try {
                    databaseManager.addBatch(tableEnum, param);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

        } else {
            try {
                databaseManager.addBatch(tableEnum, param);
            } catch (SQLException e) {
                logger.error("Failed to add batch to database", e);
            }
        }

    }

    private Object getFiled(Map row, String source) {
        String[] fileds = source.split("\\.");
        JSONObject jsonObject = JSONObject.parseObject((String) row.get(fileds[0]));
        Object value = jsonObject;
        if (fileds.length > 1) {
            for (int i = 1; i < fileds.length; i++) {
                if (jsonObject == null) {
                    return null;
                }
                String name = fileds[i];
                if (i == fileds.length - 1) {
                    value = jsonObject.get(name);
                } else {
                    jsonObject = jsonObject.getJSONObject(name);
                }

            }
        }
        return value;
    }

    private void handColumn(ColumnConfig columnConfig, Map<String, Object> groupParam, Map row) {
        if (columnConfig.getSource() != null) {
            groupParam.put(columnConfig.getName(), String.valueOf(getFiled(row, columnConfig.getSource())));
        } else if ("list".equals(columnConfig.getType())) {
            //list的不处理 最后再统一处理
        } else {
            groupParam.put(columnConfig.getName(), row.get(columnConfig.getName()));
        }
    }

    public void handleBA(Map<String, String> row) {


    }

    public void handleQD(Map<String, String> row) {

    }


}
