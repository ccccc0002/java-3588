package com.yihecode.camera.ai.web.api;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yihecode.camera.ai.entity.*;
import com.yihecode.camera.ai.enums.MessageType;
import com.yihecode.camera.ai.enums.ReportType;
import com.yihecode.camera.ai.notify.sms.SendSmsUtil;
import com.yihecode.camera.ai.notify.wework.WeWorkRobotSendUtils;
import com.yihecode.camera.ai.service.*;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.vo.Message;
import com.yihecode.camera.ai.vo.ReportMessage;
import com.yihecode.camera.ai.websocket.MessageWebsocket;
import com.yihecode.camera.ai.websocket.ReportWebsocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警推送数据，对接算法推送告警数据
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Controller
@RequestMapping({"/api/report"})
public class ReportApiController {

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
    private ConfigService configService;

    //
    @Autowired
    private ReportPushService reportPushService;

    //
    @Autowired
    private ReportService reportService;

    @Autowired
    private MessageWebsocket websocket;

    @Autowired
    private ReportWebsocket reportWebsocket;

    @Autowired
    private WareHouseService wareHouseService;

    @Autowired
    private SmsPhoneService smsPhoneService;

    /**
     *
     * @param cameraId
     * @param algorithmId
     * @param fileName
     * @param params
     * @return
     */
    @RequestMapping({"", "/"})
    @ResponseBody
    public JsonResult report(@RequestParam(value = "camera_id", required = false) Long cameraId,
                             @RequestParam(value = "algorithm_id", required = false) Long algorithmId,
                             @RequestParam(value = "file_name", required = false) String fileName,
                             @RequestParam(value = "params", required = false) String params,
                             HttpServletRequest request) {
        try {
            if(cameraId == null || algorithmId == null) {
                return JsonResultUtils.fail("camera_id or algorithm_id is null");
            }

            //
            Camera camera = cameraService.getById(cameraId);
            if(camera == null) {
                return JsonResultUtils.fail("the camera is not found.");
            }

            //
            Algorithm algorithm = this.algorithmService.getById(algorithmId);
            if(algorithm == null) {
                return JsonResultUtils.fail("the algorithm is not found.");
            }

            // 把消息推送出去
            try {
                ReportMessage reportMessage = new ReportMessage();
                reportMessage.setType("REPORT");
                reportMessage.setCameraId(String.valueOf(cameraId));
                reportMessage.setParams(params);
                reportWebsocket.sendToAll(JSON.toJSONString(reportMessage));
            } catch (Exception e) {
                //
            }

            // 告警时间段判断
            boolean inPeriod = false;
            Integer period = Integer.valueOf(DateUtil.format(new Date(), "HHmm"));
            List<ReportPeriod> reportPeriodList = reportPeriodService.listData(cameraId, algorithmId);
            if(reportPeriodList.isEmpty()) {
                inPeriod = true;
            } else {
                for(ReportPeriod reportPeriod : reportPeriodList) {
                    if(reportPeriod.getStartTime() <= period && period <= reportPeriod.getEndTime()) {
                        inPeriod = true;
                        break;
                    }
                }
            }

            // 不在告警时段
            if(!inPeriod) {
                log.info("不在告警时段 {}, {}, {}, {}", camera.getName(), algorithm.getName(), fileName, params);
                return JsonResultUtils.success("不在报警时间段内");
            }

            //
            Report report = new Report();
            report.setCameraId(cameraId);
            report.setAlgorithmId(algorithmId);
            report.setType(ReportType.AI.getType());
            report.setFileName(fileName);
            report.setParams(params);
            report.setCreatedAt(new Date());
            report.setCreatedMills(System.currentTimeMillis());
            report.setAuditResult(0);
            report.setAuditState(0);
            int display = 0;

            // 根据摄像头的告警间隔处理
            Float intervalTime = camera.getIntervalTime();
            if(intervalTime != null && intervalTime > 0) {
                Report last = this.reportService.findLast(cameraId, algorithmId);
                if (last != null) {
                    if ((System.currentTimeMillis() - last.getCreatedMills()) < intervalTime * 1000) {
                        display = 1;
                    }
                }
            }

            //
            if(display == 0) {
                // display = 1 的数据都丢弃掉
                report.setDisplay(display);
                this.reportService.save(report);

                // 推送数据到前端显示
                String wareHouseName = "-";
                Long wareHouseId = camera.getWareHouseId();
                if(wareHouseId != null && wareHouseId != 0) {
                    WareHouse wareHouse = wareHouseService.getById(wareHouseId);
                    if(wareHouse != null) {
                        wareHouseName = wareHouse.getName();
                    }
                }
                ReportMessage reportMessage = new ReportMessage();
                reportMessage.setType("REPORT_SHOW");
                reportMessage.setCameraId(String.valueOf(cameraId));
                reportMessage.setAlgorithmId(String.valueOf(algorithm.getId()));
                reportMessage.setParams(params);
                reportMessage.setCameraName(camera.getName());
                reportMessage.setAlgorithmName(algorithm.getName());
                reportMessage.setAlarmTime(DateUtil.format(new Date(), "yyyy-MM-dd MM:ss"));
                reportMessage.setWareName(wareHouseName);
                reportMessage.setId(String.valueOf(report.getId()));
                reportWebsocket.sendToAll(JSON.toJSONString(reportMessage));

                //
                Message messageVo = new Message();
                messageVo.setType(MessageType.REPORT.getType());
                messageVo.setContent(camera.getName() + " 出现 " + algorithm.getName() + " 告警");

                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("reportId", report.getId());
                dataMap.put("cameraName", camera.getName());
                messageVo.setData(dataMap);

                ObjectMapper objectMapper = new ObjectMapper();
                SimpleModule simpleModule = new SimpleModule();
                simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
                simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
                objectMapper.registerModule(simpleModule);
                String voJson = objectMapper.writeValueAsString(messageVo);
                websocket.sendToAll(voJson);

                // 推送第三方
                String reportPushUrl = configService.getByValTag("reportPushUrl");
                String reportPushImage = configService.getByValTag("reportPushImage");
                if(StrUtil.isNotBlank(reportPushUrl)) {
                    try {
                        JSONObject reportMap = new JSONObject();
                        reportMap.put("cmpn_cd", "TLB");
                        reportMap.put("camera_id", String.valueOf(cameraId));
                        reportMap.put("camera_name", camera.getName());
                        reportMap.put("algorithm_id", String.valueOf(algorithmId));
                        reportMap.put("algorithm_name", algorithm.getName());
                        reportMap.put("level", "F");
                        reportMap.put("img_path", report.getFileName());
                        reportMap.put("img_ext", FileUtil.extName(report.getFileName()));
                        reportMap.put("img_name", FileUtil.getName(report.getFileName()));
                        reportMap.put("alarm_dt", DateUtil.format(new Date(), "MM/dd HH:mm"));
                        reportMap.put("report_id", String.valueOf(report.getId()));
                        reportMap.put("params", params);
                        reportMap.put("webUrl", configService.getByValTag("webUrl"));

                        // imageBase64 这个数据做成配置式，因为需要消耗时间
                        boolean toBase64 = false;
                        if(reportPushImage != null && "true".equals(reportPushImage)) {
                            toBase64 = true;
                        }

                        reportPushService.request(reportPushUrl, reportMap, toBase64, fileName);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    log.info("不推送告警，没有配置推送地址 {}, {}, {}, {}", camera.getName(), algorithm.getName(), fileName, params);
                }

                // 推送短信
                String smsEnable = configService.getByValTag("smsEnable");
                if(StrUtil.isNotBlank(smsEnable) && "true".equals(smsEnable)) {
                    String mobiles = smsPhoneService.listPhoneStr("test");
                    if (StrUtil.isNotBlank(mobiles)) {
                        SendSmsUtil.send(mobiles, camera.getName(), algorithm.getName());
                    }
                }

                // 推送到企业微信群机器人
                String weworkEnable = configService.getByValTag("weworkEnable");
                if(StrUtil.isNotBlank(weworkEnable) && "true".equals(weworkEnable)) {
                    String weworkUrl = configService.getByValTag("weworkUrl");
                    String webUrl = configService.getByValTag("webUrl");
                    if(StrUtil.isNotBlank(weworkUrl) && StrUtil.isNotBlank(webUrl)) {
                        String clickUrl = webUrl + "/report/detail?id=" + report.getId();
                        String picUrl = webUrl + "/report/stream?id=" + report.getId();
                        WeWorkRobotSendUtils.sendTextAndImage(weworkUrl, camera.getName() + "#" + algorithm.getName() + "#告警, " + DateUtil.format(new Date(), "MM/dd HH:mm"), clickUrl, picUrl);
                    }
                }
            }

            return JsonResultUtils.success();
        } catch (Exception e) {
            log.error("调用告警上报接口异常 {}", e.getMessage());
            return JsonResultUtils.fail(e.getMessage());
        }
    }
}