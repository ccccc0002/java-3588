package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Model;
import com.yihecode.camera.ai.entity.ModelDepend;
import com.yihecode.camera.ai.enums.ModelType;
import com.yihecode.camera.ai.service.*;
import com.yihecode.camera.ai.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * 算法模型管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Controller
@RequestMapping({"/model"})
public class ModelController {

    //
    @Autowired
    private AlgorithmService algorithmService;

    //
    @Autowired
    private CameraAlgorithmService cameraAlgorithmService;

    //
    @Autowired
    private ModelService modelService;

    //
    @Autowired
    private ModelDependService modelDependService;

    //
    @Autowired
    private ConfigService configService;

    @Autowired
    private ModelTestResultService modelTestResultService;

    @Autowired
    private ModelTestCaptureService modelTestCaptureService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    //
    @Value("${modelDir}")
    private String uploadDir;

    /**
     *
     * @return
     */
    @GetMapping({"", "/"})
    public String index() {
        return "model/index";
    }

    /**
     *
     * @param id
     * @param modelMap
     * @return
     */
    @GetMapping({"/form"})
    public String form(Long id, ModelMap modelMap) {
        //
        List<Model> modelList = modelService.listData();
        if(modelList == null) {
            modelList = new ArrayList<>();
        }
        //
        List<ModelDepend> modelDependList = modelDependService.listByModel(id);
        //
        List<Long> modelDependIds = new ArrayList<>();
        for(ModelDepend modelDepend : modelDependList) {
            modelDependIds.add(modelDepend.getDependModelId());
        }
        //
        List<Map<String, Object>> dataList = new ArrayList<>();
        for(Model model : modelList) {
            if(model.getId().equals(id)) {
                continue;
            }
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("id", model.getId());
            dataMap.put("name", model.getName());
            dataMap.put("checked", modelDependIds.contains(model.getId()) ? "checked" : "");
            dataList.add(dataMap);
        }
        modelMap.addAttribute("modelList", dataList);

        //
        Model model = modelService.getById(id);
        modelMap.addAttribute("model", model);

        return "model/form";
    }

