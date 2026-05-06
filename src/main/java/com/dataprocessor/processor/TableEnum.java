package com.dataprocessor.processor;


public enum TableEnum {

    cost_mapping_aggregation("费用映射聚合表", new String[]{"hos_code", "hos_name", "hos_project_code", "hos_project_name",
            "charge_type_code_hs", "charge_type_name_hs", "ins_city_project_code",
            "ins_city_project_name", "ins_pro_project_code", "ins_pro_project_name",
            "mija_project_code", "mija_project_name", "mija_medicine_generic_code",
            "mija_medicine_generic_name", "mija_medicine_dosage_code", "mija_medicine_dosage_name",
            "mija_medicine_spec_code", "mija_medicine_spec_name", "mija_medicine_package_code",
            "mija_medicine_package_name", "mija_medicine_company_code", "mija_medicine_company_name",
            "ins_nation_project_code", "ins_nation_project_name", "mapping_mode", "mapping_prob",
            "mapping_date", "mija_database_version", "fee_dictionary_version", "data_source",
            "insurance_code", "org_code", "org_name", "big_class_name", "big_class_map_prob",
            "big_class_map_mode", "big_class_preprocess_name", "single_module_preprocess_name",
            "es_keyword", "frequency", "fee_standardize_version", "insurance_name", "batch_id", "batch_name"},
            new String[]{"HOS_CODE", "HOS_PROJECT_CODE", "CHARGE_TYPE_CODE_HS", "MIJA_PROJECT_CODE", "INS_NATION_PROJECT_CODE", "INSURANCE_CODE", "ORG_CODE", "BATCH_ID", "BATCH_NAME"}, new String[]{}),

    cost_mapping_aggregation_mrhp("费用映射聚合表", new String[]{"hos_code", "hos_name", "hos_project_code", "hos_project_name",
            "charge_type_code_hs", "charge_type_name_hs", "ins_city_project_code",
            "ins_city_project_name", "ins_pro_project_code", "ins_pro_project_name",
            "mija_project_code", "mija_project_name", "mija_medicine_generic_code",
            "mija_medicine_generic_name", "mija_medicine_dosage_code", "mija_medicine_dosage_name",
            "mija_medicine_spec_code", "mija_medicine_spec_name", "mija_medicine_package_code",
            "mija_medicine_package_name", "mija_medicine_company_code", "mija_medicine_company_name",
            "ins_nation_project_code", "ins_nation_project_name", "mapping_mode", "mapping_prob",
            "mapping_date", "mija_database_version", "fee_dictionary_version", "data_source",
            "insurance_code", "org_code", "org_name", "big_class_name", "big_class_map_prob",
            "big_class_map_mode", "big_class_preprocess_name", "single_module_preprocess_name",
            "es_keyword", "frequency", "fee_standardize_version", "insurance_name", "batch_id", "batch_name"},
            new String[]{"HOS_CODE", "HOS_PROJECT_CODE", "CHARGE_TYPE_CODE_HS", "MIJA_PROJECT_CODE", "INS_NATION_PROJECT_CODE", "INSURANCE_CODE", "ORG_CODE", "BATCH_ID", "BATCH_NAME"}, new String[]{}),

    group_param_mrhp("分组入参表", new String[]{"pid", "zdcode", "sscode", "age", "czdcode", "csscode", "daynum", "sex",
            "xsetz", "lyfs", "py_group_param", "settle_date", "source_flag", "iprdid", "hos_code", "batch_id", "batch_name"},
            new String[]{"pid", "zdcode", "sscode", "age", "czdcode", "csscode", "daynum", "sex",
                    "xsetz", "lyfs", "py_group_param", "hos_code", "batch_id", "batch_name"}, new String[]{}),

    group_param("分组入参表", new String[]{"pid", "zdcode", "sscode", "age", "czdcode", "csscode", "daynum", "sex",
            "xsetz", "lyfs", "py_group_param", "settle_date", "source_flag", "iprdid", "hos_code", "batch_id", "batch_name"},
            new String[]{"pid", "zdcode", "sscode", "age", "czdcode", "csscode", "daynum", "sex",
                    "xsetz", "lyfs", "py_group_param", "hos_code", "batch_id", "batch_name"}, new String[]{}),

