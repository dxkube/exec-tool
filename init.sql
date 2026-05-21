
drop table if exists group_param;
CREATE TABLE group_param (
                             id BIGSERIAL  PRIMARY KEY,
                             PID varchar(255),
                             zdcode varchar(255),
                             sscode varchar(255),
                             age DOUBLE PRECISION,
                             czdcode varchar(512),
                             csscode varchar(512),
                             daynum DOUBLE PRECISION,
                             sex varchar(255),
                             xsetz DOUBLE PRECISION,
                             lyfs DOUBLE PRECISION,
                             PY_GROUP_PARAM text,
                             SETTLE_DATE TIMESTAMP,
                             SOURCE_FLAG INTEGER,
                             IRPDID varchar(255),
                             HOS_CODE varchar(255),
                             BATCH_ID VARCHAR(255),
                             fee_list JSONB,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX   group_param_pid_idx ON group_param (PID);
CREATE INDEX  group_param_batch_id_idx ON group_param (BATCH_ID);

drop table if exists cost_mapping_aggregation;
CREATE TABLE cost_mapping_aggregation (
                                          id BIGSERIAL  PRIMARY KEY,
                                          HOS_CODE VARCHAR(255),
                                          HOS_NAME VARCHAR(255),
                                          HOS_PROJECT_CODE VARCHAR(255),
                                          HOS_PROJECT_NAME VARCHAR(255),
                                          CHARGE_TYPE_CODE_HS VARCHAR(255),
                                          CHARGE_TYPE_NAME_HS VARCHAR(255),
                                          INS_CITY_PROJECT_CODE VARCHAR(255),
                                          INS_CITY_PROJECT_NAME VARCHAR(255),
                                          INS_PRO_PROJECT_CODE VARCHAR(255),
                                          INS_PRO_PROJECT_NAME VARCHAR(255),
                                          MIJA_PROJECT_CODE VARCHAR(255),
                                          MIJA_PROJECT_NAME VARCHAR(255),
                                          MIJA_MEDICINE_GENERIC_CODE VARCHAR(255),
                                          MIJA_MEDICINE_GENERIC_NAME VARCHAR(255),
                                          MIJA_MEDICINE_DOSAGE_CODE VARCHAR(255),
                                          MIJA_MEDICINE_DOSAGE_NAME VARCHAR(255),
                                          MIJA_MEDICINE_SPEC_CODE VARCHAR(255),
                                          MIJA_MEDICINE_SPEC_NAME VARCHAR(255),
                                          MIJA_MEDICINE_PACKAGE_CODE VARCHAR(255),
                                          MIJA_MEDICINE_PACKAGE_NAME VARCHAR(255),
                                          MIJA_MEDICINE_COMPANY_CODE VARCHAR(255),
                                          MIJA_MEDICINE_COMPANY_NAME VARCHAR(255),
                                          INS_NATION_PROJECT_CODE VARCHAR(255),
                                          INS_NATION_PROJECT_NAME VARCHAR(255),
                                          MAPPING_MODE VARCHAR(255),
                                          MAPPING_PROB double precision DEFAULT 0,
                                          MAPPING_DATE VARCHAR(255),
                                          MIJA_DATABASE_VERSION VARCHAR(255),
                                          FEE_DICTIONARY_VERSION VARCHAR(255),
                                          DATA_SOURCE VARCHAR(255),
                                          INSURANCE_CODE VARCHAR(255),
                                          ORG_CODE VARCHAR(255),
                                          ORG_NAME VARCHAR(255),
                                          BIG_CLASS_NAME VARCHAR(255),
                                          BIG_CLASS_MAP_PROB double precision DEFAULT 0,
                                          BIG_CLASS_MAP_MODE VARCHAR(255),
                                          BIG_CLASS_PREPROCESS_NAME VARCHAR(255),
                                          SINGLE_MODULE_PREPROCESS_NAME VARCHAR(255),
                                          ES_KEYWORD VARCHAR(255),
                                          FREQUENCY double precision DEFAULT 0,
                                          FEE_STANDARDIZE_VERSION VARCHAR(255),
                                          INSURANCE_NAME VARCHAR(255),
                                          BATCH_ID VARCHAR(255),
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX hos_code_idx ON cost_mapping_aggregation (HOS_CODE);
CREATE INDEX  cost_mapping_aggregation_batch_id_idx ON cost_mapping_aggregation (BATCH_ID);

-- 创建表结构，表名请根据实际需求替换
drop table if exists cost_detail;
CREATE TABLE cost_detail (
                             id BIGSERIAL  PRIMARY KEY,
                             PID varchar(255) NOT NULL,
                             mapping_mark VARCHAR(255),
                             feeCode VARCHAR(255),
                             feeName VARCHAR(255),
                             itemClassCode VARCHAR(255),
                             CHARGE_TYPE_NAME_HS VARCHAR(255),
                             medicalInsuranceCode VARCHAR(255),
                             INSURANCE_NAME VARCHAR(255),
                             INS_NATION_PROJECT_CODE VARCHAR(255),
                             INS_NATION_PROJECT_NAME VARCHAR(255),
                             MIJA_PROJECT_CODE VARCHAR(255),
                             MIJA_PROJECT_NAME VARCHAR(255),
                             SOURCE_FLAG SERIAL,
                             orgCode VARCHAR(255),
                             number DOUBLE PRECISION,
                             fee DOUBLE PRECISION,
                             BATCH_ID VARCHAR(255),
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX cost_detail_pid_idx ON cost_detail (PID);
CREATE INDEX cost_detail_batch_id_idx ON cost_detail (BATCH_ID);


