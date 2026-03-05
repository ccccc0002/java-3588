package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.entity.VideoPlay;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.MediaStreamUrlService;
import com.yihecode.camera.ai.service.ReportService;
import com.yihecode.camera.ai.service.VideoPlayService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SaCheckLogin
@Controller
@RequestMapping({"/stream"})
public class StreamController {

    @Autowired
    private CameraService cameraService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private AlgorithmService algorithmService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private VideoPlayService videoPlayService;

    @Autowired
    private MediaStreamUrlService mediaStreamUrlService;

    @GetMapping({"", "/"})
    public String index(ModelMap modelMap) {
        String streamType = configService.getByValTag("streamType");
        String streamUrl = configService.getByValTag("streamUrl");
        modelMap.addAttribute("streamUrl", streamUrl);
        modelMap.addAttribute("wsUrl", configService.getByValTag("wsUrl"));
        modelMap.addAttribute("uid", IdUtil.fastSimpleUUID());

        long startMills = DateUtil.truncate(new Date(), DateField.DAY_OF_MONTH).getTime();
        long endMills = DateUtil.truncate(DateUtil.offsetDay(new Date(), 1), DateField.DAY_OF_MONTH).getTime();
        int counter = reportService.getCounter(startMills, endMills);
        modelMap.addAttribute("counter", counter);

        List<VideoPlay> videoPlays = videoPlayService.list();
        if (videoPlays == null) {
            videoPlays = new ArrayList<>();
        }

        int cameraSize = videoPlays.size();
        modelMap.addAttribute("showNum", cameraSize <= 1 ? 1 : 4);

        Map<Long, String> cameraNames = cameraService.toMap();
        Map<Long, String> algorithmNames = algorithmService.toMap();
        List<Report> reportList = reportService.listNewly(3);
        if (reportList != null) {
            List<Map<String, Object>> dataList = new ArrayList<>();
            for (Report report : reportList) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("id", report.getId());
                dataMap.put("params", report.getParams());
                dataMap.put("cameraName", cameraNames.get(report.getCameraId()));
                dataMap.put("algorithmName", algorithmNames.get(report.getAlgorithmId()));
                dataMap.put("wareName", "");
                dataMap.put("alarmTime", (report.getCreatedAt() == null) ? "" : DateUtil.format(report.getCreatedAt(), "MM/dd HH:mm"));
                dataList.add(dataMap);
            }
            modelMap.addAttribute("reportList", dataList);
        }