    cost_detail("费用明细表", new String[]{"pid", "mapping_mark", "feecode", "feename", "itemclasscode",
            "charge_type_name_hs", "medicalInsurancecode", "insurance_name", "ins_nation_project_code", "ins_nation_project_name",
            "mija_project_code", "mija_project_name", "orgcode", "number", "fee", "source_flag", "batch_name", "batch_id"}
            , new String[]{"pid", "feecode", "itemclasscode", "ins_nation_project_code", "mija_project_code", "number", "fee", "source_flag",
            "batch_name", "batch_id"}, new String[]{}),

    cost_detail_mrhp("费用明细表", new String[]{"pid", "mapping_mark", "feecode", "feename", "itemclasscode",
            "charge_type_name_hs", "medicalInsurancecode", "insurance_name", "ins_nation_project_code", "ins_nation_project_name",
            "mija_project_code", "mija_project_name", "orgcode", "number", "fee", "source_flag", "batch_name", "batch_id"}
            , new String[]{"pid", "feecode", "itemclasscode", "ins_nation_project_code", "mija_project_code", "number", "fee", "source_flag",
            "batch_name", "batch_id"}, new String[]{}),


    patient_basic_info("病案基础信息表", new String[]{"pid", "usercode", "username", "ylfkfs", "jkkh", "zycs", "bah", "xm", "xb", "xbmc",
            "csrq", "nl", "gj", "gjmc", "bzyzsnl", "xsenl", "xsecstz", "dxsecstz", "xserytz",
            "dxserytz", "csd", "gg", "mz", "mzdm", "mzmc", "sfzh", "zy", "zydm", "zymc", "hy",
            "jzdzmc_sheng", "jzdzmc_shi", "jzdzmc_xian", "jzdz_mph", "jzdz_sheng", "jzdz_shi",
            "jzdz_xian", "dh", "xzz", "yb1", "hkdz", "yb2", "gzdwdz", "gzdwjdz", "gzdwmc", "dwdh",
            "yb", "yb3", "lxrxm", "gx", "gxmc", "dz", "lxdzmc_sheng", "lxdzmc_shi", "lxdzmc_xian",
            "lxdz_sheng", "lxdz_shi", "lxdz_xian", "lxdz_xxdz", "dh2", "rytj", "rytjmc", "rysj",
            "rysjs", "rykb", "rykbmc", "rybf", "zkkb", "zkkbmc", "cysj", "cysjs", "cykb", "cykbmc",
            "cybf", "sjzyts", "jbbm_xy", "mzzd_xy", "sunshangzdlist", "binglidiagnosislist", "ywgm",
            "gmyw", "swhzsj", "xx", "rh", "kzr", "zrys", "zzys", "zzysmc", "zyys", "zrhs", "zrhsmc",
            "jxys", "sxys", "bmy", "bazl", "zkys", "zkhs", "zkrq", "lyfs", "lyfsmc", "yzzy_jgdm",
            "yzzy_jgmc", "yzzy_yljg", "wsy_yljg", "sfzzyjh", "sfzzyjhmc", "md", "ryh_f", "ryh_t",
            "ryh_xs", "ryq_f", "ryq_t", "ryq_xs", "zfy", "zfje", "ylfuf", "zlczf", "hlf", "qtfy",
            "blzdf", "syszdf", "yxxzdf", "lczdxmf", "fsszlxmf", "wlzlf", "sszlf", "maf", "ssf", "kff",
            "zyzlf", "xyf", "kjywf", "zcyf", "zcyf1", "xf", "bdblzpf", "qdblzpf", "nxyzlzpf",
            "xbyzlzpf", "hcyyclf", "yyclf", "ycxyyclf", "qtf", "zyqtf", "dbzgl", "lcljgl", "zdfhqk",
            "mcfhbz", "mcfhbzmc", "rcfhbz", "rcfhbzmc", "sqysh", "sqyshmc", "lcycl", "lcyclmc",
            "fsybl", "fsyblmc", "qjqk", "cgcs", "qjcs", "zgqk", "vetime", "icu", "stllf", "batch_id"}, new String[]{},
            new String[]{"XM", "SFZH", "CSD", "DH", "HKDZ", "GZDWDZ", "GZDWJDZ", "GZDWMC", "DWDH", "LXRXM", "DZ", "DH2"}),

