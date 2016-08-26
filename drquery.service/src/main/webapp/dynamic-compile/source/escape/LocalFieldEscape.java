package com.asiainfo.billing.drquery.process.dynamic;

import java.lang.System;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.asiainfo.billing.drquery.process.operation.fieldEscape.model.FieldEscapeModel;
import com.asiainfo.billing.drquery.process.core.request.DRProcessRequest;
import com.asiainfo.billing.drquery.cache.CacheProvider;


public class LocalFieldEscape {

    public String DEST_GPRS_A1(Map<String, String> model, String fieldName, FieldEscapeModel field, DRProcessRequest request) {
        if (fieldName.equals("startTime"))
            return model.get("START_TIME");
        else if (fieldName.equals("endTime"))
            return model.get("END_TIME");
        else if (fieldName.equals("mainDomain"))
            return model.get("MAIN_DOMAIN");
        else if (fieldName.equals("apnId"))
            return model.get("APN");
        else if (fieldName.equals("downFlow"))
            return model.get("DOWN_FLOW");
        else if (fieldName.equals("upFlow"))
            return model.get("UP_FLOW");
        else if (fieldName.equals("flow"))
            return model.get("FLOW");
//	        else if(fieldName.equals("termBrandName"))
//	            return model.get("BRAND_ID") + "|" + model.get("BRAND_MODEL_ID");
        else if (fieldName.equals("serverIp"))
            return model.get("SERVICE_HOST_IP");
        else if (fieldName.equals("serviceHost"))
            return model.get("SERVICE_HOST");
        else if (fieldName.equals("url"))
            return model.get("URL");
        else if (fieldName.equals("rat"))
            return model.get("RAT");
        else if (fieldName.equals("regionName"))
            return model.get("REGION_NAME");
        else if (fieldName.equals("appId"))
            return model.get("SITE_ID");
        else if (fieldName.equals("HourId"))
            return model.get("HOURID");
        else
            return "";
    }

    public String DEST_GPRS_K1(Map<String, String> model, String fieldName, FieldEscapeModel field, DRProcessRequest request) {
        String DEST_COLUMN = "";
        String[] DEST_COLUMN_ARR = null;
        if (fieldName.equals("appSite")) {
            DEST_COLUMN_ARR = CacheProvider.getEscapedValue("DIM_APP_SITE", model.get("SITE_ID"), new String[]{"SITE_NAME"});
        } else if (fieldName.equals("appName")) {
            DEST_COLUMN_ARR = CacheProvider.getEscapedValue("DIM_APP", model.get("APP_ID"), new String[]{"APP_NAME"});
        } else if (fieldName.equals("appType")) {
            DEST_COLUMN_ARR = CacheProvider.getEscapedValue("DIM_APP_TYPE", model.get("APP_TYPE_ID"), new String[]{"APP_TYPE_NAME"});
        } else if (fieldName.equals("siteId")) {
            DEST_COLUMN_ARR = CacheProvider.getEscapedValue("DIM_WEB_SITE", model.get("SITE_ID"), new String[]{"SITE_NAME"});
        } else if (fieldName.equals("browserType")) {
            DEST_COLUMN_ARR = CacheProvider.getEscapedValue("DIM_BROWSER", model.get("USER_AGENT"), new String[]{"BROWSER_TYPE"});
        } else if (fieldName.equals("termBrandName")) {
            DEST_COLUMN_ARR = CacheProvider.getEscapedValue("DIM_TERM_MODEL", model.get("IMEI"), new String[]{"NAME"});
        } else if (fieldName.equals("imeiSeg")) {
            DEST_COLUMN_ARR = CacheProvider.getEscapedValue("DIM_TERM_INFO_TAC", model.get("IMEI_SEG"), new String[]{"IMEI_SEG_NAME"});
        } else if (fieldName.equals("rat")) {
            //DEST_COLUMN =(String) CacheProvider.getCachingRedisMapData("RAT", model.get("RAT"));
            String value = model.get("RAT");
            DEST_COLUMN = "其它";
            if (value.equals("0")) {
                DEST_COLUMN = "未携带";
            } else if (value.equals("1")) {
                DEST_COLUMN = "UTRAN";
            } else if (value.equals("2")) {
                DEST_COLUMN = "GERAN";
            } else if (value.equals("3")) {
                DEST_COLUMN = "WLAN";
            } else if (value.equals("4")) {
                DEST_COLUMN = "GAN";
            } else if (value.equals("5")) {
                DEST_COLUMN = "HSPA Evolution";
            } else if (value.equals("6")) {
                DEST_COLUMN = "EUTRAN";
            }
        } else if (fieldName.equals("appProtocol")) {
            String value = model.get("appProtocolId".toUpperCase());
            if (null == value || "".equals(value) || "null".equals(value)) {
                DEST_COLUMN = model.get(fieldName);
            } else {
                if (value.equals("0")) {
                    DEST_COLUMN = "APP";
                } else if (value.equals("-9")) {
                    DEST_COLUMN = "其他";
                } else {
                    DEST_COLUMN = "网站";
                }
            }
        } else if (fieldName.equals("accessMode")) {
            String value = model.get("accessModeId".toUpperCase());
            if (null == value || "".equals(value) || "null".equals(value)) {
                DEST_COLUMN = model.get(fieldName);
            } else {
                if (value.equals("1")) {
                    DEST_COLUMN = "3G";
                } else if (value.equals("2")) {
                    DEST_COLUMN = "2G";
                } else if (value.equals("6")) {
                    DEST_COLUMN = "4G";
                } else {
                    DEST_COLUMN = "未知";
                }
            }
        }

        if (DEST_COLUMN_ARR != null && DEST_COLUMN_ARR.length > 0) {
            DEST_COLUMN = DEST_COLUMN_ARR[0];
        }
        if (StringUtils.isEmpty(DEST_COLUMN) || DEST_COLUMN.equals("0") || DEST_COLUMN.equals("未识别")) {
            DEST_COLUMN = "其它";
        }
        return DEST_COLUMN;
    }

