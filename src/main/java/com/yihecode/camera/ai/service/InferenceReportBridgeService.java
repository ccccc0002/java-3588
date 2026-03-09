package com.yihecode.camera.ai.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.entity.WareHouse;
import com.yihecode.camera.ai.enums.MessageType;
import com.yihecode.camera.ai.enums.ReportType;
import com.yihecode.camera.ai.vo.InferenceResult;
import com.yihecode.camera.ai.vo.Message;
import com.yihecode.camera.ai.vo.ReportMessage;
import com.yihecode.camera.ai.websocket.MessageWebsocket;
import com.yihecode.camera.ai.websocket.ReportWebsocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class InferenceReportBridgeService {

    @Autowired
    private CameraService cameraService;

    @Autowired
    private AlgorithmService algorithmService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private WareHouseService wareHouseService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private ReportWebsocket reportWebsocket;

    @Autowired
    private MessageWebsocket messageWebsocket;

    public Map<String, Object> persistAndBroadcast(Long cameraId, Long algorithmId, InferenceResult inferenceResult, String traceId) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("trace_id", traceId);
        ret.put("camera_id", cameraId);
        ret.put("algorithm_id", algorithmId);
        ret.put("persisted", false);
        ret.put("broadcasted", false);

        if (cameraId == null || algorithmId == null) {
            ret.put("status", "invalid_args");
            ret.put("reason", "camera_id or algorithm_id is null");
            return ret;
        }
        if (inferenceResult == null) {
            ret.put("status", "invalid_args");
            ret.put("reason", "inference_result is null");
            return ret;
        }

        List<Map<String, Object>> alarmPayload = inferenceResult.resolveAlarmPayload();
        boolean explicitAlerts = inferenceResult.hasExplicitAlerts();
        ret.put("alerts_explicit", explicitAlerts);
        ret.put("alert_count", alarmPayload == null ? 0 : alarmPayload.size());
        ret.put("event_count", inferenceResult.getEvents() == null ? 0 : inferenceResult.getEvents().size());
        if (alarmPayload == null || alarmPayload.isEmpty()) {
            ret.put("status", "skipped");
            ret.put("reason", explicitAlerts ? "empty alerts" : "empty detections");
            return ret;
        }

        Camera camera = cameraService.getById(cameraId);
        if (camera == null) {
            ret.put("status", "not_found");
            ret.put("reason", "camera not found");
            return ret;
        }

        Algorithm algorithm = algorithmService.getById(algorithmId);
        if (algorithm == null) {
            ret.put("status", "not_found");
            ret.put("reason", "algorithm not found");
            return ret;
        }

        String params = JSON.toJSONString(alarmPayload);

        int display = 0;
        Float intervalTime = camera.getIntervalTime();
        if (intervalTime != null && intervalTime > 0) {
            Report last = reportService.findLast(cameraId, algorithmId);
            if (last != null && (System.currentTimeMillis() - last.getCreatedMills()) < intervalTime * 1000) {
                display = 1;
            }
        }

        if (display != 0) {
            ret.put("status", "suppressed");
            ret.put("reason", "camera interval suppression");
            ret.put("suppressed", true);
            return ret;
        }

        Report report = new Report();
        report.setCameraId(cameraId);
        report.setAlgorithmId(algorithmId);
        report.setType(ReportType.AI.getType());
        report.setFileName("");
        report.setParams(params);
        report.setCreatedAt(new Date());
        report.setCreatedMills(System.currentTimeMillis());
        report.setAuditResult(0);
        report.setAuditState(0);
        report.setDisplay(0);
        reportService.save(report);

        ret.put("persisted", true);
        ret.put("report_id", report.getId());

        String wareHouseName = "-";
        if (camera.getWareHouseId() != null && camera.getWareHouseId() != 0) {
            WareHouse wareHouse = wareHouseService.getById(camera.getWareHouseId());
            if (wareHouse != null && StrUtil.isNotBlank(wareHouse.getName())) {
                wareHouseName = wareHouse.getName();
            }
        }

        try {
            ReportMessage reportMessage = new ReportMessage();
            reportMessage.setType("REPORT_SHOW");
            reportMessage.setCameraId(String.valueOf(cameraId));
            reportMessage.setAlgorithmId(String.valueOf(algorithmId));
            reportMessage.setParams(params);
            reportMessage.setCameraName(camera.getName());
            reportMessage.setAlgorithmName(algorithm.getName());
            reportMessage.setAlarmTime(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
            reportMessage.setWareName(wareHouseName);
            reportMessage.setId(String.valueOf(report.getId()));
            reportMessage.setWebUrl(configService.getByValTag("webUrl"));
            reportMessage.setTraceId(traceId);
            reportMessage.setAlertCount(alarmPayload.size());
            reportMessage.setAlertLabels(extractAlertLabels(alarmPayload, "label"));
            reportMessage.setAlertLabelsZh(extractAlertLabels(alarmPayload, "label_zh"));
            reportWebsocket.sendToAll(JSON.toJSONString(reportMessage));
            ret.put("broadcasted", true);
        } catch (Exception e) {
            log.warn("report websocket broadcast failed, trace_id={}, ex={}", traceId, e.getMessage());
        }

        try {
            Message messageVo = new Message();
            messageVo.setType(MessageType.REPORT.getType());
            messageVo.setContent(buildMessageContent(camera, algorithm, alarmPayload));

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("reportId", report.getId());
            dataMap.put("cameraName", camera.getName());
            dataMap.put("alertCount", alarmPayload.size());
            dataMap.put("alertLabels", extractAlertLabels(alarmPayload, "label"));
            dataMap.put("alertLabelsZh", extractAlertLabels(alarmPayload, "label_zh"));
            dataMap.put("traceId", traceId);
            messageVo.setData(dataMap);

            ObjectMapper objectMapper = new ObjectMapper();
            SimpleModule simpleModule = new SimpleModule();
            simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
            simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(simpleModule);
            String voJson = objectMapper.writeValueAsString(messageVo);
            messageWebsocket.sendToAll(voJson);
        } catch (Exception e) {
            log.warn("message websocket broadcast failed, trace_id={}, ex={}", traceId, e.getMessage());
        }

        ret.put("status", "ok");
        return ret;
    }

    private String buildMessageContent(Camera camera, Algorithm algorithm, List<Map<String, Object>> alarmPayload) {
        StringBuilder builder = new StringBuilder();
        builder.append(camera.getName()).append(" alarm: ").append(algorithm.getName());
        List<String> labelsZh = extractAlertLabels(alarmPayload, "label_zh");
        if (!labelsZh.isEmpty()) {
            builder.append(" [").append(String.join(", ", labelsZh)).append("]");
        }
        return builder.toString();
    }

    private List<String> extractAlertLabels(List<Map<String, Object>> alarmPayload, String key) {
        Set<String> labels = new LinkedHashSet<>();
        if (alarmPayload == null) {
            return new ArrayList<>();
        }
        for (Map<String, Object> item : alarmPayload) {
            if (item == null) {
                continue;
            }
            String value = String.valueOf(item.get(key) == null ? "" : item.get(key)).trim();
            if (StrUtil.isBlank(value) && "label_zh".equals(key)) {
                value = String.valueOf(item.get("label") == null ? "" : item.get("label")).trim();
            }
            if (StrUtil.isNotBlank(value)) {
                labels.add(value);
            }
        }
        return new ArrayList<>(labels);
    }
}