    patient_diagnosis_surgery_info("病案诊断手术信息表", new String[]{"diagnosisList", "surgeryList"}, new String[]{}, new String[]{}),

    bill_basic_info("清单基础信息表", new String[]{"pid", "qdlsh", "ddyljgmc", "ddyljgdm", "ybjsdj", "ybbh", "bah", "sbsj", "xm", "xb",
            "xbmc", "csrq", "nl", "gj", "gjmc", "bzyzsnl", "xsenl", "mz", "mzdm", "mzmc",
            "hzzjlb", "hzzjhm", "zy", "zydm", "zymc", "xzz", "jzdzmc_sheng", "jzdzmc_shi",
            "jzdzmc_xian", "jzdz_mph", "jzdz_sheng", "jzdz_shi", "jzdz_xian", "gzdwdz",
            "gzdwjdz", "gzdwmc", "dwdh", "yb", "yb3", "lxrxm", "gx", "gxmc", "dz",
            "lxdzmc_sheng", "lxdzmc_shi", "lxdzmc_xian", "lxdz_sheng", "lxdz_shi", "lxdz_xian",
            "lxdz_xxdz", "dh2", "yblx", "tsrylx", "cbd", "sserylxxmc", "xsecstz", "dxsecstz",
            "xserytz", "dxserytz", "mt_jzrq", "mt_zdkb", "mt_zdkbmc", "cyjbdm_zb", "cyjbdm_zz",
            "outDiagnosisOpsDtoList", "outSurgeryOpsDtoList", "zyyllx", "zyyllxmc",
            "rytj", "rytjmc", "zllx", "zllxmc", "rysj", "rysjs", "rykb", "rykbmc", "zkkb",
            "zkkbmc", "cykb", "cykbmc", "cysj", "cysjs", "sjzyts", "jbbm_xy", "mzzd_xy",
            "jbbm_zy", "mzzd_zy", "zddmjs", "ssjczdmjs", "cyzyzd_zb", "cyzyzd_zz",
            "rybq_zyzb", "rybq_zyzz", "hxjsy_fz", "hxjsy_ts", "hxjsy_xs", "ryh_f", "ryh_t",
            "ryh_xs", "ryq_f", "ryq_t", "ryq_xs", "intensiveCareDtoList",
            "bloodTransfusionList", "vetime", "icu", "stllf", "batch_id"}, new String[]{},
            new String[]{"XM", "XZZ", "GZDWDZ", "GZDWJDZ", "GZDWMC", "DWDH", "LXRXM", "DZ", "DH2"}),

    bill_diagnosis_surgery_info("清单诊断手术信息表", new String[]{"diagnosisList", "surgeryList"}, new String[]{}, new String[]{});

    private String desc;
    private String[] columns;
    private String[] notNullColumns;
    private String[] encryptFields;

    TableEnum(String desc, String[] columns, String[] notNullColumns, String[] encryptFields) {
        this.desc = desc;
        this.columns = columns;
        this.notNullColumns = notNullColumns;
        this.encryptFields = encryptFields;
    }

    public String getDesc() {
        return desc;
    }

    public String[] getColumns() {
        return columns;
    }

    public String[] getNotNullColumns() {
        return notNullColumns;
    }

    public String[] getEncryptFields() {
        return encryptFields;
    }

    public static TableEnum[] getTurkeyTables() {
        return new TableEnum[]{
                group_param_mrhp,
                group_param,
                cost_detail,
                patient_basic_info,
                patient_diagnosis_surgery_info,
                bill_basic_info,
                bill_diagnosis_surgery_info
        };
    }

}
