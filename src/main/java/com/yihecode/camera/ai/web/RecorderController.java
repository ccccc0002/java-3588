package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yihecode.camera.ai.utils.ConnUtil;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * 录音管理, 适配中化定制需求
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Controller
@RequestMapping({"/recorder"})
public class RecorderController {

    /**
     *
     */
    @Value("${uploadDir}")
    private String uploadDir;

    private static final Map<String, Integer> codeMap = new HashMap<>();

    static {
        codeMap.put("基地管理", 24);
        codeMap.put("摄像头", 11);
        codeMap.put("算法", 10);
    }

    /**
     *
     * @return
     */
    @PostMapping("/upload")
    @ResponseBody
    public JsonResult doUpload(@RequestParam("file") MultipartFile file) {
        if (file == null) {
            return JsonResultUtils.fail("没有录音");
        }

        //
        try {
            //
            String newFileName = IdUtil.fastSimpleUUID() + ".wav";
            file.transferTo(new File(this.uploadDir + newFileName));

            //
            String accessToken = this.getAccessToken();
            if(StrUtil.isBlank(accessToken)) {
                return JsonResultUtils.fail("无法识别，请重新尝试");
            }

            //
            List<String> speechList = parseRecorder(accessToken, this.uploadDir + newFileName);
            if(speechList == null || speechList.isEmpty()) {
                return JsonResultUtils.fail("没有识别到您想要的操作");
            }

            //
            Integer code = -1;
            Iterator<String> iter = codeMap.keySet().iterator();
            while(iter.hasNext()) {
                String key = iter.next();

                //
                boolean found = false;
                for(String speech : speechList) {
                    if(speech.contains(key)) {
                        found = true;
                        code = codeMap.get(key);
                        break;
                    }
                }

                //
                if(found) {
                    break;
                }
            }

            //
            if(code == -1) {
                return JsonResultUtils.fail("没有识别到您想要的操作");
            }

            return JsonResultUtils.success(code);
        } catch (Exception e) {
            e.printStackTrace();
            return JsonResultUtils.fail("录音上传异常，请稍后重试");
        }
    }

    /**
     *
     * @return
     */
    private String getAccessToken() {
//        AppID：28826800
//
//        API Key：PpkZnv4YLNZe8Odtb7N9xaui
//
//        Secret Key：r0Atv2C8PohzYXFHGzSLzt6PlTu5Wnzc

        try {
            //
            String apikey = "PpkZnv4YLNZe8Odtb7N9xaui";
            String secretKey = "r0Atv2C8PohzYXFHGzSLzt6PlTu5Wnzc";

            // 获取token地址
            String authHost = "https://aip.baidubce.com/oauth/2.0/token?";
            String getAccessTokenUrl = authHost
                    + "grant_type=client_credentials"
                    + "&client_id=" + apikey
                    + "&client_secret=" + secretKey;

            //
            URL realUrl = new URL(getAccessTokenUrl);
            HttpURLConnection connection = (HttpURLConnection) realUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            Map<String, List<String>> map = connection.getHeaderFields();
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String result = "";
            String line;
            while ((line = in.readLine()) != null) {
                result += line;
            }

            /**
             * 返回结果示例
             */
//            System.err.println("result:" + result);
            JSONObject jsonObject = JSON.parseObject(result);
            String access_token = jsonObject.getString("access_token");
            return access_token;
        } catch (Exception e) {
            //
        }
        return null;
    }

    /**
     *
     * @param accessToken
     * @param fileName
     * @return
     * @throws Exception
     */
    public List<String> parseRecorder(String accessToken, String fileName) throws Exception {
        try {
            String url = "http://vop.baidu.com/server_api";

            byte[] content = getFileContent(fileName);
            String speech = base64Encode(content);
            // System.out.println("speech -> " + speech);//

            JSONObject params = new JSONObject();
            //  1537 表示识别普通话，使用输入法模型。 其它语种参见文档
            params.put("dev_pid", 1537);
            //params.put("lm_id",LM_ID);//测试自训练平台需要打开注释
            params.put("format", "wav");
            params.put("rate", 16000);
            params.put("token", accessToken);
            params.put("cuid", "1234567JAVA");
            params.put("channel", "1");
            params.put("len", content.length);
            params.put("speech", speech);

            // System.out.println(params.toString());
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.getOutputStream().write(params.toString().getBytes());
            conn.getOutputStream().close();
            String result = ConnUtil.getResponseString(conn);

            System.out.println("语音识别结果: " + result);

            //params.put("speech", "base64Encode(getFileContent(FILENAME))");
            //System.out.println("url is : " + url);
            //System.out.println("params is :" + params.toString());

            //
            JSONObject resultJson = JSON.parseObject(result);
            Integer errNo = resultJson.getInteger("err_no");
            if(errNo == null || errNo != 0) {
                return null;
            }

            JSONArray resultArray = resultJson.getJSONArray("result");
            if(resultArray == null || resultArray.isEmpty()) {
                return null;
            }

            //
            List<String> speechList = new ArrayList<>();

            //
            int len = resultArray.size();

            //
            for(int i = 0; i < len; i++) {
                String resultStr = this.clearString(resultArray.getString(i));
                if(StrUtil.isBlank(resultStr)) {
                    continue;
                }
                speechList.add(resultStr);
            }
            return speechList;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     *
     * @param filename
     * @return
     * @throws IOException
     */
    private byte[] getFileContent(String filename) throws IOException {
        File file = new File(filename);
        if (!file.canRead()) {
            System.err.println("文件不存在或者不可读: " + file.getAbsolutePath());
            throw new RuntimeException("file cannot read: " + file.getAbsolutePath());
        }
        FileInputStream is = null;
        try {
            is = new FileInputStream(file);
            return ConnUtil.getInputStreamContent(is);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     *
     * @param content
     * @return
     */
    private String base64Encode(byte[] content) {
        Base64.Encoder encoder = Base64.getEncoder(); // JDK 1.8  推荐方法
        String str = encoder.encodeToString(content);
        return str;
    }

    /**
     *
     * @param source
     * @return
     */
    private String clearString(String source) {
        if(StrUtil.isBlank(source)) {
            return null;
        }
        return source.replaceAll("[^\\u4E00-\\u9FA5]", "");
    }

}