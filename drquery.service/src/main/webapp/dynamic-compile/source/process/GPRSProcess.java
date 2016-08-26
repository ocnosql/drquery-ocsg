package com.asiainfo.billing.drquery.process.dynamic;

import com.ailk.oci.ocnosql.common.rowkeygenerator.MD5RowKeyGenerator;
import com.asiainfo.billing.drquery.cache.CacheProvider;
import com.asiainfo.billing.drquery.cache.ICache;
import com.asiainfo.billing.drquery.exception.BusinessException;
import com.asiainfo.billing.drquery.exception.DrqueryRuntimeException;
import com.asiainfo.billing.drquery.datasource.query.DefaultQueryParams;
import com.asiainfo.billing.drquery.model.Field;
import com.asiainfo.billing.drquery.model.MetaModel;
import com.asiainfo.billing.drquery.process.Executor;
import com.asiainfo.billing.drquery.process.ProcessException;
import com.asiainfo.billing.drquery.process.core.DRCommonProcess;
import com.asiainfo.billing.drquery.process.core.request.CommonDRProcessRequest;
import com.asiainfo.billing.drquery.process.dto.BaseDTO;
import com.asiainfo.billing.drquery.process.dto.EmptyDTO;
import com.asiainfo.billing.drquery.process.dto.PageDTO;
import com.asiainfo.billing.drquery.process.dto.ResultDTO;
import com.asiainfo.billing.drquery.utils.DateUtil;
import com.asiainfo.billing.drquery.utils.NumberUtils;
import com.asiainfo.billing.drquery.utils.PropertiesUtil;
import com.asiainfo.billing.drquery.utils.ServiceLocator;
import com.sun.org.apache.commons.logging.Log;
import com.sun.org.apache.commons.logging.LogFactory;
import org.apache.commons.lang.StringUtils;
import com.asiainfo.billing.drquery.process.operation.fieldEscape.CommonFieldEscapeOperation;

