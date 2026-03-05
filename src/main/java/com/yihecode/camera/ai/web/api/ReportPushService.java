package com.yihecode.camera.ai.web.api;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
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

/**
 * 告警第三方推送，将最新对告警同时推送到第三方平台
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Component
@EnableAsync
public class ReportPushService {

    /**
     * 调用接口
     * @param url
     * @param params
     */
    @Async
    public void request(String url, JSONObject params, boolean toBase64, String fileName) {
        int statusCode = -1;
        HttpEntity httpEntity = null;
        try {
            //
            if(toBase64) {
                // 文件还没写完，还不能读取？
                int count = 0;
                while(true) {
                    File file = new File(fileName);
                    if(file.exists() && file.canRead()) {
                        break;
                    }
                    count++;

                    if(count >= 10) {
                        break;
                    }

                    Thread.sleep(20);
                }

                try {
                    File file = new File(fileName);
                    String imageBase64 = ImgUtil.toBase64(ImgUtil.read(file), FileUtil.extName(file));
                    params.put("imageBase64", imageBase64);
                } catch (Exception e) {
                    params.put("imageBase64", "");
                }
            } else {
                params.put("imageBase64", "");
            }

            CloseableHttpClient client = HttpClients.createDefault();
            //
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("Accept-Encoding", "gzip, deflate, br");
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(params.toString(),"UTF-8"));

            //
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).setConnectionRequestTimeout(500).build();
            httpPost.setConfig(requestConfig);

            //
            CloseableHttpResponse response = client.execute(httpPost);
            statusCode = response.getStatusLine().getStatusCode();
            httpEntity = response.getEntity();

            //
            if (statusCode != 200) {
                log.error("调用第三方上报接口状态异常 status:{}, url:{}, camera:{}, algorithm:{}, response:{}", statusCode, url, params.getString("camera_name"), params.getString("algorithm_name"), EntityUtils.toString(httpEntity));
            } else {
                log.info("调用第三方上报接口状态成功 status:{}, url:{}, camera:{}, algorithm:{}, response:{}", statusCode, url, params.getString("camera_name"), params.getString("algorithm_name"), EntityUtils.toString(httpEntity));
            }

            response.close();
            client.close();
        } catch (Exception e) {
            log.error("调用第三方上报接口异常 {}, ex:{}", url, e);
        } finally {
            try {
                EntityUtils.consume(httpEntity);
            } catch (Exception e) {}
        }
    }
}
