package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.enums.ReportType;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.ReportService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据统计管理
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
//@SaCheckLogin
@Slf4j
@CrossOrigin
@Controller
@RequestMapping({"/statistic"})
public class StatisticController {

    //
    @Autowired
    private CameraService cameraService;

    //
    @Autowired
    private AlgorithmService algorithmService;

    //
    @Autowired
    private ReportService reportService;

    //
    @Autowired
    private ConfigService configService;

    /**
     *
     * @param modelMap
     * @return
     */
    @GetMapping
    public String index(ModelMap modelMap) {
        String startDate = DateUtil.formatDate(DateUtil.offsetDay(new Date(), -7));
        String endDate = DateUtil.formatDate(new Date());
        modelMap.addAttribute("startDate", startDate);
        modelMap.addAttribute("endDate", endDate);
        modelMap.addAttribute("cameraList", cameraService.listData() == null ? new ArrayList<>() : cameraService.listData());
        modelMap.addAttribute("algorithmList", algorithmService.list() == null ? new ArrayList<>() : algorithmService.list());
        modelMap.addAttribute("typeList", ReportType.toList());
        int cameraCount = 0;
        List<Camera> cameraList = this.cameraService.listData();
        if (cameraList != null) {
            cameraCount = Integer.valueOf(cameraList.size());
        }
        modelMap.addAttribute("cameraCount", cameraCount);
        return "statistic/index";
    }

    /**
     *
     * @return
     */
    @PostMapping({"/camera/count"})
    @ResponseBody
    public JsonResult cameraCount() {
        int cameraCount = 0;
        List<Camera> cameraList = this.cameraService.listData();
        if (cameraList != null) {
            cameraCount = Integer.valueOf(cameraList.size());
        }
        return JsonResultUtils.success(cameraCount);
    }

    /**
     *
     * @param startDate
     * @param endDate
     * @return
     */
    @PostMapping({"/algorithm/ratio"})
    @ResponseBody
    public JsonResult listAlgorithmRatio(String startDate, String endDate, Long cameraId, Long algorithmId, Integer type) {
        Date start = getStartDate(startDate);
        Date end = getEndDate(endDate);
        Long startMills = start == null ? null : start.getTime();
        Long endMills = end == null ? null : end.getTime();
        Map<Long, String> algorithmMap = this.algorithmService.toMap();
        List<Map<String, Object>> dataList = new ArrayList<>();

        List<Long> targetAlgorithmIds = new ArrayList<>();
        if (algorithmId != null) {
            targetAlgorithmIds.add(algorithmId);
        } else {
            targetAlgorithmIds.addAll(algorithmMap.keySet());
        }

        for (Long algorithmIdItem : targetAlgorithmIds) {
            Integer count = reportService.getCount(startMills, endMills, cameraId, algorithmIdItem, type);
            String algorithmName = algorithmMap.get(algorithmIdItem);
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("name", algorithmName == null ? "Unknow" : algorithmName);
            dataMap.put("value", count == null ? 0 : count);
            dataList.add(dataMap);
        }
        return JsonResultUtils.success(dataList);
    }

    /**
     *
     * @param startDate
     * @param endDate
     * @return
     */
    @PostMapping({"/camera"})
    @ResponseBody
    public JsonResult listCamera(String startDate, String endDate, Long cameraId, Long algorithmId, Integer type) {
        Date start = getStartDate(startDate);
        Date end = getEndDate(endDate);
        Long startMills = start == null ? null : start.getTime();
        Long endMills = end == null ? null : end.getTime();

        Map<Long, String> cameraMap = this.cameraService.toMap();
        ArrayList xAxiss = new ArrayList();
        ArrayList values = new ArrayList();

        List<Long> targetCameraIds = new ArrayList<>();
        if (cameraId != null) {
            targetCameraIds.add(cameraId);
        } else {
            targetCameraIds.addAll(cameraMap.keySet());
        }

        for (Long cameraIdItem : targetCameraIds) {
            xAxiss.add(cameraMap.get(cameraIdItem));
            Integer count = reportService.getCount(startMills, endMills, cameraIdItem, algorithmId, type);
            if (count == null) {
                values.add(0);
            } else {
                values.add(count);
            }
        }
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("xAxiss", xAxiss);
        dataMap.put("values", values);
        return JsonResultUtils.success(dataMap);
    }

