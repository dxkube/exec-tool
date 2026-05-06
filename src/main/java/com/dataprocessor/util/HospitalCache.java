package com.dataprocessor.util;

import com.dataprocessor.db.DatabaseManager;
import com.dataprocessor.processor.TableConfig;
import com.dataprocessor.processor.TableEnum;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.*;

/**
 * @Author: RedFlag
 * @CreateTime: 2025-08-20  11:39
 * @Description:
 */
@Slf4j
public class HospitalCache {
    public static DatabaseManager dbManager;

    private static final Map<String, String> hospitalInfos = new HashMap<>();

    public static void init(DatabaseManager dbManager)  {
        HospitalCache.dbManager = dbManager;
       if (hospitalInfos.isEmpty()){
           setHospitalInfo() ;
       }
       if (!hospitalInfos.isEmpty()){
           log.info("医院信息初始化成功");
       }
    }

    public static Map<String, String> setHospitalInfo()  {
        String getHospitalSql = "SELECT hos_code, hos_name FROM ods.tbl_hospital_info_bak";
        List<Map<String, Object>> hospitalResults = dbManager.querySql(getHospitalSql, null);
        for (Map<String, Object> map : hospitalResults) {
            hospitalInfos.put((String)map.get("hos_code"), (String) map.get("hos_name"));
        }
        return hospitalInfos;
    }
    public static String getHospitalName(String hosCode) {
        return hospitalInfos.get(hosCode);
    }
}