        if (StrUtil.isBlank(streamType)) {
            return "stream/index_tj";
        }
        return "stream/index";
    }

    @GetMapping({"/form"})
    public String form(String id, ModelMap modelMap) {
        List<Map<String, Object>> dataList = new ArrayList<>();

        String streamType = configService.getByValTag("streamType");
        if (StrUtil.isBlank(streamType)) {
            List<Camera> cameraList = this.cameraService.listData();
            if (cameraList != null) {
                for (Camera camera : cameraList) {
                    Map<String, Object> dataMap = new HashMap<>();
                    dataMap.put("id", camera.getId());
                    dataMap.put("name", (camera == null) ? "unknown" : camera.getName());
                    dataList.add(dataMap);
                }
            }
        } else {
            List<VideoPlay> videoPlays = videoPlayService.list();
            if (videoPlays != null) {
                for (VideoPlay videoPlay : videoPlays) {
                    Camera camera = cameraService.getById(videoPlay.getCameraId());
                    Map<String, Object> dataMap = new HashMap<>();
                    dataMap.put("id", videoPlay.getCameraId());
                    dataMap.put("name", (camera == null) ? "unknown" : camera.getName());
                    dataList.add(dataMap);
                }
            }
        }

        modelMap.addAttribute("cameraList", dataList);
        modelMap.addAttribute("id", id);
        return "stream/form";
    }

    @GetMapping("/v2")
    public String indexv2(ModelMap modelMap) {
        String streamUrl = configService.getByValTag("streamUrl");
        modelMap.addAttribute("streamUrl", streamUrl);
        modelMap.addAttribute("cameraList", cameraService.listData());
        modelMap.addAttribute("uid", IdUtil.fastSimpleUUID());
        modelMap.addAttribute("wsUrl", configService.getByValTag("wsUrl"));

        long startMills = DateUtil.truncate(new Date(), DateField.DAY_OF_MONTH).getTime();
        long endMills = DateUtil.truncate(DateUtil.offsetDay(new Date(), 1), DateField.DAY_OF_MONTH).getTime();
        int counter = reportService.getCounter(startMills, endMills);
        modelMap.addAttribute("counter", counter);

        return "stream2/index";
    }

    @GetMapping({"/formConfig"})
    public String formConfig(String id, ModelMap modelMap) {
        List<Algorithm> algorithmList = algorithmService.listUsed();
        if (algorithmList == null) {
            algorithmList = new ArrayList<>();
        }

        for (Algorithm algorithm : algorithmList) {
            if (algorithm.getStaticsFlag() != null && algorithm.getStaticsFlag() == 1) {
                algorithm.setStaticsFlagVal("checked");
            } else {
                algorithm.setStaticsFlagVal("");
            }
        }
        modelMap.addAttribute("algorithmList", algorithmList);
        return "stream/form_config";
    }

    @PostMapping({"/formConfig"})
    @ResponseBody
    public JsonResult formConfig(String ids) {
        JSONArray array = JSON.parseArray(ids);
        int len = array.size();
        if (len > 8) {
            return JsonResultUtils.fail("max 8 selections supported");
        }

        List<Long> idList = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            idList.add(array.getLongValue(i));
        }
        algorithmService.updateStaticsFlag(idList);
        return JsonResultUtils.success();
    }

    @PostMapping({"/statics/algorithms"})
    @ResponseBody
    public JsonResult staticsAlgorithms() {
        List<Algorithm> algorithmList = algorithmService.listUsed();
        if (algorithmList == null) {
            algorithmList = new ArrayList<>();
        }

        long startMills = DateUtil.truncate(new Date(), DateField.DAY_OF_MONTH).getTime();
        long endMills = DateUtil.truncate(DateUtil.offsetDay(new Date(), 1), DateField.DAY_OF_MONTH).getTime();

        List<Algorithm> showAlgorithmList = new ArrayList<>();
        for (Algorithm algorithm : algorithmList) {
            if (algorithm.getStaticsFlag() != null && algorithm.getStaticsFlag() == 1) {
                Integer counter = reportService.getAlgorithmCounter(algorithm.getId(), startMills, endMills);
                algorithm.setStaticsFlagVal(String.valueOf(counter));
                showAlgorithmList.add(algorithm);
            }
        }
        return JsonResultUtils.success(showAlgorithmList);
    }

    @GetMapping("/select_play")
    public String selectPlayPage(ModelMap modelMap) {
        String videoPorts = configService.getByValTag("video_ports");
        List<Map<String, String>> dataList = new ArrayList<>();
        if (StrUtil.isNotBlank(videoPorts)) {
            String[] videoPortsArr = videoPorts.split(",");
            for (String videoPort : videoPortsArr) {
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put("videoPort", videoPort);
                dataMap.put("videoUrl", mediaStreamUrlService.buildPlayUrl(null, Convert.toInt(videoPort, 0)));
                dataList.add(dataMap);
            }
        }
        modelMap.addAttribute("videoUrls", dataList);
        return "stream/select_play";
    }

    @PostMapping("/play_list")
    @ResponseBody
    public PageResult playList() {
        String traceId = IdUtil.fastSimpleUUID();
        List<VideoPlay> videoPlays = videoPlayService.list();
        List<Map<String, Object>> dataList = new ArrayList<>();
        if (videoPlays != null) {
            for (VideoPlay videoPlay : videoPlays) {
                Camera camera = cameraService.getById(videoPlay.getCameraId());
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("trace_id", traceId);
                dataMap.put("cameraId", videoPlay.getCameraId());
                dataMap.put("playUrl", mediaStreamUrlService.buildPlayUrl(camera, videoPlay.getVideoPort()));
                if (camera == null || StrUtil.isBlank(camera.getName())) {
                    dataMap.put("cameraName", "unknown");
                } else {
                    dataMap.put("cameraName", camera.getName());
                }
                dataList.add(dataMap);
            }
        }
        return PageResultUtils.success(null, dataList);
    }

    @PostMapping("/camera_list")
    @ResponseBody
    public PageResult cameraList(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit) {
        String traceId = IdUtil.fastSimpleUUID();
        List<VideoPlay> videoPlays = videoPlayService.list();
        Map<Long, Integer> videoPortMap = new HashMap<>();
        if (videoPlays != null) {
            for (VideoPlay videoPlay : videoPlays) {
                videoPortMap.put(videoPlay.getCameraId(), videoPlay.getVideoPort());
            }
        }

        IPage<Camera> pageResult = cameraService.listPageAndOrderVideoPlay(new Page<>(page, limit));
        List<Map<String, Object>> dataList = new ArrayList<>();
        if (pageResult.getRecords() != null) {
            for (Camera camera : pageResult.getRecords()) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("trace_id", traceId);
                dataMap.put("cameraId", camera.getId());
                dataMap.put("cameraName", camera.getName());
                if (videoPortMap.containsKey(camera.getId())) {
                    dataMap.put("playUrl", mediaStreamUrlService.buildPlayUrl(camera, videoPortMap.get(camera.getId())));
                } else {
                    dataMap.put("playUrl", "");
                }
                dataList.add(dataMap);
            }
        }
        return PageResultUtils.success(pageResult.getTotal(), dataList);
    }

    @PostMapping("/stop")
    @ResponseBody
    public JsonResult stopStream(Long cameraId) {
        if (cameraId == null) {
            return JsonResultUtils.fail("cameraId is required");
        }
        String traceId = IdUtil.fastSimpleUUID();
        boolean zlmMode = mediaStreamUrlService.isZlmMode();
        if (zlmMode) {
            cameraService.updatePlay(cameraId, 0);
        } else {
            cameraService.updateVideoPlay(cameraId, 0, 0);
        }
        Map<String, Object> resMap = new HashMap<>();
        resMap.put("trace_id", traceId);
        resMap.put("cameraId", cameraId);
        resMap.put("mode", zlmMode ? "zlm" : "legacy");
        return JsonResultUtils.success(resMap);
    }

    @PostMapping("/start")
    @ResponseBody
    public JsonResult startStream(Long cameraId, Integer videoPort) {
        if (cameraId == null) {
            return JsonResultUtils.fail("cameraId is required");
        }
        String traceId = IdUtil.fastSimpleUUID();
        boolean zlmMode = mediaStreamUrlService.isZlmMode();

        if (!zlmMode && videoPort == null) {
            return JsonResultUtils.fail("videoPort is required in legacy mode");
        }

        if (zlmMode && (videoPort == null || videoPort <= 0)) {
            boolean useOk = cameraService.updatePlay(cameraId, 1);
            if (!useOk) {
                return JsonResultUtils.fail("no available play address");
            }
        } else {
            Map<String, Object> retMap = cameraService.updateVideoPlay(cameraId, 1, videoPort);
            if (Convert.toInt(retMap.get("code"), 0) != 200) {
                return JsonResultUtils.fail(Convert.toStr(retMap.get("msg")));
            }
        }

        VideoPlay currentPlay = videoPlayService.getByCamera(cameraId);
        Camera camera = cameraService.getById(cameraId);
        Integer currentPort = (currentPlay == null) ? videoPort : currentPlay.getVideoPort();
        Map<String, Object> resMap = new HashMap<>();
        resMap.put("trace_id", traceId);
        resMap.put("cameraId", cameraId);
        resMap.put("mode", zlmMode ? "zlm" : "legacy");
        resMap.put("videoPort", currentPort);
        resMap.put("playUrl", mediaStreamUrlService.buildPlayUrl(camera, currentPort));
        return JsonResultUtils.success(resMap);
    }
}