    /**
     *
     * @param startDate
     * @param endDate
     * @return
     */
    @PostMapping(value = "/camera2algorithm")
    @ResponseBody
    public JsonResult listCameraAlgorithm(String startDate, String endDate, Long cameraId, Long algorithmId, Integer type) {
        Date _startDate = getStartDate(startDate);
        Date _endDate = getEndDate(endDate);
        Long startMills = _startDate == null ? null : _startDate.getTime();
        Long endMills = _endDate == null ? null : _endDate.getTime();

        //
        Map<Long, String> cameraMap = this.cameraService.toMap();
        List<String> cameraNames = new ArrayList();
        List<Long> cameraIds = new ArrayList<>();
        if (cameraId != null) {
            cameraIds.add(cameraId);
        } else {
            cameraIds.addAll(cameraMap.keySet());
        }
        for (Long cameraIdItem : cameraIds) {
            cameraNames.add(cameraMap.get(cameraIdItem));
        }

        //
        Map<Long, String> algorithmMap = this.algorithmService.toMap();
        List<String> algorithmNames = new ArrayList();
        List<Long> algorithmIds = new ArrayList<>();
        if (algorithmId != null) {
            algorithmIds.add(algorithmId);
        } else {
            algorithmIds.addAll(algorithmMap.keySet());
        }
        for (Long algorithmIdItem : algorithmIds) {
            algorithmNames.add(algorithmMap.get(algorithmIdItem));
        }

        //
        List<Map<String, Object>> dataList = new ArrayList();
        for (Long algorithmIdItem : algorithmIds) {

            Map<String, Object> dataMap = new HashMap();
            dataMap.put("name", algorithmMap.get(algorithmIdItem));
            dataMap.put("type", "bar");
            dataMap.put("barGap", 0);
            Map<String, Object> labelMap = new HashMap();
            labelMap.put("show", false);
            labelMap.put("position", "insideBottom");
            labelMap.put("distance", 15);
            labelMap.put("align", "left");
            labelMap.put("verticalAlign", "middle");
            labelMap.put("rotate", 90);
            //labelMap.put("formatter", "{c}  {name|{a}}");
            labelMap.put("fontSize", 14);

            Map<String, Object> richMap = new HashMap<>();
            richMap.put("name", new HashMap<>());
            labelMap.put("rich", richMap);
            dataMap.put("label", labelMap);

            //
            HashMap emphasisMap = new HashMap();
            emphasisMap.put("focus", "series");
            dataMap.put("emphasis", emphasisMap);

            //
            List<Integer> values = new ArrayList();
            for (Long cameraIdItem : cameraIds) {
                Integer count = reportService.getCount(startMills, endMills, cameraIdItem, algorithmIdItem, type);
                values.add(count == null ? 0 : count);
            }
            dataMap.put("data", values);
            dataList.add(dataMap);
        }
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("cameraNames", cameraNames);
        dataMap.put("algorithmNames", algorithmNames);
        dataMap.put("datas", dataList);

        return JsonResultUtils.success(dataMap);
    }