import javax.xml.bind.SchemaOutputResolver;
import java.lang.Double;
import java.lang.Integer;
import java.lang.Override;
import java.lang.System;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPRSProcess extends DRCommonProcess {

    public static final int timeout = Integer.parseInt(PropertiesUtil.getProperty("drquery.service/runtime.properties",
            "redis.expiretime", "300"));
    ICache redisCache = ServiceLocator.getInstance().getService("redisCache", ICache.class);
    private int threhold = Integer.parseInt(PropertiesUtil.getProperty("drquery.service/runtime.properties", "app.flow.share.threhold", (5 * 1024) + ""));

    public static final Log log = LogFactory.getLog(Process.class);


    @Override
    public void before(CommonDRProcessRequest request, MetaModel viewMeta, Map extendParams)
            throws ProcessException, BusinessException {
        extendParams.put("SYSTIME", com.asiainfo.billing.drquery.utils.DateUtil.getCurrentTimestamp("yyyy-MM-dd HH:mm:ss"));
        extendParams.put("PHONE_NO", request.get("phoneNo"));
        extendParams.put("SYSTEM_ID", request.get("SystemID"));
        extendParams.put("STAFF_ID", request.get("StaffID"));
        extendParams.put("START_TIME", request.get("startDate"));
        extendParams.put("END_DATE", request.get("endDate"));
    }


//	    @Override
//	    public void after(CommonDRProcessRequest request, MetaModel viewMeta, Map extendParams)
//	            throws ProcessException, BusinessException {
//	        extendParams.put("query done", com.asiainfo.billing.drquery.utils.DateUtil.getCurrentTimestamp("yyyyMMdd HH:mm:ss"));
//	    }


    public BaseDTO update(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams)
            throws ProcessException, BusinessException {
//	        System.out.println("do update operation");
        return new EmptyDTO();
    }

    public BaseDTO processF1(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams)
            throws ProcessException, BusinessException {
        String rowkey = (String) new MD5RowKeyGenerator().generate(request.getParam("phoneNo"));
        String sql = "select PHONE_NO,NAME,EPARCHY_CODE,FLOW_PLAN_ID,MAIN_PLAN_FLAG,FREE_FLOW,USED_FLOW," +
                "REMAIN_FLOW,USER_TERM_BRAND,USER_TERM_MODEL" +
                " from THB_USER_INFO_DAY " +
                " where id='" + rowkey + "'";
        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));
        return new ResultDTO(list);
    }


    public BaseDTO processF2(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        if ("1".equals(request.getParam("isCurrentMonth"))) {
            return processF1(request, viewMeta, extendParams);
        }

        String rowkey = new MD5RowKeyGenerator().generatePrefix(request.getParam("phoneNo")) + request.getParam("phoneNo");
        String sql = "select PHONE_NO,NAME,EPARCHY_CODE,FLOW_PLAN_ID,MAIN_PLAN_FLAG,FREE_FLOW,USED_FLOW,REMAIN_FLOW,USER_TERM_BRAND,USER_TERM_MODEL " +
                " from THB_USER_INFO_" + request.getParam("dataMonth") +
                " where id='" + rowkey + "'";
        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));
        return new ResultDTO(list);
    }


    /**
     * 多表汇总查询demo
     *
     * @param request
     * @param viewMeta
     * @param extendParams
     * @return
     * @throws ProcessException
     * @throws BusinessException
     */
    public BaseDTO processF3(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
//	        String cacheKey = request.generateCacheKey();
//	        //List dataList = redisCache.getValue(cacheKey, new CacheParameters.Range(request.getParam("startIndex"), request.getParam("startIndex") + request.getParam("offset")));
//	        List dataList = redisCache.getValue(cacheKey, new CacheParameters.Range(1, 10));
//	        if(dataList.size() > 0) {
//	            return new PageDTO(dataList.subList(1, dataList.size()), (Integer) dataList.get(0));
//	        }

        String startTime = request.getParam("startDate");
        String endTime = request.getParam("endDate");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 8) {
            throw new IllegalArgumentException("startDate is invalid, required format yyyyMMdd, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 8) {
            throw new IllegalArgumentException("endDate is invalid, required format yyyyMMdd, but found: " + startTime);
        }
        String phoneNo = request.getParam("phoneNo");
        List<String> months = DateUtil.getMonthsBetween(startTime, endTime, "yyyyMMdd");

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select $0, " +
                    " sum(to_number(gprs_flow)) gprs_flow, sum(to_number(lte_flow)) lte_flow, " +
                    "sum(to_number(roam_flow)) roam_flow, sum(to_number(http_flow)) http_flow" +
                    " from THB_USER_FLOW_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' group by phone_No, $1 union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));
        if ("F3".equals(request.getInterfaceType())) {
            sql = sql.replace("$0", "data_date").replace("$1", "data_date");
        } else if ("F4".equals(request.getInterfaceType())) {
            sql = sql.replace("$0", "PROTOCOL_TYPE_ID").replace("$1", "PROTOCOL_TYPE_ID");
        } else if ("F5".equals(request.getInterfaceType())) {
            sql = sql.replace("$0", "BUSI_TYPE_ID").replace("$1", "BUSI_TYPE_ID");
        }
        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));
        long totalFlow = 0;
        for (Map<String, String> record : list) {
            totalFlow += NumberUtils.parseLong(record.get("GPRS_FLOW"));
        }
        for (Map<String, String> record : list) {
            if ("F3".equals(request.getInterfaceType())) {
                if (totalFlow != 0) {
                    record.put("FLOW_CNT", (NumberUtils.parseDouble(record.get("GPRS_FLOW")) / totalFlow) + "");
                } else {
                    record.put("FLOW_CNT", "0");
                }
            } else if ("F4".equals(request.getInterfaceType())) {
                if (totalFlow != 0) {
                    record.put("PROTOCOL_TYPE_CNT", (NumberUtils.parseDouble(record.get("GPRS_FLOW")) / totalFlow) + "");
                } else {
                    record.put("PROTOCOL_TYPE_CNT", "0");
                }
            } else if ("F5".equals(request.getInterfaceType())) {
                if (totalFlow != 0) {
                    record.put("BUSI_TYPE_CNT", (NumberUtils.parseDouble(record.get("GPRS_FLOW")) / totalFlow) + "");
                } else {
                    record.put("BUSI_TYPE_CNT", "0");
                }
            }
        }
        PageDTO dto = new PageDTO(list, list.size());
        return dto;
    }


    /**
     * 多表跨月查询demo
     *
     * @param request
     * @param viewMeta
     * @param extendParams
     * @return
     * @throws ProcessException
     * @throws BusinessException
     */
    public BaseDTO processF6(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {

        String phoneNo = request.getParam("phoneNo");
        int startIndex = Integer.valueOf(request.getParam("startIndex"));
        int limit = -1;
        String offset = request.getParam("offset");
        if (StringUtils.isNotEmpty(offset)) {
            limit = Integer.parseInt(offset);
        }

        String cacheKey = request.generateCacheKey();
        int countCache = -1;
        String pageCache = null;
        List counterAndRowKey = CacheProvider.getCountAndRowkeyInfo(cacheKey, startIndex);
        if (counterAndRowKey.get(0) != null) {  //不是第一次查询
            countCache = (Integer) counterAndRowKey.get(0);
            pageCache = (String) counterAndRowKey.get(1);
        }

        String[] sqls = buildSQL(countCache, pageCache, limit, request);

        List<Future<List<Map<String, String>>>> futures = new ArrayList<Future<List<Map<String, String>>>>();
        for (int i = 0; i < sqls.length; i++) {
            final String querySql = sqls[i];
            Callable<List<Map<String, String>>> callable = new Callable<List<Map<String, String>>>() {
                @Override
                public List<Map<String, String>> call() throws Exception {
                    return loadData(DefaultQueryParams.newBuilder().buildSQL(querySql));
                }
            };
            futures.add(Executor.getInstance().getThreadPool().submit(callable));
        }
        int i = 0;
        List<Map<String, String>> countRecords = null;
        List<Map<String, String>> detailRecords = null;
        try {
            for (Future<List<Map<String, String>>> future : futures) {
                if (i == 0 && countCache == -1)
                    countRecords = future.get();
                else
                    detailRecords = future.get();
                i++;
            }
        } catch (Exception e) {
            throw new ProcessException("execute query exception", e);
        }
        int totalCount = 0;
        if (countCache == -1) {
            for (Map<String, String> record : countRecords) {
                totalCount += Integer.parseInt(record.get("C"));
            }
            CacheProvider.put(cacheKey, -9, totalCount, timeout);
        } else {
            totalCount = countCache;
        }
        String nextKey = null;
        if (limit != -1 && detailRecords.size() == limit + 1) {
            nextKey = detailRecords.get(detailRecords.size() - 1).get("ID");
            CacheProvider.put(cacheKey, startIndex + limit, nextKey, timeout);
            detailRecords = detailRecords.subList(0, limit);
        }
        return new PageDTO(detailRecords, totalCount);
    }

    public BaseDTO processF13(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        //Decode phoneNo for BJ
        String phoneNo = request.getParam("phoneNo");
//	    	Map<String,String> params = request.getParams();
//	        PhoneCipherWrapper decoder = new PhoneCipherWrapper();
//	        phoneNo = decoder.encrypt(phoneNo);
//	        params.put("phoneNo", phoneNo);
//	        request.setParams(params);

        int startIndex = Integer.valueOf(request.getParam("startIndex"));
        int limit = -1;
        String offset = request.getParam("offset");
        if (StringUtils.isNotEmpty(offset)) {
            limit = Integer.parseInt(offset);
        }

        String cacheKey = request.generateCacheKey();
        int countCache = -1;
        String pageCache = null;
        List counterAndRowKey = CacheProvider.getCountAndRowkeyInfo(cacheKey, startIndex);
        if (counterAndRowKey.get(0) != null) {  //不是第一次查询
            countCache = (Integer) counterAndRowKey.get(0);
            pageCache = (String) counterAndRowKey.get(1);
        }

        String[] sqls = buildSQL(countCache, pageCache, limit, request);

        List<Future<List<Map<String, String>>>> futures = new ArrayList<Future<List<Map<String, String>>>>();
        for (int i = 0; i < sqls.length; i++) {
            final String querySql = sqls[i];
            Callable<List<Map<String, String>>> callable = new Callable<List<Map<String, String>>>() {
                @Override
                public List<Map<String, String>> call() throws Exception {
                    return loadData(DefaultQueryParams.newBuilder().buildSQL(querySql));
                }
            };
            futures.add(Executor.getInstance().getThreadPool().submit(callable));
        }
        int i = 0;
        List<Map<String, String>> countRecords = null;
        List<Map<String, String>> detailRecords = null;
        try {
            for (Future<List<Map<String, String>>> future : futures) {
                if (i == 0 && countCache == -1)
                    countRecords = future.get();
                else
                    detailRecords = future.get();
                i++;
            }
        } catch (Exception e) {
            throw new ProcessException("execute query exception", e);
        }
        int totalCount = 0;
        if (countCache == -1) {
            for (Map<String, String> record : countRecords) {
                totalCount += Integer.parseInt(record.get("C"));
            }
            CacheProvider.put(cacheKey, -9, totalCount, timeout);
        } else {
            totalCount = countCache;
        }
        String nextKey = null;
        if (limit != -1 && detailRecords.size() == limit + 1) {
            nextKey = detailRecords.get(detailRecords.size() - 1).get("ID");
            CacheProvider.put(cacheKey, startIndex + limit, nextKey, timeout);
            int test = startIndex + limit;
            detailRecords = detailRecords.subList(0, limit);
        }
        Map<String, Integer> extData = new HashMap<String, Integer>();
        extData.put("totalCount", totalCount);
        extData.put("startIndex", startIndex);
        extData.put("offset", limit);
        return new PageDTO(detailRecords, extData, totalCount);
    }

    /**
     * @param countCache
     * @param pageCache
     * @param limit
     * @param request
     * @return
     */
    public String[] buildSQL(int countCache, String pageCache, int limit, CommonDRProcessRequest request) {
        String md5Phone = (String) new MD5RowKeyGenerator().generate(request.getParam("phoneNo"));
        String sql = "", countQuery = "", detailQuery = "", startKey = "", stopKey = "";
        if ("F6".equals(request.getInterfaceType())) {
            String startTime = request.getParam("startDate");
            String endTime = request.getParam("endDate");
            List<String> months = DateUtil.getMonthsBetween(startTime, endTime, "yyyyMMdd");
            if (pageCache == null)
                startKey = md5Phone + startTime;
            else
                startKey = pageCache;
            stopKey = md5Phone + endTime + "g";
            for (String month : months) {
                sql += "select $1 " +
                        " from THB_GPRS_CHARGE_" + month +
                        " where id >= '" + startKey + "' and id < '" + stopKey + "' union all ";
            }
            sql = sql.substring(0, sql.lastIndexOf("union"));
            countQuery = sql.replace("$1", "count(1) as c");
            detailQuery = sql.replace("$1", "ID,DATA_DATE,PHONE_NO,CHARGING_ID,EPARCHY_CODE,START_TIME,DURATION,CHARGE_FLOW,TOTAL_FLOW");
        } else if ("F7".equals(request.getInterfaceType())) {
            if (pageCache == null)
                startKey = md5Phone + request.getParam("dataDate");
            else
                startKey = pageCache;
            stopKey = md5Phone + request.getParam("dataDate") + "g";
            sql += "select $1 " +
                    " from THB_GPRS_FLOW_" + request.getParam("dataDate").substring(0, 6) +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' and data_date='" + request.getParam("dataDate") + "'" +
                    "  and CHARGING_ID='" + request.getParam("chargingId") + "'" +
                    //"  and to_date(start_time, 'yyyyMMddHHmmss') >= to_date('" + request.getParam("startTime") + "', 'yyyyMMddHHmmss') - 0.0000115740741 * 60 * 5 " +
                    "  and to_date(start_time, 'yyyyMMddHHmmss') >= to_date('" + request.getParam("startTime") + "', 'yyyyMMddHHmmss')" +
                    " union all ";
            sql = sql.substring(0, sql.lastIndexOf("union"));
            countQuery = sql.replace("$1", "count(1) as c");
            detailQuery = sql.replace("$1", "ID,DATA_DATE,PHONE_NO,CHARGING_ID,NET_TYPE,ACCESS_MODE,TERM_BRAND,TERM_MODEL,START_TIME,DURATION,TOTAL_FLOW,UP_FLOW,DOWN_FLOW,PROTOCOL_TYPE_ID,BUSI_ID,BUSI_REMARK,hour_id");
        } else if ("F8".equals(request.getInterfaceType())) {
            if (pageCache == null)
                startKey = md5Phone + request.getParam("dataTime");
            else
                startKey = pageCache;
            stopKey = md5Phone + request.getParam("dataTime") + "g";
            sql += "select $1 " +
                    " from THB_GPRS_WAP_" + request.getParam("dataTime").substring(0, 6) +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' and data_time='" + request.getParam("dataTime") + "'" +
                    "  and CHARGING_ID='" + request.getParam("chargingId") + "'" +
                    "  and busi_id='" + request.getParam("busiId") + "'" +
                    //"  and to_date(start_time, 'yyyyMMddHHmmss') >= to_date('" + request.getParam("startTime") + "', 'yyyyMMddHHmmss') - 0.0000115740741 * 60 * 5 " +
                    "  and to_date(start_time, 'yyyyMMddHHmmss') >= to_date('" + request.getParam("startTime") + "', 'yyyyMMddHHmmss') " +
                    " union all ";
            sql = sql.substring(0, sql.lastIndexOf("union"));
            countQuery = sql.replace("$1", "count(1) as c");
            detailQuery = sql.replace("$1", "ID,DATA_TIME,PHONE_NO,CHARGING_ID,BUSI_ID,START_TIME,DURATION,TOTAL_FLOW,UP_FLOW,DOWN_FLOW,BUSI_REMARK");
        } else if ("F13".equals(request.getInterfaceType()) || "F14".equals(request.getInterfaceType())
                || "F13S".equals(request.getInterfaceType())
                || "F15".equals(request.getInterfaceType())
                || "F13SD".equals(request.getInterfaceType())) {
            String appId = request.getParam("appId");
            String mainDomain = request.getParam("mainDomain");
            String startTime = request.getParam("startTime");
            String endTime = request.getParam("endTime");
            String orderColumnCode = request.getParam("orderColumnCode");
            String orderFlag = request.getParam("orderFlag");
//	            String dateFormat = startTime.length() > 8 ? "yyyyMMddHHmmss" : "yyyyMMdd";
            String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
            String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
            List<String> tableSuffixes = dayTable.equals("true") ?
                    DateUtil.getDaysBetween(startTime, endTime, dateFormat) :
                    DateUtil.getMonthsBetween(startTime, endTime, dateFormat);
            if (pageCache == null)
                startKey = md5Phone + startTime;
            else
                startKey = pageCache;
            stopKey = md5Phone + endTime + "g";
            String tablePrefix = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.tablePrefix");
            for (String suffix : tableSuffixes) {
                //TODO rowkey构建方式待定，所以查询条件待定
                sql += "select $1 " +
                        " from $6" + suffix +
                        " where id >= '" + startKey + "' and id < '" + stopKey + "'" +
//	                        " and to_date(START_TIME, '$5') >= to_date('" + startTime + "', '$5') " +
//	                        " and to_date(END_TIME, '$5') < to_date('" + endTime + "', '$5') " +
                        " $2 " + " $3 " + " $4 " + " union all ";
                sql = sql.replace("$5", dateFormat);
//	                ocnosql.query.tablePrefix=GPRS_

                sql = sql.replace("$6", tablePrefix);
                if (appId != null && appId.length() > 0)
                    sql = sql.replace("$2", "and APP_ID = '" + appId + "'");
                else
                    sql = sql.replace("$2", "");
                if (mainDomain != null && mainDomain.length() > 0)
                    sql = sql.replace("$3", "and SITE_ID = '" + mainDomain + "'");
                else
                    sql = sql.replace("$3", "");

            }
            sql = sql.substring(0, sql.lastIndexOf("union"));

            //TODO 表结构中没有appname；apptype为apptypeid；apnId为apn
            if ("F13".equals(request.getInterfaceType())) {
                countQuery = sql.replace("$1", "count(1) as c").replace("$4", "");
                detailQuery = sql.replace("$1", "ID, START_TIME, END_TIME, APP_TYPE_ID, APP_ID, MAIN_DOMAIN, APN,substr(MEI,1,8) AS IMEI,substr(IMEI,1,8) as TERM_BRAND_NAME,USER_AGENT, FLOW");
            } else if ("F14".equals(request.getInterfaceType())) {
                detailQuery = sql.replace("$1", "ID, START_TIME, END_TIME, APP_TYPE_ID, APP_ID, MAIN_DOMAIN, APN, substr(IMEI,1,8) as IMEI，BRAND_ID, BRAND_MODEL_ID, USER_AGENT, FLOW, URL, SERVICE_HOST_IP, RAT, REGION_NAME");
                countQuery = sql.replace("$1", "count(1) as c").replace("$4", "");
            } else if ("F13S".equals(request.getInterfaceType())) {
                String cname = PropertiesUtil.getProperty("drquery.service/runtime.properties", "single.mode.field.name");
                detailQuery = sql.replace("$1", cname);
                countQuery = sql.replace("$1", "count(1) as c").replace("$4", "");
            } else if ("F15".equals(request.getInterfaceType())) {
                String cols = "ID,(case when INSTR(free_res_val,',3000,') > 0 or INSTR( free_res_val,',4000,') > 0 then '3000' else '0'  end ) as RESERVE7 ,"
                        + "'0' as RESERVE6,"
                        + "'' as RESERVE5, "
                        + "'1' as RESERVE1, "
                        + "(case when vplmn1= '240' then  '辽宁省' when vplmn1='891' then  '西藏自治区' when vplmn1='898' then  '海南省' when vplmn1='931' then  '甘肃省'"
                        + " when vplmn1='951' then  '宁夏自治区' when vplmn1='971' then  '青海省' when vplmn1='200' then  '广东省' when vplmn1='250' then  '江苏省' "
                        + "when vplmn1='270' then  '湖北省' when vplmn1='280' then  '四川省' when vplmn1='290' then  '陕西省' when vplmn1='311' then  '河北省' "
                        + "when vplmn1='351' then  '山西省' when vplmn1='371' then  '河南省' when vplmn1='431' then '吉林省' when vplmn1='451' then  '黑龙江省' "
                        + "when vplmn1='471' then  '内蒙古自治区' when vplmn1='531' then  '山东省' when vplmn1='551' then  '安徽省' when vplmn1='991' then  '新疆自治区' "
                        + "when vplmn1='571' then  '浙江省' when vplmn1='591' then  '福建省' when vplmn1='731' then  '湖南省' when vplmn1='771' then  '广西自治区' "
                        + "when vplmn1='791' then  '江西省' when vplmn1='851' then  '贵州省' when vplmn1='871' then  '云南省' else vplmn1 end ) as HPLMN,"
                        + "vplmn2 as RESERVE4,"
                        + " start_time as STARTTIME,"
                        + "apn_ni as DRTYPE,DURATION as DURATION, "
                        + "(to_number(rating_res) * 1024) as DATAFLOWTOTAL,"
                        + "(case when free_res_val = null then  '' else ARRAY_ELEM(regexp_split(free_res_val,','),2)||'<' end)  as VALIDRATEPRODID,"
                        + "(to_number(CHARGE1_disc)+to_number(CHARGE2_disc )+to_number(CHARGE3_disc) + to_number(CHARGE4_disc)) as CHARGEDISC,"
                        + " (to_number(CHARGE1)+to_number(CHARGE2) + to_number(CHARGE3) + to_number(CHARGE4)) as CHARGE,"
                        + " (case when mns_type = '0' then  '2G' when mns_type = '1' then  '3G' else  '4G' end ) as RESERVE2,"
                        + "(case when COALESCE(PARTIAL_NUM, '0') = '0' then  '' else (USER_NUMBER||CHARGING_ID || GGSN_ADDRESS || mns_type || to_char(to_date(start_time),'yyyymmdd')) end ) as RESERVE3,"
                        + "'false' as MERGER,  '' as CHILDLIST ";
                String codition = "and (upper(apn_ni) not in ( 'CMDM',  'CMLAP',  'CMMM',  'CMPAY') "
                        + "and service_code <>  '1020000001' "
                        + "and ((upper(apn_ni) =  'CMWAP' "
                        + "and service_code not in ( '2000000000',  '1010000001',  '1020000001',  '1030000002',  '1020000002',  '4000000004',  '1010000002',  '4000000005',  '4000000002',  '1030000009', "
                        + "'1040000007',  '1040000008',  '1030000013',  '2000000009',  '1040000011',  '1020000003',  '1030000004',  '1030000014',  '1030000016',  '1040000002',  '1030000018',  '1030000020',"
                        + "'1040000015',  '1040000016')) or upper(apn_ni) <>  'CMWAP')) "
                        + "and rating_res !=  '0'";
                countQuery = sql.replace("$1", "count(1) as c").replace("$4", codition);
                detailQuery = sql.replace("$1", cols).replace("$4", codition);
            } else if ("F13SD".equals(request.getInterfaceType())) {
                String cols = " /*seek_to_column*/ id," +
                        "imei_seg," +
                        "start_time," +
                        "app_id," +
                        "app_type_id," +
                        "service_host," +
                        "site_id," +
                        "to_number(up_flow)/1024 as up_flow," +
                        "to_number(down_flow)/1024 as down_flow," +
                        "to_number(flow)/1024 as flow ";
                countQuery = sql.replace("$1", "count(1) as c").replace("$4", "");
                detailQuery = sql.replace("$1", cols);
            }

            if (orderColumnCode != null && orderFlag != null)
                detailQuery = detailQuery.replace("$4", "ORDER BY " + camelToUnderline(orderColumnCode) + " " + orderFlag);
            else
                detailQuery = detailQuery.replace("$4", "");
        } else if ("F16SD".equals(request.getInterfaceType())) {
            String accessModeId = request.getParam("accessModeId");
            String appProtocolId = request.getParam("appProtocolId");
            String groupValue = request.getParam("groupValue");
            String startTime = request.getParam("startTime");
            String endTime = request.getParam("endTime");
            String orderColumnCode = request.getParam("orderColumnCode");
            String orderFlag = request.getParam("orderFlag");

            if (pageCache == null) {
                startKey = md5Phone + startTime;
                stopKey = md5Phone + endTime + "g";
            } else {
                if (orderColumnCode != null && orderFlag != null && orderColumnCode.equals("startTime") && orderFlag.toUpperCase().equals("DESC")) {
                    startKey = md5Phone + startTime;
                    stopKey = pageCache;
                } else {
                    startKey = pageCache;
                    stopKey = md5Phone + endTime + "g";
                }
            }
            String tablePrefix = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.tablePrefix");
            String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
            String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
            List<String> tableSuffixes = dayTable.equals("true") ?
                    DateUtil.getDaysBetween(startTime, endTime, dateFormat) :
                    DateUtil.getMonthsBetween(startTime, endTime, dateFormat);

            for (String suffix : tableSuffixes) {
                //TODO rowkey构建方式待定，所以查询条件待定
                sql += "select $1 " +
                        " from $6" + suffix +
                        " where id >= '" + startKey + "' and id <= '" + stopKey + "'" +
                        " $2 " + " $3 " + " $4 " + " union all ";

                sql = sql.replace("$6", tablePrefix);
                if (accessModeId != null && accessModeId.length() > 0) {
                    sql = sql.replace("$2", "and access_Mode_Id = '" + accessModeId + "'");
                } else {
                    sql = sql.replace("$2", "");
                }

                if (appProtocolId != null && appProtocolId.length() > 0) {
                    if ("0".equals(appProtocolId.trim())) {
                        sql = sql.replace("$3", "and app_Protocol_Id = '0'");
                        sql = sql.replace("$4", "and app_id = '" + groupValue + "'");
                    } else if (Integer.valueOf(appProtocolId.trim()).intValue() > 0) {
                        sql = sql.replace("$3", "and app_Protocol_Id > '0'");
                        sql = sql.replace("$4", " and site_id = '" + groupValue + "'");
                    } else {
                        sql = sql.replace("$3", "and app_Protocol_Id = '-9'");
                        sql = sql.replace("$4", " and app_id='-9_-9' and site_id='-9'");
                    }
                } else {
                    sql = sql.replace("$3", "");
                    sql = sql.replace("$4", "");
                }

            }
            sql = sql.substring(0, sql.lastIndexOf("union"));
            countQuery = sql.replace("$1", "count(1) as c").replace("$5", "").replace("$4", "");
            detailQuery = sql.replace("$1", "/*seek_to_column*/ id,imei_seg,start_time,app_id,app_type_id,service_host,site_id,(case when app_id <> '-9_-9' then '0'  when app_id='-9_-9' and site_id<>'-9' then '1' else '-9' end) as appProtocolId,access_mode_id as accessModeId,to_number(up_flow)/1024 as up_flow,to_number(down_flow)/1024 as down_flow,to_number(flow)/1024 as flow");
            if (orderColumnCode != null && orderFlag != null)
                detailQuery = detailQuery + "ORDER BY " + camelToUnderline(orderColumnCode) + " " + orderFlag;
        }

//end of F13/14/F13SD/F16SD

        if (limit > 0) {
            detailQuery += " limit " + (limit + 1);
        }
        String[] sqls;
        if (countCache == -1) {
            sqls = new String[]{countQuery, detailQuery};
        } else {
            sqls = new String[]{detailQuery};
        }
        return sqls;
    }

    /**
     * 多表汇总查询top数据,内部接口
     *
     * @param request
     * @param viewMeta
     * @param extendParams
     * @return
     * @throws ProcessException
     * @throws BusinessException
     */
    public BaseDTO processF11(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        //Decode phoneNo for BJ
        String phoneNo = request.getParam("phoneNo");
//	    	Map<String,String> params = request.getParams();
//	        PhoneCipherWrapper decoder = new PhoneCipherWrapper();
//	        phoneNo = decoder.encrypt(phoneNo);
//	        params.put("phoneNo", phoneNo);
//	        request.setParams(params);

        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 14) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 14) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }

        //标识希望返回前多少条记录。
        String topNum = request.getParam("topNum");
        //当前需要分类的字段。按照应用名称分类，则其值为appName;按照一级域名分类，则其值为mainDomain
        String groupColumnCode = request.getParam("groupColumnCode");
        if ("appId".equals(groupColumnCode)) {
            groupColumnCode = "APP_TYPE_ID,APP_ID"; //按appName分组还是按mainDomain分组
        } else if ("mainDomain".equals(groupColumnCode)) {
            groupColumnCode = "main_domain"; //按main_domain分组
        }
        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
        String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, dateFormat);

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select " + groupColumnCode +
                    " , sum(to_number(flow)) flow, sum(1)  record_count " +
                    " from GPRS_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' " +
                    " group by " + groupColumnCode + " union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));
        sql = "select " + groupColumnCode + " groupValue , '' groupValueName, sum(flow) groupTotalFlow, sum(record_count) groupRecordCount " +
                " from ( " + sql + " )" + " as t1 " +
                " group by " + groupColumnCode + " order by groupTotalFlow desc ";

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));
        //转换groupValue成groupValueName,如果没有转义成功则设置为“其他”
        try {
            CommonFieldEscapeOperation fieldEscape1 = new CommonFieldEscapeOperation();
            list = fieldEscape1.execute(list, viewMeta, request);
        } catch (Exception e) {
            throw new DrqueryRuntimeException(e);
        }

        int topCount = 0;//计数器
        long totalFlow = 0;
        long otherFlow2 = 0, othersRecourdCount = 0;
        int iTopNum = Integer.valueOf(topNum);//要返回的汇总的top app个数
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();
        for (Map<String, String> record : list) {
            String record_flow = record.get("groupTotalFlow");
            if (null == record_flow || record_flow.equals("null")
                    || record_flow.equals("")) {
                record.put("groupTotalFlow", "0");
            }
            totalFlow += NumberUtils.parseLong(record_flow);
            if (record.get("groupValueName") != null && !record.get("groupValueName").equals("")
                    && record.get("groupValueName") != "null" && topCount < iTopNum) {
                topCount++;
                returnList.add(record);
            } else {
                otherFlow2 += NumberUtils.parseLong(record.get("groupTotalFlow"));
                othersRecourdCount += NumberUtils.parseLong(record.get("groupRecordCount"));
            }
        }

        if (otherFlow2 > 0) {    //汇总topnum以外的数据
            Map<String, String> others = new HashMap<String, String>();
            others.put("groupValue", "others");
            others.put("groupValueName", "其他");
            others.put("groupRecordCount", String.valueOf(othersRecourdCount));
               /* if("F12".equals(request.getInterfaceType())) {
                    if(totalFlow != 0) {
		        		others.put("GROUP_TOTAL_FLOW_PERCENT", otherFlow2 / totalFlow + "");
		        	}else{
		        		others.put("GROUP_TOTAL_FLOW_PERCENT",  "0");
		        	}
		        }else if("F11".equals(request.getInterfaceType()))*/
            {
                others.put("groupTotalFlow", String.valueOf(otherFlow2));
            }
            returnList.add(others);
        }

	        /*for(Map<String, String> record : returnList) {
	            if("F12".equals(request.getInterfaceType())) {//外部接口
	            	if(totalFlow != 0) {
	                    record.put("GROUP_TOTAL_FLOW_PERCENT", (NumberUtils.parseDouble(record.get("GROUP_TOTAL_FLOW")) / totalFlow) + "");
	                }else {
	                    record.put("GROUP_TOTAL_FLOW_PERCENT", "0");
	                }
	            	record.remove("GROUP_TOTAL_FLOW");
	            }
	        }*/

        PageDTO dto = new PageDTO(returnList, returnList.size());
	        /*if("F11".equals(request.getInterfaceType())) */
        {//内部接口
            Map<String, String> extData = new HashMap<String, String>();
            extData.put("groupCount", String.valueOf(returnList.size()));
            extData.put("totalFlow", String.valueOf(totalFlow));
            dto.setExtData(extData);
        }
        return dto;
    }

    /**
     * 山东汇总查询top数据
     *
     * @param request
     * @param viewMeta
     * @param extendParams
     * @return
     * @throws ProcessException
     * @throws BusinessException
     */
    public BaseDTO processF11SD(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        //aggregate interface for shandong
        String phoneNo = request.getParam("phoneNo");
        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 14) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 14) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }

        //标识希望返回前多少条记录。
        String topNum = request.getParam("topNum");
        //当前需要分类的字段。按照应用名称分类，则其值为appName;按照一级域名分类，则其值为mainDomain
        String groupColumnCode = request.getParam("groupColumnCode");
        if ("appId".equals(groupColumnCode)) {
            groupColumnCode = "APP_TYPE_ID,APP_ID"; //按appName分组还是按mainDomain分组
        } else if ("mainDomain".equals(groupColumnCode)) {
            groupColumnCode = "site_id"; //按main_domain分组
        }
        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
        String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, dateFormat);

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select " + groupColumnCode +
                    " , sum(to_number(flow)/1024) flow, sum(1)  record_count " +
                    " from GPRS_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' " +
                    " group by " + groupColumnCode + " union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));
        sql = "select " + groupColumnCode + " groupValue , '' groupValueName, sum(flow) groupTotalFlow, sum(record_count) groupRecordCount " +
                " from ( " + sql + " )" + " as t1 " +
                " group by " + groupColumnCode + " order by groupTotalFlow desc ";

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));
        //转换groupValue成groupValueName,如果没有转义成功则设置为“其他”
        try {
            CommonFieldEscapeOperation fieldEscape1 = new CommonFieldEscapeOperation();
            list = fieldEscape1.execute(list, viewMeta, request);
        } catch (Exception e) {
            throw new DrqueryRuntimeException(e);
        }

        int topCount = 0;//计数器
        long totalFlow = 0;
        long otherFlow2 = 0, othersRecourdCount = 0;
        int iTopNum = Integer.valueOf(topNum);//要返回的汇总的top app个数
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();
        for (Map<String, String> record : list) {
            if (null == record.get("groupTotalFlow") || record.get("groupTotalFlow").equals("null")
                    || record.get("groupTotalFlow").equals("")) {
                record.put("groupTotalFlow", "0");
            }
            totalFlow += NumberUtils.parseDouble(record.get("groupTotalFlow"));
            if (record.get("groupValueName") != null && !record.get("groupValueName").equals("")
                    && record.get("groupValueName") != "null" && topCount < iTopNum) {
                topCount++;
                returnList.add(record);
            } else {
                otherFlow2 += NumberUtils.parseDouble(record.get("groupTotalFlow"));
                othersRecourdCount += NumberUtils.parseDouble(record.get("groupRecordCount"));
            }
        }

        if (otherFlow2 > 0) {    //汇总topnum以外的数据
            Map<String, String> others = new HashMap<String, String>();
            others.put("groupValue", "others");
            others.put("groupValueName", "其他");
            others.put("groupRecordCount", String.valueOf(othersRecourdCount));
		       /* if("F12".equals(request.getInterfaceType())) {
		        	if(totalFlow != 0) {
		        		others.put("GROUP_TOTAL_FLOW_PERCENT", otherFlow2 / totalFlow + "");
		        	}else{
		        		others.put("GROUP_TOTAL_FLOW_PERCENT",  "0");
		        	}
		        }else if("F11".equals(request.getInterfaceType()))*/
            {
                others.put("groupTotalFlow", String.valueOf(otherFlow2));
            }
            returnList.add(others);
        }

	        /*for(Map<String, String> record : returnList) {
	            if("F12".equals(request.getInterfaceType())) {//外部接口
	            	if(totalFlow != 0) {
	                    record.put("GROUP_TOTAL_FLOW_PERCENT", (NumberUtils.parseDouble(record.get("GROUP_TOTAL_FLOW")) / totalFlow) + "");
	                }else {
	                    record.put("GROUP_TOTAL_FLOW_PERCENT", "0");
	                }
	            	record.remove("GROUP_TOTAL_FLOW");
	            }
	        }*/

        PageDTO dto = new PageDTO(returnList, returnList.size());
	        /*if("F11".equals(request.getInterfaceType())) */
        {//内部接口
            Map<String, String> extData = new HashMap<String, String>();
            extData.put("groupCount", String.valueOf(returnList.size()));
            extData.put("totalFlow", String.valueOf(totalFlow));
            dto.setExtData(extData);
        }
        return dto;
    }

    //外部接口
    public BaseDTO processF12SD(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        //Decode phoneNo for BJ
        String phoneNo = request.getParam("phoneNo");
        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 14) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 14) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }

        //标识希望返回前多少条记录。
        String topNum = request.getParam("topNum");
        //当前需要分类的字段。按照应用名称分类，则其值为appName;按照一级域名分类，则其值为mainDomain
        String groupColumnCode = request.getParam("groupColumnCode");
        String appId = request.getParam("appId");
