package com.yihecode.camera.ai.web;


import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.entity.WareHouse;
import com.yihecode.camera.ai.enums.ReportType;
import com.yihecode.camera.ai.service.*;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import com.yihecode.camera.ai.vo.ReportMessage;
import com.yihecode.camera.ai.web.api.ReportPushService;
import com.yihecode.camera.ai.websocket.ReportWebsocket;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;

/**
 * 告警管理，查询/展示/审核等
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Slf4j
@Controller
@RequestMapping({"/report"})
public class ReportController {

    //
    @Autowired
    private ReportService reportService;

    //
    @Autowired
    private CameraService cameraService;

    //
    @Autowired
    private AlgorithmService algorithmService;

    //
    @Autowired
    private ConfigService configService;

    //
    @Autowired
    private ReportPushService reportPushService;

    //
    @Autowired
    private WareHouseService wareHouseService;

    @Autowired
    private ReportWebsocket reportWebsocket;

    @Value("${uploadDir}")
    private String uploadDir;

    /**
     *
     * @param modelMap
     * @return
     */
    @GetMapping({"", "/"})
    public String index(ModelMap modelMap) {
        List<Camera> cameraList = cameraService.listData();
        modelMap.addAttribute("cameraList", cameraList == null ? new ArrayList<>() : cameraList);
        List<Algorithm> algorithmList = algorithmService.list();
        modelMap.addAttribute("algorithmList", algorithmList == null ? new ArrayList<>() : algorithmList);
        modelMap.addAttribute("typeList", ReportType.toList());

        return "report/index";
    }

    /**
     *
     * @param modelMap
     * @return
     */
    @GetMapping("/discard")
    public String discard(ModelMap modelMap) {
        List<Camera> cameraList = cameraService.list();
        modelMap.addAttribute("cameraList", cameraList == null ? new ArrayList<>() : cameraList);
        List<Algorithm> algorithmList = algorithmService.list();
        modelMap.addAttribute("algorithmList", algorithmList == null ? new ArrayList<>() : algorithmList);
        modelMap.addAttribute("typeList", ReportType.toList());
        return "report/discard";
    }

    /**
     *
     * @return
     */
    @PostMapping({"/reportTypes"})
    @ResponseBody
    public JsonResult reportTypeList() {
        return JsonResultUtils.success(ReportType.toList());
    }

    /**
     *
     * @param page
     * @param limit
     * @param cameraId
     * @param algorithmId
     * @param type
     * @return
     */
    @PostMapping({"/listPage"})
    @ResponseBody
    public PageResult listPage(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit, Long cameraId, Long algorithmId, Integer type) {
        Report reportQuery = new Report();
        reportQuery.setCameraId(cameraId);
        reportQuery.setAlgorithmId(algorithmId);
        reportQuery.setType(type);
        IPage<Report> pageResult = this.reportService.listPage(new Page<>(page, limit), reportQuery);
        List<Report> reportList = pageResult.getRecords();
        if (reportList == null) {
            reportList = new ArrayList<>();
        }
        Map<Long, String> cameraNames = new HashMap<>();
        List<Camera> cameraList = this.cameraService.list();
        if (cameraList != null) {
            for (Camera camera : cameraList) {
                cameraNames.put(camera.getId(), camera.getName());
            }
        }
        Map<Long, String> algorithmNames = new HashMap<>();
        List<Algorithm> algorithmList = this.algorithmService.list();
        if (algorithmList != null) {
            for (Algorithm algorithm : algorithmList) {
                algorithmNames.put(algorithm.getId(), algorithm.getName());
            }
        }
        for (Report report : reportList) {
            String cameraName = cameraNames.get(report.getCameraId());
            String algorithmName = algorithmNames.get(report.getAlgorithmId());
            report.setCameraName(cameraName == null ? "" : cameraName);
            report.setAlgorithmName(algorithmName == null ? "" : algorithmName);
            report.setCreatedStr(report.getCreatedAt() != null ? DateUtil.format(report.getCreatedAt(), "yyyy-MM-dd HH:mm:ss") : "");
            String typeName = "UNKNOW";
            if (ReportType.AI.getType() == report.getType()) {
                typeName = ReportType.AI.getText();
            } else if (ReportType.STREAM.getType() == report.getType()) {
                typeName = ReportType.STREAM.getText();
            }
            report.setTypeName(typeName);
        }
        return PageResultUtils.success(pageResult.getTotal(), reportList);
    }

    /**
     *
     * @param id
     * @param modelMap
     * @return
     */
    @SaIgnore
    @GetMapping({"/detail"})
    public String detail(Long id, ModelMap modelMap) {
        Report report = reportService.getById(id);
        if(report != null && report.getCreatedAt() != null) {
            report.setCreatedStr(DateUtil.format(report.getCreatedAt(), "yyyy-MM-dd HH:mm:ss"));
        }
        modelMap.addAttribute("report", report);

        //
        int width = 1;
        int height = 1;
        try {
            File file = new File(report.getFileName());
            if (file.exists()) {
                BufferedImage bi = ImgUtil.read(file);
                width = bi.getWidth();
                height = bi.getHeight();
            }
        } catch (Exception e) {
            //
        }
        modelMap.addAttribute("width", width <= 0 ? 1 : width);
        modelMap.addAttribute("height", height <= 0 ? 1 : height);

        //
        Camera camera = cameraService.getById(report == null ? 0L : report.getCameraId());
        modelMap.addAttribute("camera", camera);

        //
        Algorithm algorithm = algorithmService.getById(report == null ? 0L : report.getAlgorithmId());

        //
        if(report.getAlgorithmId() == 0L && report.getType() == ReportType.STREAM.getType()) {
            algorithm = new Algorithm();
            algorithm.setName(ReportType.STREAM.getText());
        }
        modelMap.addAttribute("algorithm", algorithm);

        //
        modelMap.addAttribute("webUrl", configService.getByValTag("webUrl"));

        return "report/detail";
    }

    /**
     *
     * @param id
     * @return
     */
    @PostMapping({"/detail"})
    @ResponseBody
    public JsonResult detailInfo(Long id) {
        Map<String, Object> retMap = new HashMap<>();

        //
        Report report = reportService.getById(id);
        if(report != null && report.getCreatedAt() != null) {
            report.setCreatedStr(DateUtil.format(report.getCreatedAt(), "yyyy-MM-dd HH:mm:ss"));
        }
        retMap.put("report", report);

        //
        int width = 1;
        int height = 1;
        try {
            File file = new File(report.getFileName());
            if (file.exists()) {
                BufferedImage bi = ImgUtil.read(file);
                width = bi.getWidth();
                height = bi.getHeight();
            }
        } catch (Exception e) {
            //
        }
        retMap.put("width", width <= 0 ? 1 : width);
        retMap.put("height", height <= 0 ? 1 : height);

        //
        Camera camera = cameraService.getById(report == null ? 0L : report.getCameraId());
        retMap.put("camera", camera);

        //
        Algorithm algorithm = algorithmService.getById(report == null ? 0L : report.getAlgorithmId());

        //
        if(report.getAlgorithmId() == 0L && report.getType() == ReportType.STREAM.getType()) {
            algorithm = new Algorithm();
            algorithm.setName(ReportType.STREAM.getText());
        }
        retMap.put("algorithm", algorithm);

        return JsonResultUtils.success(retMap);
    }

    /**
     *
     * @param id
     * @param response
     * @throws Exception
     */
    @SaIgnore
    @GetMapping({"/stream"})
    public void getImageAsByteArray(@RequestParam(defaultValue = "0") Long id, HttpServletResponse response) {
        Report report = reportService.getById(id);
        if(report != null && StrUtil.isNotBlank(report.getFileName())) {
            try {
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(new File(report.getFileName())));
                response.setContentType("image/jpeg");
                IOUtils.copy(in, response.getOutputStream());
                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param ids
     * @return
     */
    @PostMapping({"/batchRemove"})
    @ResponseBody
    public JsonResult batchRemove(String ids) {
        if(StrUtil.isBlank(ids)) {
            return JsonResultUtils.fail("没有选中数据");
        }

        String[] idArr = ids.split(",");
        if(idArr == null || idArr.length == 0) {
            return JsonResultUtils.fail("没有选中数据");
        }

        for(String id : idArr) {
            try {
                Long idLng = Long.parseLong(id);
                reportService.updateDisplay(idLng, 1); // 不显示
            } catch (Exception e) {
                //
            }
        }


        return JsonResultUtils.success();
    }


    /**
     * 增量训练
     * @param modelMap
     * @return
     */
    @GetMapping("/audit")
    public String audit(ModelMap modelMap) {
        List<Camera> cameraList = cameraService.listData();
        modelMap.addAttribute("cameraList", cameraList == null ? new ArrayList<>() : cameraList);
        List<Algorithm> algorithmList = algorithmService.list();
        modelMap.addAttribute("algorithmList", algorithmList == null ? new ArrayList<>() : algorithmList);
        return "report/audit";
    }

    /**
     *
     * @param page
     * @param limit
     * @param cameraId
     * @param algorithmId
     * @param auditState
     * @return
     */
    @PostMapping({"/auditListPage"})
    @ResponseBody
    public PageResult auditListPage(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer limit, Long cameraId, Long algorithmId, Integer auditState, Integer auditResult) {
        Report reportQuery = new Report();
        reportQuery.setCameraId(cameraId);
        reportQuery.setAlgorithmId(algorithmId);
        reportQuery.setType(0);
        reportQuery.setAuditState(auditState);
        reportQuery.setAuditResult(auditResult);

        //
        IPage<Report> pageResult = this.reportService.listPage(new Page<>(page, limit), reportQuery);
        List<Report> reportList = pageResult.getRecords();
        if (reportList == null) {
            reportList = new ArrayList<>();
        }
        Map<Long, String> cameraNames = new HashMap<>();
        List<Camera> cameraList = this.cameraService.list();
        if (cameraList != null) {
            for (Camera camera : cameraList) {
                cameraNames.put(camera.getId(), camera.getName());
            }
        }
        Map<Long, String> algorithmNames = new HashMap<>();
        List<Algorithm> algorithmList = this.algorithmService.list();
        if (algorithmList != null) {
            for (Algorithm algorithm : algorithmList) {
                algorithmNames.put(algorithm.getId(), algorithm.getName());
            }
        }
        for (Report report : reportList) {
            String cameraName = cameraNames.get(report.getCameraId());
            String algorithmName = algorithmNames.get(report.getAlgorithmId());
            report.setCameraName(cameraName == null ? "" : cameraName);
            report.setAlgorithmName(algorithmName == null ? "" : algorithmName);
            report.setCreatedStr(report.getCreatedAt() != null ? DateUtil.format(report.getCreatedAt(), "yyyy-MM-dd HH:mm:ss") : "");
            report.setTypeName("监控告警");
            try {
                JSONArray root = JSON.parseArray(report.getParams());
                JSONObject first = root.getJSONObject(0);
                report.setConf(first.getString("confidence"));
            } catch (Exception e) {
                report.setConf("Error");
            }
        }
        return PageResultUtils.success(pageResult.getTotal(), reportList);
    }

    /**
     *
     * @param id
     * @param modelMap
     * @return
     */
    @GetMapping({"/auditDetail"})
    public String auditDetail(Long id, ModelMap modelMap) {
        Report report = reportService.getById(id);
        if(report != null && report.getCreatedAt() != null) {
            report.setCreatedStr(DateUtil.format(report.getCreatedAt(), "yyyy-MM-dd HH:mm:ss"));
        }
        modelMap.addAttribute("report", report);

        //
        int width = 1;
        int height = 1;
        try {
            File file = new File(report.getFileName());
            if (file.exists()) {
                BufferedImage bi = ImgUtil.read(file);
                width = bi.getWidth();
                height = bi.getHeight();
            }
        } catch (Exception e) {
            //
        }
        modelMap.addAttribute("width", width <= 0 ? 1 : width);
        modelMap.addAttribute("height", height <= 0 ? 1 : height);

        //
        Camera camera = cameraService.getById(report == null ? 0L : report.getCameraId());
        modelMap.addAttribute("camera", camera);

        //
        Algorithm algorithm = algorithmService.getById(report == null ? 0L : report.getAlgorithmId());

        //
        if(report.getAlgorithmId() == 0L && report.getType() == ReportType.STREAM.getType()) {
            algorithm = new Algorithm();
            algorithm.setName(ReportType.STREAM.getText());
        }
        modelMap.addAttribute("algorithm", algorithm);

        String webUrl = configService.getByValTag("webUrl");
        String imageUrl = ((webUrl == null) ? "" : webUrl) + "/report/stream?id=" + report.getId();
        modelMap.addAttribute("imageUrl", imageUrl);

        return "report/audit_detail";
    }

    /**
     * 审核
     * @param id
     * @param result
     * @return
     */
    @PostMapping("/audit")
    @ResponseBody
    public JsonResult doAudit(Long id, Integer result) {
        reportService.updateAudit(id, result);
        return JsonResultUtils.success();
    }

    /**
     * 增量训练 - 导出
     * @param modelMap
     * @return
     */
    @GetMapping("/audit/export")
    public String auditExport(ModelMap modelMap) {
        List<Camera> cameraList = cameraService.listData();
        modelMap.addAttribute("cameraList", cameraList == null ? new ArrayList<>() : cameraList);
        List<Algorithm> algorithmList = algorithmService.list();
        modelMap.addAttribute("algorithmList", algorithmList == null ? new ArrayList<>() : algorithmList);
        return "report/audit_export";
    }

    /**
     * 增量训练 - 导出
     * @param modelMap
     * @return
     */
    @PostMapping("/audit/export")
    @ResponseBody
    public JsonResult doAuditExport(Long algorithmId, Long cameraId, String startText, String endText, Integer auditState, ModelMap modelMap) {
        Date startDate = null;
        Date endDate = null;

        //
        if(StrUtil.isNotBlank(startText)) {
            try {
                startDate = DateUtil.parse(startText, "yyyy-MM-dd");
            } catch (Exception e) {
                //
            }
        }

        //
        if(StrUtil.isNotBlank(endText)) {
            try {
                endDate = DateUtil.parse(endText, "yyyy-MM-dd");
                endDate = DateUtil.offsetDay(endDate, 1); // 向后移动一天
            } catch (Exception e) {
                //
            }
        }

        String fileName = reportService.export(uploadDir, algorithmId, cameraId, startDate, endDate, auditState);
        if(fileName == null) {
            return JsonResultUtils.fail("没有数据");
        }
        return JsonResultUtils.success(fileName);
    }

    /**
     * 下载文件压缩包
     */
    @GetMapping("/audit/download")
    public void download(String fileName, HttpServletRequest request, HttpServletResponse response) {
        //
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            String chName = "增量训练_下载.zip";

            //
            //File file = new File("/Users/zhoumingxing/Downloads/futuyuan/05.mp4");

            File file = new File(uploadDir + fileName);

            //
            response.reset();
            response.setContentType("application/octet-stream");
            response.setContentLengthLong(file.length());
            response.addHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(chName, "UTF-8"));

            //


//            bis = new BufferedInputStream(new FileInputStream(uploadDir + fileName));
//            bis = new BufferedInputStream(new FileInputStream(file));
//            bos = new BufferedOutputStream(response.getOutputStream());
//            byte[] b = new byte[1024];
//            int len;
//            while ((len = bis.read(b)) != -1) {
//                bos.write(b, 0, len);
//                //Thread.sleep(5*1000);
//            }
//            bos.flush();

            InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
            FileCopyUtils.copy(inputStream, response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            if(bos != null) {
//                try {
//                    bos.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            if(bis != null) {
//                try {
//                    bis.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
        }
    }

    /**
     * 手动推送
     * @param id
     * @return
     */
    @PostMapping("/pushData")
    @ResponseBody
    public JsonResult pushData(Long id) {
        // 推送第三方
        String reportPushUrl = configService.getByValTag("reportPushUrl");
        String reportPushImage = configService.getByValTag("reportPushImage");
        if(StrUtil.isBlank(reportPushUrl)) {
            return JsonResultUtils.fail("推送地址未配置");
        }

        //
        Report report = reportService.getById(id);
        if(report == null) {
            return JsonResultUtils.fail("告警数据不存在，或已删除");
        }

        //
        Camera camera = cameraService.getById(report.getCameraId());
        if(camera == null) {
            return JsonResultUtils.fail("摄像头数据不存在，或已删除");
        }

        //
        Algorithm algorithm = algorithmService.getById(report.getAlgorithmId());
        if(algorithm == null) {
            return JsonResultUtils.fail("算法数据不存在，或已删除");
        }

        //
        try {
            JSONObject reportMap = new JSONObject();
            reportMap.put("cmpn_cd", "TLB");
            reportMap.put("camera_id", String.valueOf(report.getCameraId()));
            reportMap.put("camera_name", camera.getName());
            reportMap.put("algorithm_id", String.valueOf(report.getAlgorithmId()));
            reportMap.put("algorithm_name", algorithm.getName());
            reportMap.put("level", "F");
            reportMap.put("img_path", report.getFileName());
            reportMap.put("img_ext", FileUtil.extName(report.getFileName()));
            reportMap.put("img_name", FileUtil.getName(report.getFileName()));
            reportMap.put("alarm_dt", DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
            reportMap.put("report_id", String.valueOf(report.getId()));
            reportMap.put("params", report.getParams());

            // imageBase64 这个数据做成配置式，因为需要消耗时间
            boolean toBase64 = false;
            if (reportPushImage != null && "true".equals(reportPushImage)) {
                toBase64 = true;
            }

            reportPushService.request(reportPushUrl, reportMap, toBase64, report.getFileName());
            return JsonResultUtils.success();
        } catch (Exception e) {
            return JsonResultUtils.fail("推送异常@" + e.getMessage());
        }
    }


    @GetMapping("/test")
    @ResponseBody
    public JsonResult test() {
        ReportMessage reportMessage = new ReportMessage();
        reportMessage.setType("REPORT_SHOW");
        reportMessage.setCameraId("1591683544965459969");
        reportMessage.setAlgorithmId("1591683470285877249");
        reportMessage.setParams("[{\"confidence\":0.52,\"position\":[1036,380,1228,544],\"type\":\"water\"}]");
        reportMessage.setCameraName("test");
        reportMessage.setAlgorithmName("test");
        reportMessage.setAlarmTime(DateUtil.format(new Date(), "yyyy-MM-dd MM:ss"));
        reportMessage.setWareName("test");
        reportMessage.setId("1630107941640114178");
        reportMessage.setWebUrl(configService.getByValTag("webUrl"));
        reportWebsocket.sendToAll(JSON.toJSONString(reportMessage));

        return JsonResultUtils.success();
    }

    /**
     * 卡片列表
     * @param modelMap
     * @return
     */
    @GetMapping(value = "/list_card")
    public String listCard(ModelMap modelMap) {
        List<Camera> cameraList = cameraService.listData();
        modelMap.addAttribute("cameraList", cameraList == null ? new ArrayList<>() : cameraList);
        List<Algorithm> algorithmList = algorithmService.list();
        modelMap.addAttribute("algorithmList", algorithmList == null ? new ArrayList<>() : algorithmList);
        modelMap.addAttribute("startDate", DateUtil.format(new Date(), "yyyy-MM-dd"));
        modelMap.addAttribute("endDate", DateUtil.format(new Date(), "yyyy-MM-dd"));
        return "report/list_card";
    }

    /**
     * 卡片列表数据
     * @return
     */
    @GetMapping(value = "/list_card_data")
    @ResponseBody
    public PageResult listCardData(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "8") Integer limit, Long cameraId, Long algorithmId, String startDate, String endDate, String startDate1, String endDate1) {
        //
        Long startMills = null;
        Long endMills = null;
        if(StrUtil.isBlank(startDate) && StrUtil.isBlank(endDate)) {
            try {
                if(StrUtil.isNotBlank(startDate1)) {
                    startMills = DateUtil.parse(startDate1, "yyyy-MM-dd").getTime();
                }
                if(StrUtil.isNotBlank(endDate1)) {
                    Date date = DateUtil.parse(endDate1, "yyyy-MM-dd");
                    endMills = DateUtil.offsetDay(date, 1).getTime();
                }
            } catch (Exception e) {
                //
            }
        } else {
            try {
                if (StrUtil.isNotBlank(startDate)) {
                    startMills = DateUtil.parse(startDate, "yyyy-MM-dd").getTime();
                }
                if (StrUtil.isNotBlank(endDate)) {
                    Date date = DateUtil.parse(endDate, "yyyy-MM-dd");
                    endMills = DateUtil.offsetDay(date, 1).getTime();
                }
            } catch (Exception e) {
                //
            }
        }

        //
        IPage<Report> pageResult = this.reportService.listByPage(new Page<>(page, limit), cameraId, algorithmId, null, startMills, endMills);
        List<Report> reportList = pageResult.getRecords();
        if (reportList == null) {
            reportList = new ArrayList<>();
        }
        Map<Long, String> cameraNames = new HashMap<>();
        List<Camera> cameraList = this.cameraService.list();
        if (cameraList != null) {
            for (Camera camera : cameraList) {
                cameraNames.put(camera.getId(), camera.getName());
            }
        }
        Map<Long, String> algorithmNames = new HashMap<>();
        List<Algorithm> algorithmList = this.algorithmService.list();
        if (algorithmList != null) {
            for (Algorithm algorithm : algorithmList) {
                algorithmNames.put(algorithm.getId(), algorithm.getName());
            }
        }

        //
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Report report : reportList) {
            //
            String cameraName = cameraNames.get(report.getCameraId());
            String algorithmName = algorithmNames.get(report.getAlgorithmId());

            // 区域位置名称
            String wareName = "-";
            Camera camera = cameraService.getById(report.getCameraId());
            if(camera != null) {
                Long wareHouseId = camera.getWareHouseId();
                if(wareHouseId != null && wareHouseId != 0) {
                    WareHouse wareHouse = wareHouseService.getById(wareHouseId);
                    if(wareHouse != null) {
                        wareName = wareHouse.getName();
                    }
                }
            }

            //
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("id", report.getId());
            dataMap.put("params", report.getParams());
            dataMap.put("cameraName", "所属摄像机：" + cameraName);
            dataMap.put("algorithmName", "告警类型：" + algorithmName);
            dataMap.put("wareName", "区域名称：" + wareName);
            dataMap.put("image", "/report/stream?id=" + report.getId());
            dataMap.put("alarmTime", "告警时间：" + report.getCreatedAt() != null ? DateUtil.format(report.getCreatedAt(), "yyyy-MM-dd HH:mm:ss") : "");
            dataList.add(dataMap);
        }
        return PageResultUtils.success(pageResult.getTotal(), dataList);
    }
}