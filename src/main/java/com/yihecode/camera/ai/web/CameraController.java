package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yihecode.camera.ai.entity.*;
import com.yihecode.camera.ai.enums.CameraAction;
import com.yihecode.camera.ai.enums.CameraRunningState;
import com.yihecode.camera.ai.enums.CommState;
import com.yihecode.camera.ai.javacv.TakePhoto;
import com.yihecode.camera.ai.service.*;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 摄像头管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Controller
@RequestMapping({"/camera"})
public class CameraController {

    //
    @Autowired
    private CameraService cameraService;

    //
    @Autowired
    private AlgorithmService algorithmService;

    //
    @Autowired
    private ReportPeriodService reportPeriodService;

    //
    @Autowired
    private CameraAlgorithmService cameraAlgorithmService;

    //
    @Autowired
    private WareHouseService wareHouseService;

    //
    @Autowired
    private LocationService locationService;

    //
    @Autowired
    private VideoPlayService videoPlayService;

    //
    @Autowired
    private TakePhoto takePhoto;

    //
    @Autowired
    private ConfigService configService;

    @Autowired
    private MediaStreamUrlService mediaStreamUrlService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 打开摄像头管理页面
     * @return
     */
    @GetMapping({"", "/"})
    public String index() {
        return "camera/index";
    }

    /**
     * 打开摄像头表单页面
     * @param id
     * @param modelMap
     * @return
     */
    @GetMapping({"/form"})
    public String form(Long id, Long locationId, ModelMap modelMap) {
        if (id == null) {
            Camera camera = new Camera();
            camera.setLocationId(locationId);
            modelMap.addAttribute("camera", camera);
        } else {
            modelMap.addAttribute("camera", this.cameraService.getById(id));
        }
        return "camera/form";
    }

    /**
     * 打开新增摄像头页面，版本2
     * @param locationId
     * @param modelMap
     * @return
     */
    @GetMapping({"/newForm"})
    public String newForm(Long locationId, ModelMap modelMap) {
        Camera camera = new Camera();
        camera.setLocationId(locationId);
        modelMap.addAttribute("camera", camera);
        return "camera/form";
    }

    /**
     * 摄像头详情
     * @param id
     * @return
     */
    @PostMapping({"/detail"})
    @ResponseBody
    public JsonResult detail(Long id) {
        Camera camera = cameraService.getById(id);
        if(camera == null) {
            return JsonResultUtils.fail("找不到数据");
        }
        return JsonResultUtils.success(camera);
    }

    /**
     * 查询数据列表
     * @return
     */
    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData() {
        List<Camera> cameraList = this.cameraService.list();
        if (cameraList == null) {
            cameraList = new ArrayList<>();
        }

        //
        List<Algorithm> algorithmList = algorithmService.list();
        if(algorithmList == null) {
            algorithmList = new ArrayList<>();
        }

        //
        Map<Long, String> algorithmNames = new HashMap<>();
        for(Algorithm algorithm : algorithmList) {
            algorithmNames.put(algorithm.getId(), algorithm.getName());
        }

        //
        List<Camera> dataList = new ArrayList<>();
        for (Camera camera : cameraList) {
            if(camera.getState() == null || camera.getState() != 0) {
                continue;
            }

            //
            List<String> nameList = new ArrayList<>();
            List<CameraAlgorithm> cameraAlgorithmList = cameraAlgorithmService.listByCamera(camera.getId());
            for(CameraAlgorithm cameraAlgorithm : cameraAlgorithmList) {
                //
                String algorithmName = algorithmNames.get(cameraAlgorithm.getAlgorithmId());
                if(algorithmName == null) {
                    continue;
                }

                //
                List<String> periods = new ArrayList<>();
                List<ReportPeriod> reportPeriodList = reportPeriodService.listData(camera.getId(), cameraAlgorithm.getAlgorithmId());
                for(ReportPeriod reportPeriod : reportPeriodList) {
                    periods.add(reportPeriod.getStartText() + "-" + reportPeriod.getEndText());
                }

                nameList.add(algorithmName + "(" + String.join(" , ", periods) + ")");
            }
//            camera.setAlgorithmNames(String.join(" | ", nameList));
            camera.setAlgorithmNames(nameList.size() + " 个");

            //camera.setAlgorithmNames(this.cameraAlgorithmService.getNames(camera.getId()));
            dataList.add(camera);
        }
        return PageResultUtils.success(null, dataList);
    }