//		if("appId".equals(groupColumnCode)){
//			groupColumnCode = "APP_TYPE_ID,APP_ID"; //按app_id分组
//		}
        if ("all".equals(groupColumnCode)) {
            groupColumnCode = "groupValue"; //按app_id分组
        }
        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
        String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, dateFormat);

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select " + groupColumnCode +
                    " ,sum(to_number(flow)) flow, sum(1)  record_count " +
                    " from GPRS_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' " +
                    "and APP_PROTOCOL_ID = '0'";
            if (!appId.equals("")) {
                sql += " and app_id = " + appId;
            }
            sql += " group by " + groupColumnCode + " union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));
        sql = "select " + groupColumnCode + " groupValue, '' groupValueName, sum(flow) groupTotalFlow, sum(record_count) groupRecordCount " +
                " from ( " + sql + " )" + " as t1 " +
                " group by " + groupColumnCode + " order by groupTotalFlow desc ";

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));

        //转换groupValue成groupValueName,如果没有转义成功则设置为“其他”
        try {
            CommonFieldEscapeOperation fieldEscape1 = new CommonFieldEscapeOperation();
            list = fieldEscape1.execute(list, viewMeta, request);
        } catch (Exception e) {
            throw new DrqueryRuntimeException(e);
        }

        int topCount = 0;//计数器
        long totalFlow = 0;
        long otherFlow2 = 0, othersRecourdCount = 0;
        int iTopNum = Integer.valueOf(topNum);//要返回的汇总的top app个数
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();
        for (Map<String, String> record : list) {
            String groupTotalFlow = record.get("groupTotalFlow");
            if (null == groupTotalFlow || "null".equals(groupTotalFlow) || "".equals(groupTotalFlow)) {
                record.put("groupTotalFlow", "0");
            }
            totalFlow += NumberUtils.parseLong(record.get("groupTotalFlow"));
            String groupValueName = record.get("groupValueName");
            if (groupValueName != null && !"".equals(groupValueName) && groupValueName != "null" && topCount < iTopNum) {
                topCount++;
                returnList.add(record);
            } else {
                otherFlow2 += NumberUtils.parseLong(record.get("groupTotalFlow"));
                othersRecourdCount += NumberUtils.parseLong(record.get("groupRecordCount"));
            }
        }

	        /*计算百分比*/
        for (Map<String, String> record : returnList) {
            if (totalFlow != 0) {
                String persent = new java.text.DecimalFormat("#.00").format(NumberUtils.parseDouble(record.get("groupTotalFlow")));
                record.put("groupTotalFlowPercent", "0" + persent);
            } else {
                record.put("groupTotalFlowPercent", "0");
            }
//	        	record.remove("groupTotalFlow");
        }

	        /*其他分组信息
	        if( otherFlow2 > 0 ) {    //汇总topnum以外的数据
	            Map<String,String> others = new HashMap<String,String>();
	        	others.put("groupValue", "others");
	        	others.put("groupValueName", "其他");
	        	others.put("groupRecordCount", String.valueOf(othersRecourdCount));
	    		String persent= new java.text.DecimalFormat("#.00").format(otherFlow2/ totalFlow);
	    		others.put("groupTotalFlowPercent", "0" + persent);
		        returnList.add(others);
	        }*/

        PageDTO dto = new PageDTO(returnList, returnList.size());
        return dto;
    }

    /**
     * 山东汇总查询top数据，按site_id和app_id同时汇总
     *
     * @param request
     * @param viewMeta
     * @param extendParams
     * @return
     * @throws ProcessException
     * @throws BusinessException
     */
    public BaseDTO processF15SD(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        //aggregate interface for shandong
        String phoneNo = request.getParam("phoneNo");
        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        String accessModeId = request.getParam("accessModeId");

        if (StringUtils.isEmpty(startTime) || startTime.length() != 14) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 14) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }

        //标识希望返回前多少条记录。
        String topNum = request.getParam("topNum");

        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
        String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, dateFormat);

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select  ACCESS_MODE_ID, app_id  as group_id,sum(to_number(flow)/1024) flow, sum(1)  record_count,"
                    + " '0' as APP_PROTOCOL_ID_NEW "
                    + " from GPRS_" + month
                    + " where id >='" + startKey + "' and id < '" + stopKey + "' and APP_PROTOCOL_ID='0' ";
            if (!accessModeId.equals("") && accessModeId != null && accessModeId != "null") {
                sql += " and ACCESS_MODE_ID = '" + accessModeId + "'";
            }
            sql += " group by ACCESS_MODE_ID , group_id "
                    + " union all "
                    + " select  ACCESS_MODE_ID ,site_ID as group_id ,sum(to_number(flow)/1024) flow, sum(1)  record_count ,"
                    + " '1' as APP_PROTOCOL_ID_NEW "
                    + " from GPRS_" + month
                    + " where id>='" + startKey + "' and id < '" + stopKey + "' and APP_PROTOCOL_ID > '0' ";
            if (!accessModeId.equals("") && accessModeId != null && accessModeId != "null") {
                sql += " and ACCESS_MODE_ID = '" + accessModeId + "'";
            }
            sql += " group by ACCESS_MODE_ID ,group_id "
                    + " union all "
                    + " select  ACCESS_MODE_ID ,(case when app_id <> '-9_-9' then app_id when app_id='-9_-9' and site_id<>'-9' then site_id "
                    + " else '-9' end) as group_id ,sum(to_number(flow)/1024) flow, sum(1)  record_count ,"
                    + "(case when app_id <> '-9_-9' then '0'  when app_id='-9_-9' and site_id<>'-9' then '1' else '-9' end) as APP_PROTOCOL_ID_NEW "
                    + " from GPRS_" + month
                    + " where id>='" + startKey + "' and id<'" + stopKey + "' and APP_PROTOCOL_ID='-9' ";
            if (!accessModeId.equals("") && accessModeId != null && accessModeId != "null") {
                sql += " and ACCESS_MODE_ID = '" + accessModeId + "'";
            }
            sql += " group by ACCESS_MODE_ID ,group_ID,APP_PROTOCOL_ID_NEW" + " union all ";

        }
        sql = sql.substring(0, sql.lastIndexOf("union"));

        sql = "select group_id as groupValue , '' groupValueName, sum(flow) groupTotalFlow, sum(record_count) groupRecordCount "
                + ",ACCESS_MODE_ID as accessModeId, APP_PROTOCOL_ID_NEW as appProtocolId "
                + " from ( " + sql + " )" + " as t1 "
                + " group by accessModeId,group_id,appProtocolId order by groupTotalFlow desc ";

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));
        //转换groupValue成groupValueName,如果没有转义成功则设置为“其他”
