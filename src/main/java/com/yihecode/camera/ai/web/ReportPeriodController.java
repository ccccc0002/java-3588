package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.entity.ReportPeriod;
import com.yihecode.camera.ai.enums.CameraAction;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraAlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.ReportPeriodService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.utils.PageResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 閸涘﹨顒熼弮鑸殿唽缁狅紕鎮? *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Slf4j
@Controller
@RequestMapping({"/report/period"})
public class ReportPeriodController {

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

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    /**
     *
     * @return
     */
    @GetMapping({"", "/"})
    public String index(Long id, ModelMap modelMap) {
        modelMap.addAttribute("cameraId", id);
        return "period/index";
    }

    /**
     *
     * @param id
     * @param modelMap
     * @return
     */
    @GetMapping({"/form"})
    public String form(Long id, Long cameraId, ModelMap modelMap) {
        modelMap.addAttribute("cameraId", cameraId);

        //
        List<CameraAlgorithm> cameraAlgorithmList = cameraAlgorithmService.listByCamera(cameraId);

        //
        List<Algorithm> algorithmList = algorithmService.list();
        if(algorithmList == null) {
            algorithmList = new ArrayList<>();
        }

        //
        List<Algorithm> subList = new ArrayList<>();
        for(CameraAlgorithm cameraAlgorithm : cameraAlgorithmList) {
            for(Algorithm algorithm : algorithmList) {
                if(algorithm.getId().equals(cameraAlgorithm.getAlgorithmId())) {
                    subList.add(algorithm);
                    break;
                }
            }
        }
        modelMap.addAttribute("algorithmList", subList);

        //
        if (id == null) {
            return "period/form";
        }
        modelMap.addAttribute("reportPeriod", reportPeriodService.getById(id));
        return "period/form";
    }

    /**
     *
     * @return
     */
    @PostMapping({"/algorithms"})
    @ResponseBody
    public JsonResult listAlgorithms(Long cameraId) {
        if(cameraId == null) {
            return JsonResultUtils.success(new ArrayList<>());
        }

        //
        List<CameraAlgorithm> cameraAlgorithmList = cameraAlgorithmService.listByCamera(cameraId);

        //
        List<Algorithm> algorithmList = algorithmService.list();
        if(algorithmList == null) {
            algorithmList = new ArrayList<>();
        }

        //
        List<Algorithm> subList = new ArrayList<>();
        for(CameraAlgorithm cameraAlgorithm : cameraAlgorithmList) {
            for(Algorithm algorithm : algorithmList) {
                if(algorithm.getId().equals(cameraAlgorithm.getAlgorithmId())) {
                    subList.add(algorithm);
                    break;
                }
            }
        }
        return JsonResultUtils.success(subList);
    }

    /**
     *
     * @return
     */
    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData(Long cameraId) {
        if(cameraId == null) {
            return PageResultUtils.success(null, new ArrayList());
        }

        Camera camera = cameraService.getById(cameraId);
        if(camera == null) {
            return PageResultUtils.success(null, new ArrayList());
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
        List<Map<String, Object>> dataList = new ArrayList<>();

        //
        List<CameraAlgorithm> cameraAlgorithmList = cameraAlgorithmService.listByCamera(camera.getId());
        for(CameraAlgorithm cameraAlgorithm : cameraAlgorithmList) {
            //
            String algorithmName = algorithmNames.get(cameraAlgorithm.getAlgorithmId());
            if(algorithmName == null) {
                continue;
            }

            //
            List<ReportPeriod> reportPeriodList = reportPeriodService.listData(camera.getId(), cameraAlgorithm.getAlgorithmId());
            for(ReportPeriod reportPeriod : reportPeriodList) {
                //
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("id", reportPeriod.getId());
                dataMap.put("cameraName", camera.getName());
                dataMap.put("algorithmName", algorithmName);
                dataMap.put("period", reportPeriod.getStartText() + " - " + reportPeriod.getEndText());
                dataList.add(dataMap);
            }
        }
        return PageResultUtils.success(null, dataList);
    }

    /**
     *
     * @param reportPeriod
     * @return
     */
    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(ReportPeriod reportPeriod) {
        Long cameraIdForLog = reportPeriod == null ? null : reportPeriod.getCameraId();
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            operationLogService.record("report_period:save", "cameraId=" + cameraIdForLog, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        if (reportPeriod == null || reportPeriod.getCameraId() == null) {
            return JsonResultUtils.fail("camera is required");
        }
        if (reportPeriod.getAlgorithmId() == null) {
            return JsonResultUtils.fail("algorithm is required");
        }
        if (StrUtil.isBlank(reportPeriod.getStartText())) {
            return JsonResultUtils.fail("start time is required");
        }
        if (StrUtil.isBlank(reportPeriod.getEndText())) {
            return JsonResultUtils.fail("end time is required");
        }

        Integer startTime = Integer.valueOf(reportPeriod.getStartText().replaceAll(":", ""));
        Integer endTime = Integer.valueOf(reportPeriod.getEndText().replaceAll(":", ""));
        if (endTime <= startTime) {
            return JsonResultUtils.fail("start time must be earlier than end time");
        }

        reportPeriod.setStartTime(startTime);
        reportPeriod.setEndTime(endTime);
        reportPeriodService.saveOrUpdate(reportPeriod);
        cameraService.updateActionByCamera(reportPeriod.getCameraId(), CameraAction.ACTION_UPD.getType());
        operationLogService.record("report_period:save", "periodId=" + reportPeriod.getId(), true, "report period saved", "cameraId=" + reportPeriod.getCameraId());
        return JsonResultUtils.success();
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
            operationLogService.record("report_period:delete", "periodId=" + id, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        ReportPeriod reportPeriod = reportPeriodService.getById(id);
        reportPeriodService.removeById(id);
        if (reportPeriod != null && reportPeriod.getCameraId() != null) {
            cameraService.updateActionByCamera(reportPeriod.getCameraId(), CameraAction.ACTION_UPD.getType());
        }
        operationLogService.record("report_period:delete", "periodId=" + id, true, "report period deleted", "");
        return JsonResultUtils.success();
    }
    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}