    /**
     *
     * @return
     */
    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit) {
        IPage<Model> pageObj = new Page<>(page, limit);
        IPage<Model> pageResult = modelService.listPage(pageObj);
        List<Model> modelList = pageResult.getRecords();
        if(modelList != null) {
            for(Model model : modelList) {
                model.setTypeName(ModelType.getText(model.getType()));
                model.setFileSize(FileSizeUtils.formatSize(model.getOnnxSize()));
            }
        }
        return PageResultUtils.success(pageResult.getTotal(), pageResult.getRecords());
    }

    /**
     *
     * @param model
     * @return
     */
    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(Model model) throws Exception {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        //
        if(StrUtil.isBlank(model.getName())) {
            return JsonResultUtils.fail("请输入模型名称");
        }
        if(model.getType() == null) {
            return JsonResultUtils.fail("请选择模型类型");
        }
        //
        Map<String, Object> retMap = modelService.saveModel(model);
        operationLogService.record("model:save", "modelId=" + model.getId(), true, "model saved", model.getName());
        return JsonResultUtils.success(retMap);
    }

    /**
     * 模型启用
     * @param modelId
     * @return
     * @throws Exception
     */
    @PostMapping({"/start"})
    @ResponseBody
    public JsonResult startModel(Long modelId) throws Exception {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        //
        modelService.updateModelEnable(modelId);
        operationLogService.record("model:start", "modelId=" + modelId, true, "model enable toggled", "");
        return JsonResultUtils.success();
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     *
     * @param id
     * @return
     */
    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        Model model = modelService.getById(id);
        if(model == null) {
            return JsonResultUtils.fail("模型不存在或已删除");
        }
        //
        if(model.getState() == 0) {
            return JsonResultUtils.fail("模型为启用状态，不可以删除");
        }
        //
        this.modelService.removeById(id);
        //
        if(StrUtil.isNotBlank(model.getOnnxName())) {
            File onnxFile = new File(uploadDir + model.getOnnxName());
            if(onnxFile.exists()) {
                FileUtil.del(onnxFile);
            }
        }
        //
        //
        int newVersionCount = modelService.getVersionCountByName(model.getName());
        modelService.updateVersionCount(model.getName(), newVersionCount);
        //
        operationLogService.record("model:delete", "modelId=" + id, true, "model deleted", model.getName());
        return JsonResultUtils.success();
    }

    /**
     * 版本列表
     * @return
     */
    @GetMapping("/version")
    public String versionList(Long modelId, ModelMap modelMap) {
        modelMap.addAttribute("modelId", modelId);
        return "model/version_list";
    }

    /**
     * 版本列表
     * @return
     */
    @PostMapping({"/listVersion"})
    @ResponseBody
    public PageResult listVersion(Long modelId) {
        List<Model> modelList = modelService.listVersion(modelId);
        if(modelList != null) {
            for(Model model : modelList) {
                model.setTypeName(ModelType.getText(model.getType()));
                model.setFileSize(FileSizeUtils.formatSize(model.getOnnxSize()));
            }
        }
        return PageResultUtils.success(null, modelList);
    }

    // ----------------- 模型测试 -----------------
    /**
     * 模型测试
     * @return
     */
    @GetMapping("/test")
    public String test(ModelMap modelMap) {
        List<Algorithm> algorithmList = algorithmService.list();
        modelMap.addAttribute("algorithmList", algorithmList);
        return "model/test";
    }

    /**
     * 模型测试 - 图片上传
     * @return
     */
    @PostMapping("/test/predict")
    @ResponseBody
    public JsonResult testPredict(String file, String algorithms, String cameraId, String marks, Double imgHeight) {
        //
        if(StrUtil.isBlank(file)) {
            return JsonResultUtils.fail("请上传需要测试的图片");
        }
        //
        if(StrUtil.isBlank(algorithms)) {
            return JsonResultUtils.fail("请选择需要测试的算法");
        }
        //
        List<Long> algorithmIdList = new ArrayList<>();
        try {
            //
            JSONArray array = JSON.parseArray(algorithms);
            int len = array.size();
            if(len == 0) {
                return JsonResultUtils.fail("请选择需要测试的算法");
            }
            //
            for(int i = 0; i < len; i++) {
                algorithmIdList.add(array.getLong(i));
            }
        } catch (Exception e) {
            return JsonResultUtils.fail("请选择需要测试的算法");
        }
        //
        if(algorithmIdList.isEmpty()) {
            return JsonResultUtils.fail("请选择需要测试的算法");
        }
        //
        if(StrUtil.isBlank(cameraId)) {
            cameraId = IdUtil.randomUUID();
        }
        //
        if(StrUtil.isBlank(marks)) {
            marks = "";
        }
        //
        try {
            //
            String filepath = uploadDir + "/" + file;
            File imageFile = new File(filepath);
            if(!imageFile.exists()) {
                return JsonResultUtils.fail("图片文件不存在");
            }

            //
            String algorithmUrl = configService.getByValTag("algorithmUrl");
            if(StrUtil.isBlank(algorithmUrl)) {
                return JsonResultUtils.fail("算法地址未配置");
            }

            //
            BufferedImage image = ImgUtil.read(filepath);
            String imageBase64 = ImgUtil.toBase64(image, FileUtil.extName(filepath));

            //
            JSONArray algorithmArray = new JSONArray();
            JSONArray algorithmJsonArray = JSON.parseArray(algorithms);
            int algorithmJsonArraySize = algorithmJsonArray.size();
            for(int i = 0; i < algorithmJsonArraySize; i++) {
                Long algorithmId = algorithmJsonArray.getLong(i);
                Algorithm algorithm = algorithmService.getById(algorithmId);
                if(algorithm != null) {
                    JSONObject algorithmObj = new JSONObject();
                    algorithmObj.put("algorithm_id", algorithm.getId());
                    algorithmObj.put("algorithm_confidence", 0.3);
                    algorithmObj.put("algorithm_name", algorithm.getName());
                    algorithmObj.put("algorithm_name_en", algorithm.getNameEn());
                    algorithmArray.add(algorithmObj);
                }
            }

            //
            if(StrUtil.isBlank(marks) || imgHeight == null || imgHeight == 0) {
                marks = "";
            } else {
                // 变化比例
                int orginalHeight = image.getHeight();
                double rate = orginalHeight * 1.0 / imgHeight.intValue();

                // 变化坐标
                JSONArray newMarks = new JSONArray();
                JSONArray rootMark = JSON.parseArray(marks);
                int len = rootMark.size();
                for(int i = 0; i < len; i++) {
                    JSONObject mark = rootMark.getJSONObject(i);
                    int x = mark.getIntValue("x");
                    int y = mark.getIntValue("y");

                    JSONObject newMark = new JSONObject();
                    newMark.put("x", Double.valueOf(x * rate).intValue());
                    newMark.put("y", Double.valueOf(y * rate).intValue());
                    newMarks.add(newMark);
                }

                //
                marks = JSON.toJSONString(newMarks);
            }

            //
            JSONObject params = new JSONObject();
            params.put("image_base64", imageBase64);
            params.put("param", algorithmArray);
            params.put("area", marks);
            params.put("camera_id", cameraId); // 暂时不要传

            // [{"confidence":0.96,"position":[270,207,335,295],"type":"nohelmet"}, {"confidence":0.96,"position":[270,207,335,295],"type":"nohelmet"}]
            JSONArray predictArray = this.requestAlgorithm(algorithmUrl, params);
            if(predictArray == null || predictArray.isEmpty()) {
                return JsonResultUtils.fail("algorithm predict response is empty");
            }

            List<Map<String, Object>> detections = modelTestResultService.flattenDetections(predictArray);
            String json = JSON.toJSONString(detections, SerializerFeature.PrettyFormat,SerializerFeature.WriteMapNullValue,SerializerFeature.WriteDateUseDateFormat, SerializerFeature.WriteNullListAsEmpty);
            String rawJson = JSON.toJSONString(predictArray, SerializerFeature.PrettyFormat,SerializerFeature.WriteMapNullValue,SerializerFeature.WriteDateUseDateFormat, SerializerFeature.WriteNullListAsEmpty);
            String resultFile = modelTestResultService.saveAnnotatedImage(uploadDir, file, detections);

            Map<String, Object> retMap = new HashMap<>();
            retMap.put("json", json);
            retMap.put("rawJson", rawJson);
            retMap.put("detections", detections);
            retMap.put("sourceFile", file);
            retMap.put("resultFile", resultFile);

            return JsonResultUtils.success(retMap);
        } catch (Exception e) {
            return JsonResultUtils.fail("存储文件异常");
        }
    }

    @PostMapping("/test/capture")
    @ResponseBody
    public JsonResult testCapture(String rtspUrl) {
        if (StrUtil.isBlank(rtspUrl)) {
            return JsonResultUtils.fail("rtsp url is required");
        }
        String ffmpegBin = configService.getByValTag("media_ffmpeg_bin");
        String file = modelTestCaptureService.capture(uploadDir, rtspUrl, ffmpegBin);
        if (StrUtil.isBlank(file)) {
            return JsonResultUtils.fail("capture failed");
        }
        return JsonResultUtils.success(file);
    }

    /**
     * 接口地址: http://demo.chineseocr.com:5002/api/safety/predict
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
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).setConnectionRequestTimeout(500).build();
            httpPost.setConfig(requestConfig);

            //
            CloseableHttpResponse response = client.execute(httpPost);
            statusCode = response.getStatusLine().getStatusCode();
            httpEntity = response.getEntity();

            String json = EntityUtils.toString(httpEntity);

            System.out.println("statusCode " + statusCode);
            System.out.println("json " + json);

            if(statusCode == 200) {
                JSONObject resultJson = JSON.parseObject(json);
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
     * 上传文件
     * @param file
     * @return
     */
    @PostMapping("/test/upload")
    @ResponseBody
    public JsonResult testUpload(@RequestParam(value = "file", required = false) MultipartFile file) {
        //
        if(file == null) {
            return JsonResultUtils.fail("请上传需要测试的图片");
        }

        // 创建目录
        String dateDir = DateUtil.format(new Date(), "yyyy") + "/" + DateUtil.format(new Date(), "MMdd") + "/";
        String path = uploadDir + dateDir;
        File pathFile = new File(path);
        if(!pathFile.exists()) {
            pathFile.mkdirs();
        }

        // 保存的文件名称
        String saveName = IdUtil.randomUUID() + "." + FileUtil.extName(file.getOriginalFilename());

        //
        try {
            //
            file.transferTo(new File(path + saveName));
            //
            return JsonResultUtils.success(dateDir + saveName);
        } catch (Exception e) {
            return JsonResultUtils.fail("存储文件异常");
        }
    }

    /**
     * 模型测试 - 图片展示
     * @return
     */
    @GetMapping("/test/stream")
    public void testPicStream(String file, HttpServletResponse response) {
        try {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(uploadDir + file));
            response.setContentType("image/jpeg");
            IOUtils.copy(in, response.getOutputStream());
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --------------- 模型上传 -----------------
    /**
     * @author van
     * 检查文件存在与否
     */
    @PostMapping("/checkFile")
    @ResponseBody
    public JsonResult checkFile(@RequestParam(value = "md5File") String md5File, @RequestParam(value = "fileName") String fileName) {
        //
//        String extName = FileUtil.extName(fileName);
//        if(extName == null || !"onnx".equals(extName.toLowerCase())) {
//            return JsonResultUtils.fail("文件类型仅支持[onnx]类型");
//        }

        //
        Map<String, Object> retMap = new HashMap<>();
        retMap.put("errType", 200);
        retMap.put("errMsg", "ERROR");

        //
        Model model = modelService.getByOnnxMd5(md5File);
        if(model != null) {
            retMap.put("errType", 4001);
            retMap.put("errMsg", "模型文件已存在(md5)，不要重复上传");
            return JsonResultUtils.success(retMap);
        }

        //
        Model model2 = modelService.getByOnnxName(fileName);
        if(model2 != null) {
            retMap.put("errType", 4002);
            retMap.put("errMsg", "模型文件名称已存在，是否覆盖？");
            return JsonResultUtils.success(retMap);
        }

        retMap.put("errMsg", "OK");
        return JsonResultUtils.success(retMap);
    }

    /**
     * @author van
     * 检查分片存不存在
     */
    @SaIgnore
    @PostMapping("/checkChunk")
    @ResponseBody
    public Boolean checkChunk(@RequestParam(value = "md5File") String md5File,
                              @RequestParam(value = "chunk") Integer chunk) {
        //
        Boolean exist = false;
        String path = uploadDir + md5File + "/";

        //
        String chunkName = chunk + ".tmp";
        File file = new File(path + chunkName);
        if (file.exists()) {
            exist = true;
        }
        return exist;
    }

    /**
     * @author van
     * 修改上传
     */
    @SaIgnore
    @PostMapping("/chunkUpload")
    @ResponseBody
    public Boolean chunkUpload(@RequestParam(value = "file") MultipartFile file,
                               @RequestParam(value = "md5File") String md5File,
                               @RequestParam(value = "chunk",required= false) Integer chunk) { //第几片，从0开始
        //
        String path = uploadDir + md5File + "/";
        //
        File dir = new File(path);
        if (!dir.exists()) { // 目录不存在，创建目录
            dir.mkdirs();
        }
        //
        String chunkName;
        if(chunk == null) { //表示是小文件，还没有一片
            chunkName = "0.tmp";
        }else {
            chunkName = chunk+ ".tmp";
        }
        //
        String filePath = path + chunkName;
        File savefile = new File(filePath);
        try {
            if (!savefile.exists()) {
                savefile.createNewFile(); // 文件不存在，则创建
            }
            file.transferTo(savefile); //将文件保存
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * @author van
     * 合成分片
     */
    @PostMapping("/merge")
    @ResponseBody
    public JsonResult merge(@RequestParam(value = "chunks",required =false) Integer chunks,
                            @RequestParam(value = "md5File") String md5File,
                            @RequestParam(value = "name") String name) throws Exception {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        //
        String extName = FileUtil.extName(name);

        // 创建日期目录
        File fileDir = new File(uploadDir);
        if(!fileDir.exists()) {
            fileDir.mkdirs();
        }

        //
        FileOutputStream fileOutputStream = new FileOutputStream(fileDir.getAbsolutePath() + "/" + name);  //合成后的文件
        try {
            // 合并文件
            byte[] buf = new byte[1024];
            for(long i=0; i < chunks; i++) {
                String chunkFile= i + ".tmp";
                File file = new File(uploadDir + md5File + "/" + chunkFile);
                InputStream inputStream = new FileInputStream(file);
                int len = 0;
                while((len = inputStream.read(buf))!=-1){
                    fileOutputStream.write(buf,0, len);
                }
                inputStream.close();
            }

            // 删除md5目录，及临时文件
            File file = new File(uploadDir + md5File + "/");
            if(file.exists()) {
                FileUtil.del(file);
            }

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("onnxName", name);
            dataMap.put("onnxMd5", md5File);
            dataMap.put("onnxSize", new File(fileDir.getAbsolutePath() + "/" + name).length());
            operationLogService.record("model:merge", "modelFile=" + name, true, "model chunks merged", "md5=" + md5File);
            return JsonResultUtils.success(dataMap);
        } catch (Exception e) {
            operationLogService.record("model:merge", "modelFile=" + name, false, "model merge failed", e.getMessage());
            e.printStackTrace();
        } finally {
            fileOutputStream.close();
        }
        return JsonResultUtils.fail("文件上传错误，请重新尝试！");
    }

    /**
     * model file rename
     * @param fileName
     * @return
     * @throws Exception
     */
    @PostMapping("/rename")
    @ResponseBody
    public JsonResult fileRename(@RequestParam(value = "fileName") String fileName) throws Exception {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        //
        boolean exist = false;
        String newName = "";
        //
        File file = new File(uploadDir + fileName);
        if(exist = file.exists()) {
            String mainName = FileUtil.mainName(fileName);
            String extName = FileUtil.extName(fileName);
            newName = mainName + "_" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + "." + extName;
            file.renameTo(new File(uploadDir + newName));
        }

        //
        if(!exist) {
            return JsonResultUtils.fail("模型文件不存在");
        }

        //
        Model model = modelService.getByOnnxName(fileName);
        if(model == null) {
            return JsonResultUtils.fail("模型文件记录不存在");
        }

        //
        Model updateModel = new Model();
        updateModel.setId(model.getId());
        updateModel.setOnnxName(newName);
        modelService.updateById(updateModel);
        operationLogService.record("model:rename", "modelId=" + model.getId(), true, "model file renamed", fileName + "->" + newName);
        return JsonResultUtils.success();
    }

    /**
     * parse depend model，output config json
     * @param modelId
     * @return
     * @throws Exception
     */
    @PostMapping("/depend")
    @ResponseBody
    public JsonResult parseDepend(Long modelId) throws Exception {
        List<ModelDepend> modelDependList = this.modelDependService.listByModel(modelId);
        if(modelDependList == null || modelDependList.isEmpty()) {

        } else {
            for(ModelDepend modelDepend : modelDependList) {
                List<ModelDepend> subModelDependList = modelDependService.listByModel(modelDepend.getDependModelId());
            }
        }


        return JsonResultUtils.success();
    }


}