    /**
     * 分页查询数据列表
     * @param page
     * @param limit
     * @param name
     * @param locationId
     * @return
     */
    @PostMapping("listPage")
    @ResponseBody
    public PageResult listPage(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit, String name, Long locationId) {
        IPage<Camera> pageObj = new Page<>(page, limit);
        Camera queryCamera = new Camera();
        queryCamera.setName(name);
        queryCamera.setLocationId(locationId);
        IPage<Camera> pageResult = cameraService.listPage(pageObj, queryCamera);

        //
        List<Camera> records = pageResult.getRecords();
        if(records == null) {
            records = new ArrayList<>();
        }

        //
        List<Algorithm> algorithmList = algorithmService.list();
        if(algorithmList == null) {
            algorithmList = new ArrayList<>();
        }

        //
        Map<Long, String> algorithmNames = new HashMap<>();
        for(Algorithm algorithm : algorithmList) {
            algorithmNames.put(algorithm.getId(), algorithm.getName());
        }

        //
        List<Camera> dataList = new ArrayList<>();
        for (Camera camera : records) {
            //
            if(camera.getState() == null || camera.getState() != 0) {
                continue;
            }

            // 数据转换一次
            if(camera.getRtspType() == null) {
                camera.setRtspType(0);
            }

            //
            List<String> nameList = new ArrayList<>();
            List<CameraAlgorithm> cameraAlgorithmList = cameraAlgorithmService.listByCamera(camera.getId());
            for(CameraAlgorithm cameraAlgorithm : cameraAlgorithmList) {
                //
                String algorithmName = algorithmNames.get(cameraAlgorithm.getAlgorithmId());
                if(algorithmName == null) {
                    continue;
                }

                //
                List<String> periods = new ArrayList<>();
                List<ReportPeriod> reportPeriodList = reportPeriodService.listData(camera.getId(), cameraAlgorithm.getAlgorithmId());
                for(ReportPeriod reportPeriod : reportPeriodList) {
                    periods.add(reportPeriod.getStartText() + "-" + reportPeriod.getEndText());
                }

                nameList.add(algorithmName + "(" + String.join(" , ", periods) + ")");
            }
            //camera.setAlgorithmNames(String.join(" | ", nameList));
            camera.setAlgorithmNames(nameList.size() + " 个");

            //camera.setAlgorithmNames(this.cameraAlgorithmService.getNames(camera.getId()));
            dataList.add(camera);
        }
        return PageResultUtils.success(pageResult.getTotal(), dataList);
    }

