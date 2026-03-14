package com.yihecode.camera.ai.notify.sms;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;

import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 描述：短信发送工具类
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class SendSmsUtil {

    private static final String ENCODING = "UTF-8";
    private static final String DEFAULT_SMS_API_URL = "https://sms.yunpian.com/v2/sms/tpl_batch_send.json";


    /**
     * 发送
     * @param mobiles
     * @param cameraName
     * @param algorithmName
     */
    public static void send(String mobiles, String cameraName, String algorithmName) {
        send(mobiles, cameraName, algorithmName, "", "", "");
    }

    public static void send(String mobiles,
                            String cameraName,
                            String algorithmName,
                            String apiKey,
                            String tplId,
                            String apiUrl) {
        if(StrUtil.isBlank(mobiles)) {
            return ;
        }
        if (StrUtil.isBlank(apiKey) || StrUtil.isBlank(tplId)) {
            return;
        }

        //
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("apikey", apiKey);
            params.put("mobile", mobiles);
            params.put("tpl_id", tplId);
            params.put("tpl_value", URLEncoder.encode("#camera#", ENCODING) + "=" + URLEncoder.encode(cameraName, ENCODING) + "&" + URLEncoder.encode("#algorithm#", ENCODING) + "=" + URLEncoder.encode(algorithmName, ENCODING) + "&" + URLEncoder.encode("#time#", ENCODING) + "=" + DateUtil.format(new Date(), "MM/dd HH:mm"));
            String finalApiUrl = StrUtil.isBlank(apiUrl) ? DEFAULT_SMS_API_URL : apiUrl.trim();
            HttpUtil.post(finalApiUrl, params);
        } catch (Exception e) {
            //
        }
    }
}