    /**
     * 告警趋势：按天统计告警数量，支持时间/摄像头/算法/类型组合筛选
     */
    @PostMapping("/alarm/trend")
    @ResponseBody
    public JsonResult alarmTrend(String startDate, String endDate, Long cameraId, Long algorithmId, Integer type) {
        Date start = getStartDate(startDate);
        Date end = getEndDate(endDate);
        long cursor = start.getTime();
        long endExclusive = end.getTime();
        long oneDayMs = 24L * 60L * 60L * 1000L;

        List<String> xAxiss = new ArrayList<>();
        List<Integer> values = new ArrayList<>();

        int guard = 0;
        while (cursor < endExclusive && guard < 366) {
            long next = cursor + oneDayMs;
            Integer count = reportService.getCount(cursor, next, cameraId, algorithmId, type);
            xAxiss.add(DateUtil.format(new Date(cursor), "MM-dd"));
            values.add(count == null ? 0 : count);
            cursor = next;
            guard++;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("xAxiss", xAxiss);
        data.put("values", values);
        return JsonResultUtils.success(data);
    }

    /**
     *
     * @param startDate
     * @return
     */
    private Date getStartDate(String startDate) {
        Date _startDate = null;
        try {
            _startDate = DateUtil.parse(startDate, "yyyy-MM-dd");
        } catch (Exception e) {
        }
        if (_startDate == null) {
            _startDate = DateUtil.offsetDay(new Date(), -7);
        }
        return DateUtil.beginOfDay(_startDate);
    }

    /**
     *
     * @param endDate
     * @return
     */
    private Date getEndDate(String endDate) {
        Date _endDate = null;
        try {
            _endDate = DateUtil.parse(endDate, "yyyy-MM-dd");
        } catch (Exception e) {
        }
        if (_endDate == null) {
            _endDate = new Date();
        }
        return DateUtil.beginOfDay(DateUtil.offsetDay(_endDate, 1));
    }


    /**
     * 实时摄像头检测数据： 摄像头总数，摄像头运行总数
     * @return
     */
    @SaIgnore
    @CrossOrigin
    @RequestMapping("/countCamera")
    @ResponseBody
    public JsonResult countCamera() {
        //
        Integer totalCount = cameraService.getCountByRunState(-1);
        Integer runingCount = cameraService.getCountByRunState(1);
        //
        Map<String, Object> resMap = new HashMap<>();
        resMap.put("total", totalCount);
        resMap.put("runing", runingCount);
        //
        return JsonResultUtils.success(resMap);
    }

    /**
     * 系统管理：统计服务器数量、数据集总数、今日推送数据集总数、已标注数据集总数、任务总数、模型训练总数、模型部署数量、模型镜像数量
     * @return
     */
    @SaIgnore
    @CrossOrigin
    @RequestMapping("/countData")
    @ResponseBody
    public JsonResult countData() {
        Map<String, Object> resMap = new HashMap<>();
        resMap.put("server_count", 2);
        resMap.put("dataset_count", 0);
        resMap.put("today_dataset_count", 0);
        resMap.put("mark_dataset_count", 0);
        resMap.put("task_count", 10);
        resMap.put("model_train_count", 20);
        resMap.put("model_deploy_count", 15);
        resMap.put("model_image_count", 20);
        //
        Integer datasetCount = reportService.getCount(null, null, null, null, null);
        Integer todayDatasetCount = reportService.getCount(DateUtil.truncate(new Date(), DateField.DAY_OF_MONTH).getTime(), DateUtil.truncate(DateUtil.offsetDay(new Date(), 1), DateField.DAY_OF_MONTH).getTime(), null, null, null);
        Integer markDatasetCount = reportService.getMarkCount(null, null, null, null);

        //
        resMap.put("dataset_count", datasetCount);
        resMap.put("today_dataset_count", todayDatasetCount);
        resMap.put("mark_dataset_count", markDatasetCount);
        //
        return JsonResultUtils.success(resMap);
    }

    /**
     * 本日累计预警次数: 本日累计预警次数，算法告警次数统计
     * @return
     */
    @SaIgnore
    @CrossOrigin
    @RequestMapping("/countAlgorithm1Day")
    @ResponseBody
    public JsonResult countAlgorithm1Day() {
        Map<String, Object> resMap = new HashMap<>();

        //
        Integer totalCount = reportService.getCount(DateUtil.truncate(new Date(), DateField.DAY_OF_MONTH).getTime(), DateUtil.truncate(DateUtil.offsetDay(new Date(), 1), DateField.DAY_OF_MONTH).getTime(), null, null, null);
        Map<Long, Integer> algorithmStaticsData = reportService.getCountByAlgorithm(DateUtil.truncate(new Date(), DateField.DAY_OF_MONTH).getTime(), DateUtil.truncate(DateUtil.offsetDay(new Date(), 1), DateField.DAY_OF_MONTH).getTime());

        //
        Map<Long, String> algorithmNames = algorithmService.toMap();
        //
        List<Map<String, Object>> chartDatas = new ArrayList<>();
        Iterator<Long> iter = algorithmNames.keySet().iterator();
        while(iter.hasNext()) {
            Long algorithmId = iter.next();
            String algorithmName = algorithmNames.get(algorithmId);
            Integer count = algorithmStaticsData.get(algorithmId);
            //
            Map<String, Object> chartData = new HashMap<>();
            chartData.put("name", algorithmName);
            chartData.put("count", count == null ? 0 : count);
            chartDatas.add(chartData);
        }
        //
        resMap.put("total", totalCount);
        resMap.put("datas", chartDatas);
        return JsonResultUtils.success(resMap);
    }

    /**
     * 本月预警事件前10统计
     * @return
     */
    @SaIgnore
    @CrossOrigin
    @RequestMapping("/countAlgorithm30Day")
    @ResponseBody
    public JsonResult countAlgorithm30Day() {
        //
        Map<Long, Integer> algorithmStaticsData = reportService.getCountByAlgorithm(DateUtil.truncate(DateUtil.offsetDay(new Date(), -30), DateField.DAY_OF_MONTH).getTime(), DateUtil.truncate(DateUtil.offsetDay(new Date(), 1), DateField.HOUR_OF_DAY).getTime());

        //
        Map<Long, String> algorithmNames = algorithmService.toMap();
        //
        int num = 0;
        List<Map<String, Object>> chartDatas = new ArrayList<>();
        Iterator<Long> iter = algorithmNames.keySet().iterator();
        while(iter.hasNext()) {
            Long algorithmId = iter.next();
            String algorithmName = algorithmNames.get(algorithmId);
            Integer count = algorithmStaticsData.get(algorithmId);
            //
            Map<String, Object> chartData = new HashMap<>();
            chartData.put("name", algorithmName);
            chartData.put("count", count == null ? 0 : count);
            chartDatas.add(chartData);

            num++;
            if(num >= 10) {
                break;
            }
        }
        //
        return JsonResultUtils.success(chartDatas);
    }

    /**
     * 活动摄像头列表
     * @return
     */
    @SaIgnore
    @CrossOrigin
    @RequestMapping("/activeCameras")
    @ResponseBody
    public JsonResult activeCameras() {
        //
        List<Camera> cameraList = cameraService.listActives();
        //
        List<Map<String, Object>> dataList = new ArrayList<>();
        //
        for(Camera camera : cameraList) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("name", camera.getName());
            dataMap.put("rtsp_url", camera.getRtspUrl());
            dataList.add(dataMap);
        }

        return JsonResultUtils.success(dataList);
    }

