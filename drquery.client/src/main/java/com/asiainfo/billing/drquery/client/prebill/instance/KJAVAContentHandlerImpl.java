package com.asiainfo.billing.drquery.client.prebill.instance;

import com.asiainfo.billing.drquery.client.prebill.PreBillDRQueryUtil;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class KJAVAContentHandlerImpl{

    public Map getTailList(JSONObject data) {
        Map result = new HashMap();
        //**处理字段key**
        List listCaption = new ArrayList();
        List fields = data.getJSONArray("fields");
        int fieldsSize = fields.size();
        listCaption.add("序号");
        for (int i = 0; i < fieldsSize; i++) {
            String temp = (String) fields.get(i);
            if ("基本费用".equals(temp)) { 
                listCaption.add("通信费(元)");
            } else if ("信息费".equals(temp)) { 
                listCaption.add("信息费(元)");
            } else {
                listCaption.add(temp);
            }
        }
        result.put("listCaption", listCaption);
        
        
        //**处理数据**++++++**处理表尾数据统计**
        
        long total1 = 0;
        long total2 = 0;
        
        
        List content = data.getJSONArray("contents");
        List listBody = new ArrayList();
        int contentSize = content.size();
        for (int i = 0; i < contentSize; i++) {
            List singleRow = (List) content.get(i);
            List singleContent = new ArrayList();
            singleContent.add(String.valueOf(i+1));
            for (int j = 0; j < fieldsSize; j++) {
                String input = (String) singleRow.get(j);
                if(j == 5 ) {//费用字段
                    if ("".equals(input) || "0".equals(input)) {
                        singleContent.add("0.00");
                    } else {
                        singleContent.add(PreBillDRQueryUtil.formatMoney(input));
                        total1 += PreBillDRQueryUtil.formatNumber(PreBillDRQueryUtil.formatMoneyForSum(input));
                    }
                } else if(j == 6 ) {//费用字段
                    if ("".equals(input) || "0".equals(input)) {
                        singleContent.add("0.00");
                    } else {
                        singleContent.add(PreBillDRQueryUtil.formatMoney(input));
                        total2 += PreBillDRQueryUtil.formatNumber(PreBillDRQueryUtil.formatMoneyForSum(input));
                    }
                } else if(j == 1 || j ==2) {
                    singleContent.add(PreBillDRQueryUtil.formatTime2Date(input));
                } else {
                    singleContent.add(input);
                }
            }
            listBody.add(singleContent);
        }
        result.put("listBody", listBody);
        //**处理表尾数据统计**
        List listTail = new ArrayList();
        listTail.add("");
        for (int i = 0; i < fieldsSize; i++) {
            if (i == 5 ) {
                listTail.add(PreBillDRQueryUtil.formatMoney(String.valueOf(total1)));
            } else if (i == 6 ) {
                listTail.add(PreBillDRQueryUtil.formatMoney(String.valueOf(total2)));
            } else {
                listTail.add("");
            }
        }
        result.put("listTail", listTail);
        //**处理统计数据**
        if (!"0".equals(data.getString("count"))) {
            List listSpecialSum = PreBillDRQueryUtil.formatStats(data.getJSONArray("stats"));
            result.put("listSpecialSum", listSpecialSum);
        } else {
            List listSpecialSum = new JSONArray();
            result.put("listSpecialSum", listSpecialSum);
        }
        
        return result;
    }
}