//		try{
//			CommonFieldEscapeOperation fieldEscape1 = new CommonFieldEscapeOperation();
//			list = fieldEscape1.execute(list, viewMeta, request);
//		}catch (Exception e) {
//			throw new DrqueryRuntimeException(e);
//		}

        int topCount = 0;//计数器
        Double totalFlow = 0.00;
        Double otherFlow2 = 0.00;
        int othersRecourdCount = 0;
        int iTopNum = Integer.valueOf(topNum);//要返回的汇总的top app个数
//		List<Map<String,String>> returnList = new ArrayList<Map<String, String>>();
        for (Map<String, String> record : list) {
            if (null == record.get("GROUPTOTALFLOW") || record.get("GROUPTOTALFLOW").equals("null")
                    || record.get("GROUPTOTALFLOW").equals("")) {
                record.put("GROUPTOTALFLOW", "0");
            }
            totalFlow += NumberUtils.parseDouble(record.get("GROUPTOTALFLOW"));
        }
//			if(record.get("groupValueName")!=null && !record.get("groupValueName").equals("")
//					&& record.get("groupValueName")!= "null" && topCount < iTopNum){
//				System.out.println("2 groupValueName is ******** " + record.get("groupValueName"));
//				topCount++;
//				returnList.add(record);
//			}else{
//				otherFlow2 += NumberUtils.parseDouble(record.get("GROUPTOTALFLOW"));
//				othersRecourdCount += NumberUtils.parseDouble(record.get("groupRecordCount"));
//			}
//		}
//		if( otherFlow2 > 0 ) {    //汇总topnum以外的数据
//			Map<String,String> others = new HashMap<String,String>();
//			others.put("groupValue", "others");
//			others.put("groupValueName", "其他");
//			others.put("groupRecordCount", String.valueOf(othersRecourdCount));
//		       /* if("F12".equals(request.getInterfaceType())) {
//		        	if(totalFlow != 0) {
//		        		others.put("GROUP_TOTAL_FLOW_PERCENT", otherFlow2 / totalFlow + "");
//		        	}else{
//		        		others.put("GROUP_TOTAL_FLOW_PERCENT",  "0");
//		        	}
//		        }else if("F11".equals(request.getInterfaceType()))*/{
//				others.put("groupTotalFlow", String.valueOf(otherFlow2));
//			}
//			returnList.add(others);
//		}

        for (Map<String, String> record : list) {
            if ("F15SD".equals(request.getInterfaceType())) {//外部接口
                if (totalFlow != 0) {
//                    record.put("GROUP_TOTAL_FLOW_PERCENT", (NumberUtils.parseDouble(record.get("groupTotalFlow")) / totalFlow) + "");
                    String percent = new java.text.DecimalFormat("#.00").format(NumberUtils.parseDouble(record.get("GROUPTOTALFLOW")) / totalFlow);
                    record.put("groupTotalFlowPercent", "0" + percent);
                } else {
                    record.put("groupTotalFlowPercent", "0");
                }
//            	record.remove("GROUP_TOTAL_FLOW");
            }
        }
        PageDTO dto = new PageDTO(list, list.size());