    //直接将字段转换成大写取值
    public String DEST_GPRS_TO_UPERCASE(Map<String, String> model, String fieldName, FieldEscapeModel field, DRProcessRequest request) {
//    	System.out.println("fieldName=" +fieldName + ";upercase=" + fieldName.toUpperCase() + ";value=" + model.get(fieldName.toUpperCase()));
        String value = model.get(fieldName.toUpperCase());
        if (null == value || "".equals(value) || "null".equals(value)) {
            return model.get(fieldName);
        }
        return value;
    }

    /**
     * 将appname转义,不认识的appname为""空字符串
     *
     * @param model
     * @param fieldName
     * @param field
     * @param request
     * @return
     */
    public String DEST_GPRS_A15(Map<String, String> model, String fieldName, FieldEscapeModel field, DRProcessRequest request) {
        String appName = "";
        String[] values = null;
        if (fieldName.equals("groupValueName")) {
            if (model.get("groupValue".toUpperCase()) != null && !model.get("groupValue".toUpperCase()).equals("")
                    && !model.get("groupValue".toUpperCase()).equals("null")) {
                String key = model.get("groupValue".toUpperCase());
                values = CacheProvider.getEscapedValue("DIM_APP_SITE", key, new String[]{"APP_SITE_NAME"});
            } else {
                return DEST_GPRS_TO_UPERCASE(model, fieldName, field, request);
            }
        }

        if (values != null && values.length > 0 && !values.equals("null")) {
            if (values[0] != null && !StringUtils.isEmpty(values[0])) {
                appName = values[0].trim();
            }
        }
        return appName;
    }

    public String DEST_GPRS_A11(Map<String, String> model, String fieldName, FieldEscapeModel field, DRProcessRequest request) {
        String appName = "";
        String[] values = null;
        if (fieldName.equals("groupValueName")) {
            String groupColumn = request.getParam("groupColumnCode");
            if (groupColumn.equals("appId")) {
                if (model.get("groupValue".toUpperCase()) != null && !model.get("groupValue".toUpperCase()).equals("")
                        && !model.get("groupValue".toUpperCase()).equals("null")) {
                    String key = model.get("APP_TYPE_ID") + "_" + model.get("groupValue".toUpperCase());
                    values = CacheProvider.getEscapedValue("DIM_APP", key, new String[]{"APP_NAME"});
                } else {
                    return model.get(fieldName);
                }

            } else if (groupColumn.equals("mainDomain")) {
                if (model.get("groupValue".toUpperCase()) != null && !model.get("groupValue".toUpperCase()).equals("")
                        && !model.get("groupValue".toUpperCase()).equals("null")) {
                    String key = model.get("groupValue".toUpperCase());
                    values = CacheProvider.getEscapedValue("DIM_WEB_SITE", key, new String[]{"SITE_NAME"});
                } else {
                    return DEST_GPRS_TO_UPERCASE(model, fieldName, field, request);
                }
            } else if (groupColumn.equals("")) {
                if (model.get("groupValue".toUpperCase()) != null && !model.get("groupValue".toUpperCase()).equals("")
                        && !model.get("groupValue".toUpperCase()).equals("null")) {
                    String key = model.get("groupValue".toUpperCase());
                    values = CacheProvider.getEscapedValue("DIM_APP_SITE", key, new String[]{"APP_SITE_NAME"});
                }
            }
        } else {
            return model.get(fieldName);
        }

        if (values != null && values.length > 0 && !values.equals("null")) {
            if (values[0] != null && !StringUtils.isEmpty(values[0])) {
                appName = values[0].trim();
            }
        }
        return appName;
    }


    public String DEST_GPRS_A16(Map<String, String> model, String fieldName, FieldEscapeModel field, DRProcessRequest request) {
        String accessModeId = model.get("ACCESS_MODE_ID");
        String accessModel = "";
        if("1".equals(accessModeId)) {
            accessModel = "3G";
        } else if("2".equals(accessModeId)) {
            accessModel = "2G";
        } else if("6".equals(accessModeId)) {
            accessModel = "4G";
        } else {
            accessModel = "未知";
        }
        return accessModel;
    }


}