    /**
     * 本周累计预警：告警总数，每日告警次数, 按日期曲线图
     * @return
     */
    @SaIgnore
    @CrossOrigin
    @RequestMapping("/countAlgorithmCount7Day")
    @ResponseBody
    public JsonResult countAlgorithmCount7Day() {
        Map<String, Object> resMap = new HashMap<>();
        //
        Date startDate = DateUtil.truncate(DateUtil.offsetDay(new Date(), -7), DateField.HOUR_OF_DAY);
        Date endDate = DateUtil.truncate(DateUtil.offsetDay(new Date(), -1), DateField.HOUR_OF_DAY); // 不包含今天

        // 总数
        Integer total = reportService.getCount(startDate.getTime(), endDate.getTime(), null, null, null);
        //
        Integer end = Integer.parseInt(DateUtil.format(endDate, "yyyyMMdd"));
        List<Map<String, Object>> dataList = new ArrayList<>();
        while (true) {
            Integer today = Integer.parseInt(DateUtil.format(startDate, "yyyyMMdd"));
            if(today > end) {
                 break;
            }
            //
            Date nextDate = DateUtil.truncate(DateUtil.offsetDay(startDate, 1), DateField.HOUR_OF_DAY);
            Integer count = reportService.getCount(startDate.getTime(), nextDate.getTime(), null, null, null);
            //
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("name", DateUtil.format(startDate, "MM-dd"));
            dataMap.put("count", count);
            dataList.add(dataMap);

            //
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            startDate = calendar.getTime();
        }
        //
        resMap.put("total", total);
        resMap.put("datas", dataList);
        return JsonResultUtils.success(resMap);
    }