//	        if("F15SD".equals(request.getInterfaceType())) {//内部接口
        Map<String, String> extData = new HashMap<String, String>();
        extData.put("groupCount", String.valueOf(list.size()));
        extData.put("totalFlow", String.valueOf(totalFlow));
        dto.setExtData(extData);
//		}
        return dto;
    }

    //外部接口
    public BaseDTO processF17SD(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        //Decode phoneNo for BJ
        String phoneNo = request.getParam("phoneNo");
        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 14) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 14) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }

        //标识希望返回前多少条记录。
        String topNum = request.getParam("topNum");
        //当前需要分类的字段。按照应用名称分类，则其值为appName;按照一级域名分类，则其值为mainDomain
        String groupColumnCode = request.getParam("groupColumnCode");
        if ("appId".equals(groupColumnCode)) {
            groupColumnCode = "APP_TYPE_ID,APP_ID"; //按app_id分组
        } else if ("mainDomain".equals(groupColumnCode)) {
            groupColumnCode = "site_id"; //按site_id分组
        }
        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
        String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, dateFormat);

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select " + groupColumnCode +
                    " ,sum(to_number(flow)) flow, sum(1)  record_count " +
                    " from GPRS_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' " +
                    " group by " + groupColumnCode + " union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));
        sql = "select " + groupColumnCode + " groupValue, '' groupValueName, sum(flow) groupTotalFlow, sum(record_count) groupRecordCount " +
                " from ( " + sql + " )" + " as t1 " +
                " group by " + groupColumnCode + " order by groupTotalFlow desc ";

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));

        //转换groupValue成groupValueName,如果没有转义成功则设置为“其他”
        try {
            CommonFieldEscapeOperation fieldEscape1 = new CommonFieldEscapeOperation();
            list = fieldEscape1.execute(list, viewMeta, request);
        } catch (Exception e) {
            throw new DrqueryRuntimeException(e);
        }

        int topCount = 0;//计数器
        long totalFlow = 0;
        long otherFlow2 = 0, othersRecourdCount = 0;
        int iTopNum = Integer.valueOf(topNum);//要返回的汇总的top app个数
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();
        for (Map<String, String> record : list) {
            String groupTotalFlow = record.get("groupTotalFlow");
            if (null == groupTotalFlow || "null".equals(groupTotalFlow) || "".equals(groupTotalFlow)) {
                record.put("groupTotalFlow", "0");
            }
            totalFlow += NumberUtils.parseLong(record.get("groupTotalFlow"));
            String groupValueName = record.get("groupValueName");
            if (groupValueName != null && !"".equals(groupValueName) && groupValueName != "null" && topCount < iTopNum) {
                topCount++;
                returnList.add(record);
            } else {
                otherFlow2 += NumberUtils.parseLong(record.get("groupTotalFlow"));
                othersRecourdCount += NumberUtils.parseLong(record.get("groupRecordCount"));
            }
        }

	        /*计算百分比*/
        for (Map<String, String> record : returnList) {
            if (totalFlow != 0) {
                String persent = new java.text.DecimalFormat("#.00").format(NumberUtils.parseDouble(record.get("GROUP_TOTAL_FLOW")) / totalFlow);
                record.put("groupTotalFlowPercent", "0" + persent);
            } else {
                record.put("groupTotalFlowPercent", "0");
            }
//	        	record.remove("groupTotalFlow");
        }

	        /*其他分组信息
	        if( otherFlow2 > 0 ) {    //汇总topnum以外的数据
	            Map<String,String> others = new HashMap<String,String>();
	        	others.put("groupValue", "others");
	        	others.put("groupValueName", "其他");
	        	others.put("groupRecordCount", String.valueOf(othersRecourdCount));
	    		String persent= new java.text.DecimalFormat("#.00").format(otherFlow2/ totalFlow);
	    		others.put("groupTotalFlowPercent", "0" + persent);
		        returnList.add(others);
	        }*/

        PageDTO dto = new PageDTO(returnList, returnList.size());
        return dto;
    }

    /**
     * 单列模式，多表汇总查询top数据,内部接口
     *
     * @param request
     * @param viewMeta
     * @param extendParams
     * @return
     * @throws ProcessException
     * @throws BusinessException
     */
    public BaseDTO processF11S(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams)
            throws ProcessException, BusinessException {
        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 14) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 14) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMddHHMMss, but found: " + startTime);
        }
        String phoneNo = request.getParam("phoneNo");
        //标识希望返回前多少条记录。
        String topNum = request.getParam("topNum");
        //当前需要分类的字段。按照应用名称分类，则其值为appName;按照一级域名分类，则其值为mainDomain
        String groupColumnCode = request.getParam("groupColumnCode");
        if ("appId".equals(groupColumnCode)) {
            groupColumnCode = "APP_TYPE_ID,APP_ID"; //按appName分组还是按mainDomain分组
        } else if ("mainDomain".equals(groupColumnCode)) {
            groupColumnCode = "main_domain"; //按main_domain分组
        }
        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, "yyyyMMddHHmmss");

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        String cname = PropertiesUtil.getProperty("drquery.service/runtime.properties", "single.mode.field.name");
        for (String month : months) {
            sql += "select " + cname +
                    " from GPRS_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' " +
                    " union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));
        //解析单列到多列数据，转义
        List<Map<String, String>> returnRecords = new ArrayList<Map<String, String>>();
        String[] fields = PropertiesUtil.getProperty("drquery.service/runtime.properties", "single.mode.field").split(",");
        String seprator = PropertiesUtil.getProperty("drquery.service/runtime.properties", "single.mode.fields.separator");
        for (Map<String, String> singleMap : list) {
            String[] singleValue = singleMap.get(cname.toUpperCase()).split(seprator);
            Map<String, String> record = new HashMap<String, String>();
            for (int i = 0; i < singleValue.length; i++) {
                record.put(fields[i], singleValue[i]);
            }
            returnRecords.add(record);
        }

        //转换groupValue成groupValueName,如果没有转义成功则设置为“其他”
        try {
            CommonFieldEscapeOperation fieldEscape1 = new CommonFieldEscapeOperation();
            list = fieldEscape1.execute(list, viewMeta, request);
        } catch (Exception e) {
            throw new DrqueryRuntimeException(e);
        }

        int topCount = 0;//计数器
        long totalFlow = 0;
        long otherFlow2 = 0, othersRecourdCount = 0;
        int iTopNum = Integer.valueOf(topNum);//要返回的汇总的top app个数
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();
        for (Map<String, String> record : list) {
            if (null == record.get("groupTotalFlow") || record.get("groupTotalFlow").equals("null")
                    || record.get("groupTotalFlow").equals("")) {
                record.put("groupTotalFlow", "0");
            }
            totalFlow += NumberUtils.parseLong(record.get("groupTotalFlow"));
            if (record.get("groupValueName") != null && !record.get("groupValueName").equals("")
                    && record.get("groupValueName") != "null" && topCount < iTopNum) {
                topCount++;
                returnList.add(record);
            } else {
                otherFlow2 += NumberUtils.parseLong(record.get("groupTotalFlow"));
                othersRecourdCount += NumberUtils.parseLong(record.get("groupRecordCount"));
            }
        }

        if (otherFlow2 > 0) {    //汇总topnum以外的数据
            Map<String, String> others = new HashMap<String, String>();
            others.put("groupValue", "others");
            others.put("groupValueName", "其他");
            others.put("groupRecordCount", String.valueOf(othersRecourdCount));
            others.put("groupTotalFlow", String.valueOf(otherFlow2));
            returnList.add(others);
        }
	        /*for(Map<String, String> record : returnList) {
	            if("F12".equals(request.getInterfaceType())) {//外部接口
	            	if(totalFlow != 0) {
	                    record.put("GROUP_TOTAL_FLOW_PERCENT", (NumberUtils.parseDouble(record.get("GROUP_TOTAL_FLOW")) / totalFlow) + "");
	                }else {
	                    record.put("GROUP_TOTAL_FLOW_PERCENT", "0");
	                }
	            	record.remove("GROUP_TOTAL_FLOW");
	            }
	        }*/

        PageDTO dto = new PageDTO(returnList, returnList.size());
        Map<String, String> extData = new HashMap<String, String>();
        extData.put("groupCount", String.valueOf(returnList.size()));
        extData.put("totalFlow", String.valueOf(totalFlow));
        dto.setExtData(extData);

        return dto;
    }

    /**
     * 单列模式，分页明细查询和转义
     *
     * @param request
     * @param viewMeta
     * @param extendParams
     * @throws ProcessException
     * @throws BusinessException
     */
    public BaseDTO processF13S(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        String phoneNo = request.getParam("phoneNo");
        int startIndex = Integer.valueOf(request.getParam("startIndex"));
        int limit = -1;
        String offset = request.getParam("offset");
        if (StringUtils.isNotEmpty(offset)) {
            limit = Integer.parseInt(offset);
        }

        String cacheKey = request.generateCacheKey();
        int countCache = -1;
        String pageCache = null;
        List counterAndRowKey = CacheProvider.getCountAndRowkeyInfo(cacheKey, startIndex);
        if (counterAndRowKey.get(0) != null) {  //不是第一次查询
            countCache = (Integer) counterAndRowKey.get(0);
            pageCache = (String) counterAndRowKey.get(1);
        }

        String[] sqls = buildSQL(countCache, pageCache, limit, request);

        List<Future<List<Map<String, String>>>> futures = new ArrayList<Future<List<Map<String, String>>>>();
        for (int i = 0; i < sqls.length; i++) {
            final String querySql = sqls[i];
            Callable<List<Map<String, String>>> callable = new Callable<List<Map<String, String>>>() {
                @Override
                public List<Map<String, String>> call() throws Exception {
                    return loadData(DefaultQueryParams.newBuilder().buildSQL(querySql));
                }
            };
            futures.add(Executor.getInstance().getThreadPool().submit(callable));
        }
        int i = 0;
        List<Map<String, String>> countRecords = null;
        List<Map<String, String>> detailRecords = null;
        try {
            for (Future<List<Map<String, String>>> future : futures) {
                if (i == 0 && countCache == -1)
                    countRecords = future.get();
                else
                    detailRecords = future.get();
                i++;
            }
        } catch (Exception e) {
            throw new ProcessException("execute query exception", e);
        }
        int totalCount = 0;
        if (countCache == -1) {
            for (Map<String, String> record : countRecords) {
                totalCount += Integer.parseInt(record.get("C"));
            }
            CacheProvider.put(cacheKey, -9, totalCount, timeout);
        } else {
            totalCount = countCache;
        }
        String nextKey = null;
        if (limit != -1 && detailRecords.size() == limit + 1) {
            nextKey = detailRecords.get(detailRecords.size() - 1).get("ID");
            CacheProvider.put(cacheKey, startIndex + limit, nextKey, timeout);
            detailRecords = detailRecords.subList(0, limit);
        }

        //解析单列到多列数据，转义
        List<Map<String, String>> returnRecords = new ArrayList<Map<String, String>>();
        String[] fields = PropertiesUtil.getProperty("drquery.service/runtime.properties", "single.mode.field").split(",");
        String seprator = PropertiesUtil.getProperty("drquery.service/runtime.properties", "single.mode.fields.separator");
        String cname = PropertiesUtil.getProperty("drquery.service/runtime.properties", "single.mode.field.name");
        for (Map<String, String> singleMap : detailRecords) {
            String[] singleValue = singleMap.get(cname.toUpperCase()).split(seprator);
            Map<String, String> record = new HashMap<String, String>();
            for (int j = 0; j < singleValue.length; j++) {
                record.put(fields[j], singleValue[j]);
            }
            returnRecords.add(record);
        }
        //转换groupValue成groupValueName,如果没有转义成功则设置为“其他”
        try {
            CommonFieldEscapeOperation fieldEscape1 = new CommonFieldEscapeOperation();
            returnRecords = fieldEscape1.execute(returnRecords, viewMeta, request);
        } catch (Exception e) {
            throw new DrqueryRuntimeException(e);
        }
        ///
        Map<String, Integer> extData = new HashMap<String, Integer>();
        extData.put("totalCount", totalCount);
        extData.put("startIndex", startIndex);
        extData.put("offset", limit);
        return new PageDTO(returnRecords, extData, totalCount);
    }

    public BaseDTO processF21(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        String phoneNo = request.getParam("mobile");
        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 8) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMdd, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 8) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMdd, but found: " + startTime);
        }

        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
        String dateFormat = "yyyyMMdd";
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, dateFormat);
        int dayinterval = DateUtil.getDaysBetween(startTime, endTime, dateFormat).size();

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select app_id as site_id, '' appSite , flow " +
                    " from GPRS_SUM_HOUR_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' ";
            sql += " group by site_id union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));

        //app_id(site_id)转换成 appSite ,如果没有转义成功则设置为“其他”
        try {
            CommonFieldEscapeOperation fieldEscape1 = new CommonFieldEscapeOperation();
            list = fieldEscape1.execute(list, viewMeta, request);
        } catch (Exception e) {
            throw new DrqueryRuntimeException(e);
        }

        long totalFlow = 0;
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();

        // 对flow求总和
        for (Map<String, String> record : list) {
            totalFlow += NumberUtils.parseLong(record.get("flow"));
        }

        /*计算百分比*/
        for (Map<String, String> record : list) {
            if (totalFlow != 0) {
                String groupflow = record.get("flow");
                double gflow = Double.valueOf(groupflow);
                double NoScaleFlowRate = gflow / totalFlow;
                String flowRateStr = String.format("%.2f", NoScaleFlowRate);
                record.put("flowRate", flowRateStr);
            } else {
                record.put("flowRate", "0");
            }
            returnList.add(record);
        }

        double avgflowByDay = totalFlow / dayinterval;
        String avgflowStr = String.format("%.2f", avgflowByDay);

        Map<String, String> extData = new HashMap<String, String>();
        extData.put("totalApps", String.valueOf(returnList.size()));
        extData.put("avgFlow", avgflowStr);

        PageDTO dto = new PageDTO(returnList, extData, returnList.size());
        return dto;
    }

    public BaseDTO processF22(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        String phoneNo = request.getParam("mobile");
        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        String appId = request.getParam("appId");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 8) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMdd, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 8) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMdd, but found: " + startTime);
        }

        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
