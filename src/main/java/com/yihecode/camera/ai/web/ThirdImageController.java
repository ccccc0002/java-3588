package com.yihecode.camera.ai.web;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.job.PredictCounter;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraAlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 第三方图片上传, 识别, 适配矿山定制需求
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Controller
@RequestMapping({"/third/image"})
public class ThirdImageController {

    /**
     * 图片存储目录
     */
    @Value("${cameraDir}")
    private String cameraDir;

    /**
     * 告警接口地址
     */
    @Value("${proj-confs.self-report-url}")
    private String reportUrl;

    /**
     * 摄像头服务
     */
    @Autowired
    private CameraService cameraService;

    /**
     * 算法服务
     */
    @Autowired
    private AlgorithmService algorithmService;

    /**
     * 摄像头关联算法服务
     */
    @Autowired
    private CameraAlgorithmService cameraAlgorithmService;

    /**
     * 算法地址配置
     */
    @Autowired
    private ConfigService configService;

    /**
     * 上传图片
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ResponseBody
    public JsonResult doUpload(@RequestParam("file") MultipartFile file, @RequestParam("timestamp") String timestamp, @RequestParam(defaultValue = "综采面挡板识别") String cameraName, @RequestParam(defaultValue = "dangban") String type) {
        long s1 = System.currentTimeMillis();

        //
        Map<String, Object> retMap = new HashMap<>();
        retMap.put("result_code", 405); // 200-ok 401-摄像不存在 402-摄像头无关联算法 403-算法接口地址未配置 404-图片转base64错误 405-没有识别结果  500-服务端异常
        retMap.put("result_info", "没有识别结果");
        retMap.put("timestamp", timestamp);

        //
        if(file == null) {
            retMap.put("result_code", 405);
            retMap.put("result_info", "没有识别结果 - 文件错误");
            return JsonResultUtils.success(retMap);
        }

        //
        String dateYear = DateUtil.format(new Date(), "yyyy");
        String dateDay = DateUtil.format(new Date(), "yyyyMMdd");
        String path = cameraDir + dateYear + "/" + dateDay + "/";
        File pathFile = new File(path);
        if(!pathFile.exists()) {
            pathFile.mkdirs();
        }

        //
        try {
            // 存储文件
            long ss1 = System.currentTimeMillis();
            String originalFilename = file.getOriginalFilename();
            String filePath = path + originalFilename;
            file.transferTo(new File(filePath));
            log.info("存图耗时: {}", (System.currentTimeMillis() - ss1));

            long ss2 = System.currentTimeMillis();

            //
            String algorithmUrl = configService.getByValTag("algorithmUrl");
            if(StrUtil.isBlank(algorithmUrl)) {
                //
                retMap.put("result_code", 403);
                retMap.put("result_info", "算法接口地址未配置");
                return JsonResultUtils.success(retMap);
            }

            // 调用算法
            Camera camera = cameraService.getByName(cameraName);
            if(camera == null) {
                FileUtil.del(filePath);
                //
                retMap.put("result_code", 401);
                retMap.put("result_info", "摄像头不存在");
                return JsonResultUtils.success(retMap);
            }

            //
            Map<Long, String> algorithmNames = algorithmService.toMap();

            //
            List<CameraAlgorithm> cameraAlgorithmList = cameraAlgorithmService.listByCamera(camera.getId());
            if(cameraAlgorithmList == null || cameraAlgorithmList.isEmpty()) {
                FileUtil.del(filePath);

                //
                retMap.put("result_code", 402);
                retMap.put("result_info", "摄像头无关联算法");
                return JsonResultUtils.success(retMap);
            }

            //
            JSONArray algorithmArray = new JSONArray();
            for(CameraAlgorithm cameraAlgorithm : cameraAlgorithmList) {
                String algorithmName = algorithmNames.get(cameraAlgorithm.getAlgorithmId());

                // 挡板
                if(type.equals("dangban2")) {
                    if(algorithmName != null && algorithmName.equals("新挡板检测")) {
                        JSONObject algorithmObj = new JSONObject();
                        algorithmObj.put("algorithm_id", cameraAlgorithm.getAlgorithmId());
                        algorithmObj.put("algorithm_confidence", cameraAlgorithm.getConfidence());
                        algorithmObj.put("algorithm_name", algorithmName);
                        algorithmArray.add(algorithmObj);
                    }
                }

                // 挡板
                if(type.equals("dangban")) {
                    if(algorithmName != null && algorithmName.equals("挡板检测")) {
                        JSONObject algorithmObj = new JSONObject();
                        algorithmObj.put("algorithm_id", cameraAlgorithm.getAlgorithmId());
                        algorithmObj.put("algorithm_confidence", cameraAlgorithm.getConfidence());
                        algorithmObj.put("algorithm_name", algorithmName);
                        algorithmArray.add(algorithmObj);
                    }
                }

                // 指示灯
                if(type.equals("zhishideng")) {
                    if(algorithmName != null && algorithmName.contains("指示灯")) {
                        JSONObject algorithmObj = new JSONObject();
                        algorithmObj.put("algorithm_id", cameraAlgorithm.getAlgorithmId());
                        algorithmObj.put("algorithm_confidence", cameraAlgorithm.getConfidence());
                        algorithmObj.put("algorithm_name", algorithmName);
                        algorithmArray.add(algorithmObj);
                    }
                }
            }

            //
            String area = "";
            if(StrUtil.isNotBlank(camera.getApiParams())) {
                JSONArray apArray = JSON.parseArray(camera.getApiParams());
                if(apArray != null && apArray.size() > 0) {
                    area = apArray.getJSONArray(0).toJSONString();
                }
            }

            //
            System.out.println("算法参数: " + type);
            System.out.println(area);
            System.out.println(algorithmArray);

            //
            long x1 = System.currentTimeMillis();
//            String imageBase64 = ImgUtil.toBase64(ImgUtil.read(filePath), FileUtil.extName(originalFilename));

            String imageBase64 = toBase64(filePath);
            System.out.println("tobase64时间: " + (System.currentTimeMillis() - x1));
            if(imageBase64 == null) {
                //
                retMap.put("result_code", 404);
                retMap.put("result_info", "图片转base64错误");
                return JsonResultUtils.success(retMap);
            }
            //
            JSONObject params = new JSONObject();
            params.put("image_base64", imageBase64);
            params.put("param", algorithmArray);
            params.put("area", area);
            params.put("camera_id", camera.getId() + "-" + PredictCounter.getInst().getCount(camera.getId())); // 暂时不要传

            //log.info("请求参数  {}", params);


            //
//            JSONObject logparams = new JSONObject();
//            logparams.put("image_base64", "不显示");
//            logparams.put("param", algorithmArray);
//            logparams.put("area", area);
//            logparams.put("camera_id", params.get("camera_id")); // 暂时不要传
//            logparams.put("camera_name", cameraName);
//            log.info("摄像头ID: {}, 请求参数: {} ", camera.getId(), logparams);

            log.info("整理耗时: {}", (System.currentTimeMillis() - ss2));

            long ss3 = System.currentTimeMillis();
            JSONArray predictArray = this.requestAlgorithm(algorithmUrl, params);
            log.info("算法耗时: {}", (System.currentTimeMillis() - ss3));
            if(predictArray == null || predictArray.isEmpty()) {
                FileUtil.del(filePath);

                //
                retMap.put("result_code", 405);
                retMap.put("result_info", "无识别结果");
                return JsonResultUtils.success(retMap);
            }

            // 调用告警接口
            boolean isPredict = false;
            int len = predictArray.size();
            for(int i = 0; i < len; i++) {
                JSONObject predictObj = predictArray.getJSONObject(i);
                Long algorithmId = predictObj.getLong("algorithm_id");
                JSONArray data = predictObj.getJSONArray("data");
                if(data == null || data.isEmpty()) {
                    continue;
                }

                //
                long ss4 = System.currentTimeMillis();
                isPredict = true;
                this.requestReport(reportUrl, camera.getId(), algorithmId, filePath, data.toJSONString());
                log.info("上报耗时: {}", (System.currentTimeMillis() - ss4));

                //
                retMap.put("result_code", 200);
                retMap.put("result_info", "识别成功");
                retMap.put("result_data", data.toJSONString());
                retMap.put("algorithm_name", algorithmNames.get(algorithmId));
                retMap.put("camera_name", camera.getName());

                log.info("整体耗时: {}", (System.currentTimeMillis() - s1));
                return JsonResultUtils.success(retMap);
            }

            log.info("整体耗时: {}", (System.currentTimeMillis() - s1));
            // 没有检测结果，删除图片
            if(!isPredict) {
                FileUtil.del(filePath);

                //
                retMap.put("result_code", 405);
                retMap.put("result_info", "无识别结果");
                return JsonResultUtils.success(retMap);
            } else {
                //
                retMap.put("result_code", 200);
                retMap.put("result_info", "识别成功");
                return JsonResultUtils.success(retMap);
            }
        } catch (Exception e) {
            System.out.println("报错了");
            e.printStackTrace();
            //
            retMap.put("result_code", 500);
            retMap.put("result_info", "服务端异常@" + e.getMessage());
            return JsonResultUtils.success(retMap);
        }
    }

    /**
     * 调用自身告警接口
     * @param url
     * @param params
     */
    private void requestReport(String url, Long cameraId, Long algorithmId, String fileName, String params) {
        int statusCode = -1;
        HttpEntity httpEntity = null;
        try {
            CloseableHttpClient client = HttpClients.createDefault();
            //
            HttpPost httpPost = new HttpPost(url);
            //
            List<NameValuePair> list = new ArrayList<>();
            list.add(new BasicNameValuePair("camera_id", cameraId + ""));
            list.add(new BasicNameValuePair("algorithm_id", algorithmId + ""));
            list.add(new BasicNameValuePair("file_name", fileName));
            list.add(new BasicNameValuePair("params", params));
            httpPost.setEntity(new UrlEncodedFormEntity(list, "UTF-8"));

            //
//                httpPost.addHeader("Accept-Encoding", "gzip, deflate, br");
//                httpPost.addHeader("Content-Type", "application/json");
            //
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).setConnectionRequestTimeout(500).build();
            httpPost.setConfig(requestConfig);
            //
            CloseableHttpResponse response = client.execute(httpPost);
            statusCode = response.getStatusLine().getStatusCode();
            httpEntity = response.getEntity();

            if (statusCode != 200) {
                log.error("调用自身告警接口状态异常 {}, {}, {}", statusCode, url, EntityUtils.toString(httpEntity));
            } else {
                log.info("调用自身告警接口状态成功 {}, {}, {}", statusCode, url, EntityUtils.toString(httpEntity));
            }

            response.close();
            client.close();
        } catch (Exception e) {
            log.error("调用自身告警接口异常 {}, ex: {}", url, e);
        } finally {
            try {
                EntityUtils.consume(httpEntity);
            } catch (Exception e) {}
        }
    }

    /**
     * 接口地址: http://192.168.0.134:5002/api/safety/predict
     * 请求参数:
     * {"image_base64": "",
     *  "param": [{"algorithm_id": 1, "algorithm_name": "烟雾火灾识别", "algorithm_confidence": 0.5},
     *           {"algorithm_id": 2, "algorithm_name": "抽烟识别", "algorithm_confidence": 0.5}
     *          ],
     * "area": "",
     * "camera_id": "333"
     * }
     * 调用算法识别接口
     * @param url
     * @param params
     */
    private JSONArray requestAlgorithm(String url, JSONObject params) {
        int statusCode = -1;
        HttpEntity httpEntity = null;
        try {
            CloseableHttpClient client = HttpClients.createDefault();
            //
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("Accept-Encoding", "gzip, deflate, br");
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(params.toString(),"UTF-8"));

            //
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(60000).setConnectTimeout(60000).setConnectionRequestTimeout(500).build();
            httpPost.setConfig(requestConfig);

            //
            CloseableHttpResponse response = client.execute(httpPost);
            statusCode = response.getStatusLine().getStatusCode();
            httpEntity = response.getEntity();

            String responseContent = EntityUtils.toString(httpEntity);
            if(statusCode == 200) {
                JSONObject resultJson = JSON.parseObject(responseContent);
                if(resultJson.containsKey("code") && resultJson.get("code") != null && resultJson.getInteger("code") == 200) {
                    if(resultJson.containsKey("data") && resultJson.get("data") != null) {
                        return resultJson.getJSONArray("data");
                    }
                }
            } else {
                log.error("调用图片算法接口状态异常 {}, {}", statusCode, url);
            }
        } catch (Exception e) {
            log.error("调用图片算法接口异常 {}, ex:{}", url, e);
        } finally {
            try {
                EntityUtils.consume(httpEntity);
            } catch (Exception e) {}
        }
        return null;
    }

    /**
     * 图片转base64， hutool效率太慢
     * @param filePath
     * @return
     */
    private String toBase64(String filePath) {
        try {
            byte[] data = null;
            try (InputStream in = new FileInputStream(new File(filePath))) {
                data = new byte[in.available()];
                in.read(data);
            } catch (IOException e) {
                e.printStackTrace();
                //LoggerUtil.error(ImageBase64Utils.class, e.toString());
            }
            return new String(Base64.getEncoder().encode(data));
        } catch (Exception e) {
            log.error("图片转base64异常", e);
        }
        return null;
    }
}