    /**
     * 本7天预警：告警总数，每日告警次数, 饼图
     * @return
     */
    @SaIgnore
    @CrossOrigin
    @RequestMapping("/countAlgorithm7Day")
    @ResponseBody
    public JsonResult countAlgorithm7Day() {
        //
        Date startDate = DateUtil.truncate(DateUtil.offsetDay(new Date(), -7), DateField.HOUR_OF_DAY);
        Date endDate = DateUtil.truncate(DateUtil.offsetDay(new Date(), -1), DateField.HOUR_OF_DAY); // 不包含今天

        // 按算法查询近7天的告警统计数据
        Map<Long, Integer> countByAlgorithmMap = reportService.getCountByAlgorithm(startDate.getTime(), endDate.getTime());
        //
        Map<Long, String> algorithmNames = algorithmService.toMap();
        //
        List<Map<String, Object>> dataList = new ArrayList<>();
        Iterator<Long> iter = algorithmNames.keySet().iterator();
        while(iter.hasNext()) {
            Long algorithmId = iter.next();
            String algorithmName = algorithmNames.get(algorithmId);
            //
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("name", algorithmName);
            dataMap.put("count", countByAlgorithmMap.get(algorithmId) == null ? 0 : countByAlgorithmMap.get(algorithmId));
            dataList.add(dataMap);
        }
        return JsonResultUtils.success(dataList);
    }

    /**
     * 配置参数
     * @return
     */
    @SaIgnore
    @CrossOrigin
    @RequestMapping("/countConfig")
    @ResponseBody
    public JsonResult countConfig() {
        String wsUrl = configService.getByValTag("wsUrl");
        String streamUrl = configService.getByValTag("streamUrl");
        String webUrl = configService.getByValTag("webUrl");
        //
        Map<String, String> resMap = new HashMap<>();
        resMap.put("wsUrl", wsUrl);
        resMap.put("streamUrl", streamUrl);
        resMap.put("webUrl", webUrl);
        return JsonResultUtils.success(resMap);
    }

    /**
     * 最近20条告警记录
     * @return
     */
    @SaIgnore
    @CrossOrigin
    @RequestMapping("/countNewly")
    @ResponseBody
    public JsonResult countNewly() {
        //
        String webUrl = configService.getByValTag("webUrl");
        Map<Long, String> cameraNames = cameraService.toMap();
        Map<Long, String> algorithmNames = algorithmService.toMap();

        // 默认查询当日最近3条告警
        List<Map<String, Object>> dataList = new ArrayList<>();
        List<Report> reportList = reportService.listNewly(20);
        if(reportList != null) {
            for(Report report : reportList) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("id", report.getId());
                dataMap.put("params", report.getParams());
                dataMap.put("cameraName", cameraNames.get(report.getCameraId()));
                dataMap.put("algorithmName", algorithmNames.get(report.getAlgorithmId()));
                dataMap.put("wareName", "");
                dataMap.put("alarmTime", (report.getCreatedAt() == null) ? "" : DateUtil.format(report.getCreatedAt(), "MM/dd HH:mm"));
                dataMap.put("webUrl", webUrl);
                dataList.add(dataMap);
            }
        }
        return JsonResultUtils.success(dataList);
    }
}