//        String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
        String dateFormat = "yyyyMMdd";
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, dateFormat);

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select substr(START_TIME,0,8) as startTime , flow " +
                    " from GPRS_SUM_HOUR_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' ";
            if (!appId.equals("")) {
                sql += " and app_id = " + appId;
            }
            sql += " union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));
        sql = "select startTime, sum(flow) flow " +
                " from ( " + sql + " )" + " as t1 " +
                " group by startTime  order by startTime asc ";

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));

        long totalFlow = 0;
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();

        // 对flow求总和
        for (Map<String, String> record : list) {
            totalFlow += NumberUtils.parseLong(record.get("flow"));
        }

        /*计算百分比*/
        for (Map<String, String> record : list) {
            if (totalFlow != 0) {
                String groupflow = record.get("flow");
                double gflow = Double.valueOf(groupflow);
                double NoScaleFlowRate = gflow / totalFlow;
                String flowRateStr = String.format("%.2f", NoScaleFlowRate);
                record.put("flowRate", flowRateStr);
            } else {
                record.put("flowRate", "0");
            }
            returnList.add(record);
        }

        PageDTO dto = new PageDTO(returnList, returnList.size());
        return dto;
    }

    public BaseDTO processF23(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        String phoneNo = request.getParam("mobile");
        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        String appId = request.getParam("appId");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 8) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMdd, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 8) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMdd, but found: " + startTime);
        }
        if (!startTime.equals(endTime)) {
            throw new IllegalArgumentException("startTime " + startTime + " is not equal to endTime " + endTime);
        }

        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
