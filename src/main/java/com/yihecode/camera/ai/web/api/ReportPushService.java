package com.yihecode.camera.ai.web.api;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@EnableAsync
public class ReportPushService {

    @Async
    public void request(String url, JSONObject params, boolean toBase64, String fileName) {
        request(url, params, toBase64, fileName, null);
    }

    @Async
    public void request(String url, JSONObject params, boolean toBase64, String fileName, String bearerToken) {
        requestSync(url, params, toBase64, fileName, bearerToken);
    }

    public Map<String, Object> requestSync(String url,
                                           JSONObject params,
                                           boolean toBase64,
                                           String fileName,
                                           String bearerToken) {
        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        result.put("success", false);
        result.put("status", -1);
        result.put("response", "");

        if (StrUtil.isBlank(url)) {
            result.put("error", "push url is blank");
            return result;
        }

        int statusCode = -1;
        HttpEntity httpEntity = null;
        String responseText = "";
        try {
            JSONObject requestBody = params == null ? new JSONObject() : JSON.parseObject(params.toJSONString());
            if (requestBody == null) {
                requestBody = new JSONObject();
            }

            if (toBase64) {
                int count = 0;
                while (true) {
                    File file = new File(fileName);
                    if (file.exists() && file.canRead()) {
                        break;
                    }
                    count++;
                    if (count >= 10) {
                        break;
                    }
                    Thread.sleep(20);
                }

                try {
                    File file = new File(fileName);
                    String imageBase64 = ImgUtil.toBase64(ImgUtil.read(file), FileUtil.extName(file));
                    requestBody.put("imageBase64", imageBase64);
                } catch (Exception e) {
                    requestBody.put("imageBase64", "");
                }
            } else {
                requestBody.put("imageBase64", "");
            }

            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(url);
                httpPost.addHeader("Accept-Encoding", "gzip, deflate, br");
                httpPost.addHeader("Content-Type", "application/json");
                if (StrUtil.isNotBlank(bearerToken)) {
                    httpPost.addHeader("Authorization", "Bearer " + bearerToken.trim());
                }
                httpPost.setEntity(new StringEntity(requestBody.toString(), StandardCharsets.UTF_8));

                RequestConfig requestConfig = RequestConfig.custom()
                        .setSocketTimeout(10000)
                        .setConnectTimeout(10000)
                        .setConnectionRequestTimeout(500)
                        .build();
                httpPost.setConfig(requestConfig);

                try (CloseableHttpResponse response = client.execute(httpPost)) {
                    statusCode = response.getStatusLine().getStatusCode();
                    httpEntity = response.getEntity();
                    responseText = httpEntity == null ? "" : EntityUtils.toString(httpEntity, StandardCharsets.UTF_8);
                }
            }

            result.put("status", statusCode);
            result.put("response", responseText);
            result.put("success", statusCode == 200);

            if (statusCode != 200) {
                log.error("report push failed status={}, url={}, response={}", statusCode, url, responseText);
            } else {
                log.info("report push success status={}, url={}", statusCode, url);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
            log.error("report push exception url={}, ex={}", url, e.getMessage());
        } finally {
            try {
                EntityUtils.consume(httpEntity);
            } catch (Exception ignore) {
            }
        }

        return result;
    }
}