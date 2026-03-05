package com.yihecode.camera.ai.web.api;

import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.enums.ReportType;
import com.yihecode.camera.ai.service.*;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;

/**
 * 视频流告警推送，对接算法将断开的摄像头进行告警
 * 算法已经不再将断开的摄像头作为告警推送到中台
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Deprecated
@Controller
@RequestMapping({"/api/stream"})
public class StreamApiController {
    private static final Logger log = LoggerFactory.getLogger(StreamApiController.class);

    @Autowired
    private ReportService reportService;

    @Autowired
    private CameraService cameraService;

    /**
     *
     * @param cameraId
     * @param state
     * @return
     */
    @RequestMapping({"", "/"})
    @ResponseBody
    public JsonResult stream(@RequestParam(value = "camera_id", required = false) Long cameraId,
                             @RequestParam(value = "state", required = false) Integer state) {
        try {
            if (cameraId == null) {
                return JsonResultUtils.fail("the camera_id parameter is null");
            }

            if (state == null) {
                return JsonResultUtils.fail("the state@(must 0 or 1) parameter is null");
            }

            //
            Camera camera = cameraService.getById(cameraId);
            if(camera == null) {
                return JsonResultUtils.fail("the camera is not found");
            }

            //
            if (state >= 1) {
                Report report = new Report();
                report.setCameraId(cameraId);
                report.setAlgorithmId(0L);
                report.setFileName("");
                report.setParams("");
                report.setType(ReportType.STREAM.getType());
                report.setCreatedAt(new Date());
                report.setCreatedMills(System.currentTimeMillis());
                report.setDisplay(0);
                this.reportService.save(report);
            }

            //
            return JsonResultUtils.success();
        } catch (Exception e) {
            log.error("调用流状态上报接口异常", e);
            return JsonResultUtils.fail();
        }
    }
}