//        String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
        String dateFormat = "yyyyMMdd";
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, dateFormat);

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select START_TIME as hourId , flow " +
                    " from GPRS_SUM_HOUR_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' ";
            if (!appId.equals("")) {
                sql += " and app_id = " + appId;
            }
            sql += " group by hourId union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));

        long totalFlow = 0;
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();

        // 对flow求总和
        for (Map<String, String> record : list) {
            totalFlow += NumberUtils.parseLong(record.get("flow"));
        }

        /*计算百分比*/
        for (Map<String, String> record : list) {
            if (totalFlow != 0) {
                String groupflow = record.get("flow");
                double gflow = Double.valueOf(groupflow);
                double NoScaleFlowRate = gflow / totalFlow;
                String flowRateStr = String.format("%.2f", NoScaleFlowRate);
                record.put("flowRate", flowRateStr);
            } else {
                record.put("flowRate", "0");
            }
            returnList.add(record);
        }

        PageDTO dto = new PageDTO(returnList, returnList.size());
        return dto;
    }

    // 小时内按应用ID汇总
    public BaseDTO processF24(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        String phoneNo = request.getParam("mobile");
        String startTime = request.getParam("startTime");
        String endTime = request.getParam("endTime");
        if (StringUtils.isEmpty(startTime) || startTime.length() != 10) {
            throw new IllegalArgumentException("startTime is invalid, required format yyyyMMdd, but found: " + startTime);
        }
        if (StringUtils.isEmpty(endTime) || endTime.length() != 10) {
            throw new IllegalArgumentException("endTime is invalid, required format yyyyMMdd, but found: " + startTime);
        }

        String dayTable = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.isDayTable");
//        String dateFormat = PropertiesUtil.getProperty("drquery.service/runtime.properties", "ocnosql.query.date.format");
        String dateFormat = "yyyyMMddHH";
        List<String> months = DateUtil.getSuffixesBetween(dayTable, startTime, endTime, dateFormat);

        String sql = "";
        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime;
        String stopKey = md5Phone + endTime + "g";
        for (String month : months) {
            sql += "select app_id as site_id, '' appSite , flow " +
                    " from GPRS_SUM_HOUR_" + month +
                    " where id >= '" + startKey + "' and id < '" + stopKey + "' ";
            sql += " group by site_id union all ";
        }
        sql = sql.substring(0, sql.lastIndexOf("union"));

        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));

        //app_id(site_id)转换成 appSite ,如果没有转义成功则设置为“其他”
        try {
            CommonFieldEscapeOperation fieldEscape1 = new CommonFieldEscapeOperation();
            list = fieldEscape1.execute(list, viewMeta, request);
        } catch (Exception e) {
            throw new DrqueryRuntimeException(e);
        }


        long totalFlow = 0;
        List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();

        // 对flow求总和
        for (Map<String, String> record : list) {
            totalFlow += NumberUtils.parseLong(record.get("flow"));
        }

        /*计算百分比*/
        for (Map<String, String> record : list) {
            if (totalFlow != 0) {
                String groupflow = record.get("flow");
                double gflow = Double.valueOf(groupflow);
                double NoScaleFlowRate = gflow / totalFlow;
                String flowRateStr = String.format("%.2f", NoScaleFlowRate);
                record.put("flowRate", flowRateStr);
            } else {
                record.put("flowRate", "0");
            }
            returnList.add(record);
        }

        PageDTO dto = new PageDTO(returnList, returnList.size());
        return dto;
    }


    public BaseDTO processF31(CommonDRProcessRequest request, MetaModel viewMeta, final Map extendParams) throws ProcessException, BusinessException {
        String phoneNo = request.getParam("mobile");
        Integer amplifier = request.getInt("amplifier", 0);
        String startTime = request.getParam("startTime");
        Date start = DateUtil.add(startTime, "yyyyMMddHH", 12, -amplifier);
        String startTime_ = DateUtil.dateToString(start, "yyyyMMddHHmm");


        String endTime = request.getParam("endTime");
        Date end = DateUtil.add(endTime, "yyyyMMddHH", 12, amplifier);
        String endTime_ = DateUtil.dateToString(end, "yyyyMMddHHmm");

        String md5Phone = (String) new MD5RowKeyGenerator().generate(phoneNo);
        String startKey = md5Phone + startTime_;
        String stopKey = md5Phone + endTime_ + "g";

        String access_mode_id = request.getParam("ACCESS_MODE_ID");

        long chargeFlow = Long.parseLong(request.get("chargeFlow"));

        List<String> months = DateUtil.getSuffixesBetween("false", startTime, endTime, "yyyyMMddHH");
        String sql = "";
        for (String month : months) {
            sql = "SELECT START_TIME, APP_ID, SERVICE_HOST, ACCESS_MODE_ID, IMEI_SEG, TO_NUMBER(UP_FLOW)/TO_NUMBER(FLOW) AS UP_FLOW_PERCENT, CEIL(TO_NUMBER(FLOW)/1024) AS FLOW "
                    + " FROM GPRS_" + month
                    + " WHERE ID >='" + startKey + "' AND ID <'" + stopKey + "' AND TO_NUMBER(FLOW) > 0";
            if(StringUtils.isNotEmpty(access_mode_id)) {
                sql += " AND ACCESS_MODE_ID='" + access_mode_id + "'";
            }
            sql += " UNION ALL ";
        }
        sql = sql.substring(0, sql.lastIndexOf("UNION"));
        sql = "SELECT * FROM (" + sql + ") ORDER BY START_TIME";
        List<Map<String, String>> list = loadData(DefaultQueryParams.newBuilder().buildSQL(sql));
        long totalFlow = 0l;
        for(int i = 0; i < list.size(); i++) {
            Map<String, String> record = list.get(i);
            double flow_ = Double.parseDouble(record.get("FLOW"));
            long flow = new Double(flow_).longValue();
            totalFlow += flow;
            record.put("FLOW", flow + "");
        }
        Map<Integer, Double> percentMap = new HashMap<Integer, Double>();
        for(int i = 0; i < list.size(); i++) {
            double percent = Long.parseLong(list.get(i).get("FLOW")) / (double) totalFlow;
            percentMap.put(i, percent);
        }

        long diff_ = chargeFlow - totalFlow;
        if(diff_ != 0) {
            long diff = Math.abs(diff_);
            int sign = diff_ >= 0 ? 1 : -1;

            long shareTotal = 0;
            Long[] values = shareFlow(percentMap, diff, threhold, sign, list);
            long reserve = values[0];
            shareTotal += values[1];

            while (reserve != 0) {
                values = shareFlow(percentMap, reserve, threhold, sign, list);
                reserve = values[0];
                shareTotal += values[1];
            }

            long remainFlow = diff - shareTotal;

            int index = 0;
            while (remainFlow != 0) {
                Map<String, String> record = list.get(index);
                if ((Long.parseLong(record.get("FLOW")) == 0l)) {
                    index++;
                    if (index >= list.size()) {
                        index = 0;
                    }
                    continue;
                }
                record.put("FLOW", (Long.parseLong(record.get("FLOW")) + 1 * sign) + "");
                remainFlow --;
                index++;
                if (index >= list.size()) {
                    index = 0;
                }
            }

            //last step: process up_flow and down_flow
            for(int i = 0; i < list.size(); i++) {
                Map<String, String> record = list.get(i);
                long flow = Long.parseLong(record.get("FLOW"));
                long upFlow = new Double(flow * Double.parseDouble(record.get("UP_FLOW_PERCENT"))).longValue();
                record.put("UP_FLOW", upFlow + "");
                record.put("DOWN_FLOW", (flow - upFlow) + "");
            }
        }

        PageDTO dto = new PageDTO(list, list.size());
        return dto;
    }

    public Long[] shareFlow(Map<Integer, Double> percentMap, long flowForShare, int threhold, int sign, List<Map<String, String>> list) {
        long reserve = 0;
        long shareTotal = 0;
        for(int i = 0; i < list.size(); i++) {
            Map<String, String> record = list.get(i);
            long shareFlow = new Double(flowForShare * percentMap.get(i)).longValue();
            long overFlow = 0;
            if(shareFlow > threhold) {
                overFlow = shareFlow - threhold;
                shareFlow = threhold;
            }

            long flow = Long.parseLong(record.get("FLOW"));
            if(flow + shareFlow * sign > 0) {
                record.put("FLOW", (flow + shareFlow * sign) + "");
                shareTotal += shareFlow;
                reserve += overFlow;
            } else {
                reserve += shareFlow + overFlow;
            }

        }
        return new Long[] {reserve, shareTotal};
    }


    public static final char UNDERLINE = '_';

    public static String camelToUnderline(String param) {
        if (param == null || "".equals(param.trim())) {
            return "";
        }
        int len = param.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = param.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(UNDERLINE);
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String underlineToCamel(String param) {
        if (param == null || "".equals(param.trim())) {
            return "";
        }
        int len = param.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = param.charAt(i);
            if (c == UNDERLINE) {
                if (++i < len) {
                    sb.append(Character.toUpperCase(param.charAt(i)));
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String underlineToCamel2(String param) {
        if (param == null || "".equals(param.trim())) {
            return "";
        }
        StringBuilder sb = new StringBuilder(param);
        Matcher mc = Pattern.compile("_").matcher(param);
        int i = 0;
        while (mc.find()) {
            int position = mc.end() - (i++);
            //String.valueOf(Character.toUpperCase(sb.charAt(position)));
            sb.replace(position - 1, position + 1, sb.substring(position, position + 1).toUpperCase());
        }
        return sb.toString();
    }

    public static void main(String[] args) {
    }

}