    /**
     * 保存数据
     * @param camera
     * @param algorithmvos
     * @return
     */
    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(Camera camera, String algorithmvos, String confidencevos, String markpointsvos, Integer updatePoint) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        if (StrUtil.isBlank(camera.getName())) {
            return JsonResultUtils.fail("请输入摄像头名称");
        }
        if (StrUtil.isBlank(camera.getRtspUrl())) {
            return JsonResultUtils.fail("请输入摄像头RTSP流");
        }
        if(StrUtil.isBlank(algorithmvos)) {
            return JsonResultUtils.fail("请至少选择一项算法");
        }
        if(camera.getIntervalTime() == null || camera.getIntervalTime() <= 0) {
            return JsonResultUtils.fail("请输入告警时间间隔(秒)");
        }
        /* remove 已关联到算法
        if(StrUtil.isNotBlank(camera.getParams())) {
            if(StrUtil.isBlank(camera.getFileName())) {
                return JsonResultUtils.fail("请拍照取图");
            }
        }
         */
        if (camera.getId() == null) {
            if (isChannelLimitExceeded()) {
                return JsonResultUtils.fail("Camera channel limit exceeded by license");
            }

            camera.setState(CommState.NORMAL.getType());
            camera.setRunning(CameraRunningState.CLOSED.getType());
            camera.setAction(CameraAction.ACTION_UPD.getType());
            camera.setCreatedAt(new Date());

            // 区域设置
            if(camera.getId() == null) {
                Location location = locationService.getById(camera.getLocationId());
                if(location != null) {
                    camera.setLocationIds(location.getParentIds() + "/" + location.getId());
                }
            }
        }
        camera.setUpdatedAt(new Date());
        this.cameraService.saveCamera(camera, algorithmvos, confidencevos, markpointsvos, updatePoint);
        operationLogService.record("camera:save", "cameraId=" + camera.getId(), true, "camera saved", camera.getName());
        return JsonResultUtils.success();
    }

    /**
     * 删除摄像头
     * @param id
     * @return
     */
    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            return JsonResultUtils.fail("permission denied");
        }
        this.cameraService.delete(id);
        operationLogService.record("camera:delete", "cameraId=" + id, true, "camera deleted", "");
        return JsonResultUtils.success();
    }

    /**
     * 查询运行中的摄像头列表
     * @return
     */
    @PostMapping({"/running"})
    @ResponseBody
    public JsonResult listRunning() {
        List<Camera> cameraList = this.cameraService.list();
        if (cameraList == null) {
            cameraList = new ArrayList<>();
        }

        //
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Camera camera : cameraList) {
            if(camera.getState() == null || camera.getState() != 0) {
                continue;
            }

            //
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("id", camera.getId());
            dataMap.put("running", camera.getRunning());
            dataList.add(dataMap);
        }
        return JsonResultUtils.success(dataList);
    }

    /**
     * 摄像头拍照取图
     * @return
     */
    @RequestMapping({"/takePhoto"})
    @ResponseBody
    public JsonResult takePhoto(String rtspUrl) {
        if(StrUtil.isBlank(rtspUrl)) {
            return JsonResultUtils.fail("请输入视频流地址");
        }
//        if(rtspUrl.toLowerCase().indexOf("rtsp://") < 0) {
//            return JsonResultUtils.fail("视频流地址仅支持rtsp协议");
//        }

        //
        String fileName = takePhoto.take(rtspUrl, resolveFfmpegBin());
        if(StrUtil.isBlank(fileName)) {
            return JsonResultUtils.fail("拍照失败，请确保视频流正常并重新尝试");
        }
        return JsonResultUtils.success(fileName);
    }

    /**
     * 切换摄像头运行状态
     * @param id
     * @return
     */
    @RequestMapping({"/switchRunning"})
    @ResponseBody
    public JsonResult switchRunning(Long id) {
        Camera camera = cameraService.getById(id);
        if(camera == null) {
            return JsonResultUtils.fail("找不到摄像头");
        }

        //
        Integer running = camera.getRunning();
        if(running == null) {
            running = 0;
        }

        //
        cameraService.updateRunning(id, running == 0 ? 1 : 0);

        return JsonResultUtils.success();
    }

    /**
     * 切换视频流类型 0-实时视频流 1-备份回放视频流 2-图片地址
     * @param id
     * @return
     */
    @RequestMapping({"/switchRtspType"})
    @ResponseBody
    public JsonResult switchRtspType(Long id, Integer rtspType) {
        if(id == null) {
            return JsonResultUtils.fail("找不到摄像头");
        }

        //
        if(rtspType == null) {
            return JsonResultUtils.fail("没有指定视频流类型");
        }

        //
        Camera camera = cameraService.getById(id);
        if(camera == null) {
            return JsonResultUtils.fail("找不到摄像头");
        }

        //
        cameraService.updateRtspType(id, rtspType);

        return JsonResultUtils.success();
    }

    /**
     * 修改摄像头地址，适配中化定制需求
     * @param id
     * @return
     */
    @RequestMapping({"/updateRtsp"})
    @ResponseBody
    public JsonResult updateRtsp(Long id) {
        Camera camera = cameraService.getById(id);
        if(camera == null) {
            return JsonResultUtils.fail("找不到摄像头");
        }

        //
        if(camera.getWareHouseId() == null || camera.getWareHouseId() == 0) {
            return JsonResultUtils.fail("摄像头仅支持手动更新地址");
        }

        //
        WareHouse wareHouse = wareHouseService.getById(camera.getWareHouseId());
        if(wareHouse == null || StrUtil.isBlank(wareHouse.getIndexCode())) {
            return JsonResultUtils.fail("配置错误，请手动更新");
        }

        //
        return JsonResultUtils.success("");
    }

    /**
     * 获取最新rtsp地址，适配中化定制需求
     * @param id
     * @return
     */
    @RequestMapping({"/rtspUrl"})
    @ResponseBody
    public JsonResult getRtspUrl(Long id) {
        Camera camera = cameraService.getById(id);
        if(camera == null) {
            return JsonResultUtils.fail("找不到摄像头");
        }

        //
        if(camera.getWareHouseId() == null || camera.getWareHouseId() == 0) {
            if(StrUtil.isBlank(camera.getRtspUrl())) {
                return JsonResultUtils.fail("没有配置RTSP视频流地址");
            }
            return JsonResultUtils.success(camera.getRtspUrl());
        }

        //
        WareHouse wareHouse = wareHouseService.getById(camera.getWareHouseId());
        if(wareHouse == null || StrUtil.isBlank(wareHouse.getIndexCode())) {
            return JsonResultUtils.fail("没有找到基地配置信息");
        }

        //
        return JsonResultUtils.success("");
    }

    /**
     * 查询摄像头区域列表
     * @param id
     * @return
     */
    @RequestMapping({"/cate"})
    @ResponseBody
    public JsonResult getCate(Long id) {
        Camera camera = cameraService.getById(id);
        if(camera == null) {
            return JsonResultUtils.fail("找不到摄像头");
        }

        //
        if(camera.getWareHouseId() == null || camera.getWareHouseId() == 0) {
            return JsonResultUtils.success(camera.getName());
        }

        //
        WareHouse wareHouse = wareHouseService.getById(camera.getWareHouseId());
        if(wareHouse == null || StrUtil.isBlank(wareHouse.getIndexCode())) {
            return JsonResultUtils.fail("没有找到基地配置信息");
        }

        //
        List<String> nameList = new ArrayList<>();

        //
        String indexCode = wareHouse.getIndexCode();

        while(true) {
            WareHouse wareHouse1 = wareHouseService.getByIndexCode(indexCode);

            //
            if(wareHouse1 == null) {
                break;
            }

            //
            nameList.add(wareHouse1.getName());

            //
            indexCode = wareHouse1.getParentIndexCode();

            //
            if(wareHouse1.getTreeLevel() == 1) {
                break;
            }
        }

        //
        Collections.reverse(nameList);

        //
        String cate = String.join(" / ", nameList);

        //
        return JsonResultUtils.success(cate);
    }

    /**
     * 查询活动的摄像头
     * @return
     */
    @PostMapping({"/actives"})
    @ResponseBody
    public JsonResult listActives() {
        //
        List<Map<String, Object>> dataList = new ArrayList<>();

        //
        String streamType = configService.getByValTag("streamType");
        if(StrUtil.isBlank(streamType)) {
            // java推流
            List<Camera> cameraList = cameraService.listActives();
            if(cameraList == null) {
                cameraList = new ArrayList<>();
            }
            Collections.shuffle(cameraList);
            //
            for(Camera camera : cameraList) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("id", camera.getId());
                dataList.add(dataMap);
            }
        } else {
            // 算法推流
            List<VideoPlay> videoPlays = videoPlayService.list();
            if(videoPlays != null) {
                for(VideoPlay videoPlay : videoPlays) {
                    Map<String, Object> dataMap = new HashMap<>();
                    dataMap.put("id", videoPlay.getCameraId());
                    dataList.add(dataMap);
                }
            }
        }
        return JsonResultUtils.success(dataList);
    }

    /**
     * 查询活动的摄像头
     * @return
     */
    @PostMapping({"/shuffle_actives"})
    @ResponseBody
    public JsonResult shuffleActives() {
        List<Camera> cameraList = cameraService.listActives();
        if(cameraList == null) {
            cameraList = new ArrayList<>();
        }
        Collections.shuffle(cameraList);
        return JsonResultUtils.success(cameraList);
    }

    /**
     * 通知算法推送视频流
     * @param cameraId 摄像头id
     * @param videoPlay 播放状态 0-停止 1-播放
     * @return
     */
    @PostMapping({"/play"})
    @ResponseBody
    public JsonResult play(Long cameraId, @RequestParam(defaultValue = "0") Integer videoPlay) {
        Camera camera = cameraService.getById(cameraId);
        if(camera == null || camera.getState() == null || camera.getState() != 0) {
            return JsonResultUtils.fail("摄像头不存在或已删除");
        }

        //
        boolean useOk = cameraService.updatePlay(cameraId, videoPlay);
        if(!useOk) {
            return JsonResultUtils.fail("视频播放已达到最大播放路数，请尝试关闭其他");
        }
        // 等待10算法预备播放
        try {
            Thread.sleep(10000);
        } catch (Exception e) {}

        // 如果是请求播放，返回端口号
        int videoPort = 0;
        if(videoPlay == 1) {
            VideoPlay videoPlay1 = videoPlayService.getByCamera(cameraId);
            if(videoPlay1 != null && videoPlay1.getVideoPort() != null) {
                videoPort = videoPlay1.getVideoPort();
            }
        }

        return JsonResultUtils.success(videoPort);
    }

    /**
     * 每隔5秒上报正在播放的摄像头
     * @param cameraIds
     * @return
     */
    @PostMapping({"/refreshVideoPlay"})
    @ResponseBody
    public JsonResult refreshVideoPlay(String cameraIds) {
        if(StrUtil.isBlank(cameraIds)) {
            return JsonResultUtils.success();
        }
        String[] uCameraIds = cameraIds.split(",");
        if(uCameraIds == null) {
            return JsonResultUtils.success();
        }
        //
        List<Long> playCameraIds = new ArrayList<>();
        for(String cameraId : uCameraIds) {
            playCameraIds.add(Long.parseLong(cameraId));
        }
        //
        cameraService.updateVideoPlays(playCameraIds);
        return JsonResultUtils.success();
    }

    /**
     * 选择播放摄像头
     * @param cameraId
     * @return
     */
    @PostMapping({"/selectPlay"})
    @ResponseBody
    public JsonResult selectPlay(Long cameraId) {
        if(cameraId == null) {
            return JsonResultUtils.fail("未指定摄像头");
        }
        VideoPlay videoPlay = videoPlayService.getByCamera(cameraId);
        if(videoPlay == null) {
            // Try to allocate a playable slot automatically for smoother zlm/legacy compatibility.
            boolean useOk = cameraService.updatePlay(cameraId, 1);
            if(useOk) {
                videoPlay = videoPlayService.getByCamera(cameraId);
            }
        }
        if(videoPlay == null) {
            return JsonResultUtils.fail("该摄像头没有配置视频播放地址");
        }
        Camera camera = cameraService.getById(cameraId);
        Map<String, Object> resMap = new HashMap<>();
        resMap.put("trace_id", UUID.randomUUID().toString().replace("-", ""));
        resMap.put("cameraId", cameraId);
        resMap.put("videoPort", videoPlay.getVideoPort());
        resMap.put("playUrl", mediaStreamUrlService.buildPlayUrl(camera, videoPlay.getVideoPort()));
        resMap.put("zlmMode", mediaStreamUrlService.isZlmMode() ? 1 : 0);
        return JsonResultUtils.success(resMap);
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isChannelLimitExceeded() {
        String maxChannelsRaw = configService.getByValTag("license_max_channels");
        if (StrUtil.isBlank(maxChannelsRaw)) {
            return false;
        }
        try {
            int maxChannels = Integer.parseInt(maxChannelsRaw.trim());
            if (maxChannels <= 0) {
                return false;
            }
            List<Camera> activeCameras = cameraService.listData();
            int currentCount = activeCameras == null ? 0 : activeCameras.size();
            return currentCount >= maxChannels;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String resolveFfmpegBin() {
        String ffmpegBin = configService.getByValTag("media_ffmpeg_bin");
        return StrUtil.isBlank(ffmpegBin) ? "ffmpeg" : ffmpegBin;
    }

}
