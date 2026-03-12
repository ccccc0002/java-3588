package com.yihecode.camera.ai.web;


import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
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
 * 鍛婅绠＄悊锛屾煡锟?灞曠ず/瀹℃牳锟?
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

    @Autowired
    private ReportPushTargetService reportPushTargetService;

    //
    @Autowired
    private WareHouseService wareHouseService;

    @Autowired
    private ReportWebsocket reportWebsocket;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

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
    public PageResult listPage(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "10") Integer limit,
                               Long cameraId,
                               Long algorithmId,
                               Integer type,
                               String startTime,
                               String endTime) {
        Long startMills = parseDateTimeText(startTime, false);
        Long endMills = parseDateTimeText(endTime, true);
        IPage<Report> pageResult = this.reportService.listByPage(new Page<>(page, limit), cameraId, algorithmId, type, startMills, endMills);
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

    private Long parseDateTimeText(String text, boolean endExclusive) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String normalized = text.trim().replace("T", " ");
        Date date = null;
        try {
            if (normalized.length() == 10) {
                date = DateUtil.parse(normalized, "yyyy-MM-dd");
                if (endExclusive) {
                    date = DateUtil.offsetDay(date, 1);
                }
            } else if (normalized.length() == 16) {
                date = DateUtil.parse(normalized, "yyyy-MM-dd HH:mm");
            } else if (normalized.length() == 19) {
                date = DateUtil.parse(normalized, "yyyy-MM-dd HH:mm:ss");
            } else {
                date = DateUtil.parse(normalized);
            }
        } catch (Exception e) {
            return null;
        }
        return date == null ? null : date.getTime();
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
        int[] imageSize = resolveReportImageSize(report);
        modelMap.addAttribute("width", imageSize[0]);
        modelMap.addAttribute("height", imageSize[1]);

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
        int[] imageSize = resolveReportImageSize(report);
        retMap.put("width", imageSize[0]);
        retMap.put("height", imageSize[1]);

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
        File imageFile = resolveReportImageFile(report);
        if(imageFile != null && imageFile.exists()) {
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(imageFile))) {
                String contentType = FileUtil.getMimeType(imageFile.getName());
                response.setContentType(StrUtil.isBlank(contentType) ? "image/jpeg" : contentType);
                IOUtils.copy(in, response.getOutputStream());
            } catch (Exception e) {
                log.warn("read report image failed, id={}, path={}, ex={}", id, imageFile.getAbsolutePath(), e.getMessage());
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
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            operationLogService.record("report:batch_remove", "ids=" + ids, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        if(StrUtil.isBlank(ids)) {
            return JsonResultUtils.fail("娌℃湁閫変腑鏁版嵁");
        }

        String[] idArr = ids.split(",");
        if(idArr == null || idArr.length == 0) {
            return JsonResultUtils.fail("娌℃湁閫変腑鏁版嵁");
        }

        for(String id : idArr) {
            try {
                Long idLng = Long.parseLong(id);
                reportService.updateDisplay(idLng, 1); // 涓嶆樉锟?
            } catch (Exception e) {
                //
            }
        }


        return JsonResultUtils.success();
    }


    /**
     * 澧為噺璁粌
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
            report.setTypeName("鐩戞帶鍛婅");
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
        int[] imageSize = resolveReportImageSize(report);
        modelMap.addAttribute("width", imageSize[0]);
        modelMap.addAttribute("height", imageSize[1]);

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
     * 瀹℃牳
     * @param id
     * @param result
     * @return
     */
    private int[] resolveReportImageSize(Report report) {
        int width = 1;
        int height = 1;
        try {
            File file = resolveReportImageFile(report);
            if (file != null && file.exists()) {
                BufferedImage bi = ImgUtil.read(file);
                if (bi != null) {
                    width = bi.getWidth();
                    height = bi.getHeight();
                }
            }
        } catch (Exception e) {
            log.debug("resolve report image size failed, reportId={}", report == null ? null : report.getId(), e);
        }
        return new int[]{width <= 0 ? 1 : width, height <= 0 ? 1 : height};
    }

    private File resolveReportImageFile(Report report) {
        if (report == null || StrUtil.isBlank(report.getFileName())) {
            return null;
        }
        File candidate = new File(report.getFileName());
        if (candidate.isAbsolute() || candidate.exists()) {
            return candidate;
        }
        if (StrUtil.isBlank(uploadDir)) {
            return candidate;
        }
        return new File(uploadDir, report.getFileName());
    }

    @PostMapping("/audit")
    @ResponseBody
    public JsonResult doAudit(Long id, Integer result) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            operationLogService.record("report:audit", "reportId=" + id, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        reportService.updateAudit(id, result);
        operationLogService.record("report:audit", "reportId=" + id, true, "audit updated", "result=" + result);
        return JsonResultUtils.success();
    }

    /**
     * 澧為噺璁粌 - 瀵煎嚭
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
     * 澧為噺璁粌 - 瀵煎嚭
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
                endDate = DateUtil.offsetDay(endDate, 1); // 鍚戝悗绉诲姩涓€锟?
            } catch (Exception e) {
                //
            }
        }

        String fileName = reportService.export(uploadDir, algorithmId, cameraId, startDate, endDate, auditState);
        if(fileName == null) {
            return JsonResultUtils.fail("娌℃湁鏁版嵁");
        }
        return JsonResultUtils.success(fileName);
    }

    /**
     * 涓嬭浇鏂囦欢鍘嬬缉锟?
     */
    @GetMapping("/audit/download")
    public void download(String fileName, HttpServletRequest request, HttpServletResponse response) {
        //
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            String chName = "澧為噺璁粌_涓嬭浇.zip";

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
     * 鎵嬪姩鎺拷?
     * @param id
     * @return
     */
    @PostMapping("/pushData")
    @ResponseBody
    public JsonResult pushData(Long id) {
        if (!roleAccessService.canManagePushTargets(currentAccountId())) {
            operationLogService.record("report:push_data", "reportId=" + id, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        List<Map<String, Object>> targets = reportPushTargetService.listEnabledTargets();
        if(targets == null || targets.isEmpty()) {
            operationLogService.record("report:push_data", "reportId=" + id, false, "no enabled push targets", "");
            return JsonResultUtils.fail("锟斤拷锟酵碉拷址未锟斤拷锟矫ｏ拷锟斤拷锟斤拷锟斤拷锟斤拷 HTTP 锟斤拷锟斤拷目锟斤拷");
        }

        Report report = reportService.getById(id);
        if(report == null) {
            operationLogService.record("report:push_data", "reportId=" + id, false, "report not found", "");
            return JsonResultUtils.fail("锟芥警锟斤拷锟捷诧拷锟斤拷锟节ｏ拷锟斤拷锟斤拷删锟斤拷");
        }

        Camera camera = cameraService.getById(report.getCameraId());
        if(camera == null) {
            return JsonResultUtils.fail("锟斤拷锟斤拷头锟斤拷锟捷诧拷锟斤拷锟节ｏ拷锟斤拷锟斤拷删锟斤拷");
        }

        Algorithm algorithm = algorithmService.getById(report.getAlgorithmId());
        if(algorithm == null) {
            return JsonResultUtils.fail("锟姐法锟斤拷锟捷诧拷锟斤拷锟节ｏ拷锟斤拷锟斤拷删锟斤拷");
        }

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
            reportMap.put("webUrl", configService.getByValTag("webUrl"));

            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failedCount = 0;
            for (Map<String, Object> target : targets) {
                String targetUrl = trimText(target.get("url"));
                if (StrUtil.isBlank(targetUrl)) {
                    continue;
                }
                boolean includeImage = toBool(target.get("include_image"), false);
                String bearerToken = trimText(target.get("bearer_token"));
                Map<String, Object> pushResult = reportPushService.requestSync(
                        targetUrl,
                        reportMap,
                        includeImage,
                        report.getFileName(),
                        bearerToken
                );
                pushResult.put("target_id", trimText(target.get("id")));
                pushResult.put("target_name", trimText(target.get("name")));
                results.add(pushResult);
                if (toBool(pushResult.get("success"), false)) {
                    successCount++;
                } else {
                    failedCount++;
                }
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("target_count", targets.size());
            summary.put("success_count", successCount);
            summary.put("failed_count", failedCount);
            summary.put("results", results);

            if (failedCount > 0) {
                operationLogService.record("report:push_data", "reportId=" + id, false, "push partially failed", JSON.toJSONString(summary));
                return JsonResultUtils.fail("锟斤拷锟斤拷锟斤拷锟斤拷失锟斤拷", summary);
            }
            operationLogService.record("report:push_data", "reportId=" + id, true, "push success", JSON.toJSONString(summary));
            return JsonResultUtils.success(summary);
        } catch (Exception e) {
            operationLogService.record("report:push_data", "reportId=" + id, false, "push exception", e.getMessage());
            return JsonResultUtils.fail("锟斤拷锟斤拷锟届常:" + e.getMessage());
        }
    }

    @PostMapping("/push/targets")
    @ResponseBody
    public JsonResult listPushTargets() {
        try {
            return JsonResultUtils.success(reportPushTargetService.listAllTargets());
        } catch (Exception e) {
            return JsonResultUtils.fail("锟斤拷取锟斤拷锟斤拷目锟斤拷失锟斤拷: " + e.getMessage());
        }
    }

    @PostMapping("/push/targets/save")
    @ResponseBody
    public JsonResult savePushTarget(String id,
                                     String name,
                                     String url,
                                     @RequestParam(value = "bearerToken", required = false) String bearerToken,
                                     @RequestParam(value = "bearer_token", required = false) String bearerTokenSnake,
                                     Boolean enabled,
                                     @RequestParam(value = "includeImage", required = false) Boolean includeImage,
                                     @RequestParam(value = "include_image", required = false) Boolean includeImageSnake) {
        if (!roleAccessService.canManagePushTargets(currentAccountId())) {
            operationLogService.record("report:push_target_save", "targetId=" + id, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        try {
            String token = StrUtil.isNotBlank(bearerToken) ? bearerToken : bearerTokenSnake;
            Boolean include = includeImage != null ? includeImage : includeImageSnake;
            Object result = reportPushTargetService.saveTarget(id, name, url, token, enabled, include);
            operationLogService.record("report:push_target_save", "targetId=" + id, true, "push target saved", "name=" + name);
            return JsonResultUtils.success(result);
        } catch (Exception e) {
            operationLogService.record("report:push_target_save", "targetId=" + id, false, "push target save failed", e.getMessage());
            return JsonResultUtils.fail("push target save failed: " + e.getMessage());
        }
    }

    @PostMapping("/push/targets/delete")
    @ResponseBody
    public JsonResult deletePushTarget(String id) {
        if (!roleAccessService.canManagePushTargets(currentAccountId())) {
            operationLogService.record("report:push_target_delete", "targetId=" + id, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        try {
            Object result = reportPushTargetService.deleteTarget(id);
            operationLogService.record("report:push_target_delete", "targetId=" + id, true, "push target deleted", "");
            return JsonResultUtils.success(result);
        } catch (Exception e) {
            operationLogService.record("report:push_target_delete", "targetId=" + id, false, "push target delete failed", e.getMessage());
            return JsonResultUtils.fail("push target delete failed: " + e.getMessage());
        }
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private String trimText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean toBool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
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
     * 鍗＄墖鍒楄〃
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
     * 鍗＄墖鍒楄〃鏁版嵁
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

            // 鍖哄煙浣嶇疆鍚嶇О
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
            dataMap.put("cameraName", "Camera: " + cameraName);
            dataMap.put("algorithmName", "Algorithm: " + algorithmName);
            dataMap.put("wareName", "Area: " + wareName);
            dataMap.put("image", "/report/stream?id=" + report.getId());
            dataMap.put("alarmTime", "Alarm Time: " + (report.getCreatedAt() != null ? DateUtil.format(report.getCreatedAt(), "yyyy-MM-dd HH:mm:ss") : ""));
            dataList.add(dataMap);
        }
        return PageResultUtils.success(pageResult.getTotal(), dataList);
    }
}


