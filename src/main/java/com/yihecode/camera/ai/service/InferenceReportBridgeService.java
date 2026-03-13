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
import com.yihecode.camera.ai.javacv.TakePhoto;
import com.yihecode.camera.ai.vo.InferenceResult;
import com.yihecode.camera.ai.vo.Message;
import com.yihecode.camera.ai.vo.ReportMessage;
import com.yihecode.camera.ai.websocket.MessageWebsocket;
import com.yihecode.camera.ai.websocket.ReportWebsocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    @Autowired(required = false)
    private TakePhoto takePhoto;

    @Value("${uploadDir}")
    private String uploadDir;

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
        broadcastOverlay(cameraId, alarmPayload, traceId);

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
            ret.put("broadcasted", true);
            return ret;
        }

        Report report = new Report();
        report.setCameraId(cameraId);
        report.setAlgorithmId(algorithmId);
        report.setType(ReportType.AI.getType());
        report.setFileName(resolveAlertImage(camera, inferenceResult, alarmPayload));
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

    private String resolveAlertImage(Camera camera, InferenceResult inferenceResult, List<Map<String, Object>> alarmPayload) {
        String annotatedPath = saveAnnotatedImageFromInference(inferenceResult, alarmPayload);
        if (StrUtil.isNotBlank(annotatedPath)) {
            return annotatedPath;
        }
        String snapshotPath = captureSnapshot(camera);
        if (StrUtil.isBlank(snapshotPath)) {
            return "";
        }
        return annotateSnapshot(snapshotPath, alarmPayload);
    }

    private String saveAnnotatedImageFromInference(InferenceResult inferenceResult, List<Map<String, Object>> alarmPayload) {
        if (inferenceResult == null || inferenceResult.getFrame() == null || inferenceResult.getFrame().isEmpty()) {
            return "";
        }
        String traceId = inferenceResult.getTraceId();
        String annotatedImageBase64 = asString(inferenceResult.getFrame().get("annotated_image_base64"));
        String alarmImageBase64 = asString(inferenceResult.getFrame().get("alarm_image_base64"));
        String rawImageBase64 = asString(inferenceResult.getFrame().get("image_base64"));

        String outputPath = saveInferenceFrameCandidate(annotatedImageBase64, false, alarmPayload, traceId);
        if (StrUtil.isNotBlank(outputPath)) {
            return outputPath;
        }
        outputPath = saveInferenceFrameCandidate(alarmImageBase64, false, alarmPayload, traceId);
        if (StrUtil.isNotBlank(outputPath)) {
            return outputPath;
        }
        outputPath = saveInferenceFrameCandidate(rawImageBase64, true, alarmPayload, traceId);
        if (StrUtil.isNotBlank(outputPath)) {
            return outputPath;
        }
        return "";
    }

    private String saveInferenceFrameCandidate(String imageBase64,
                                               boolean annotateRawImage,
                                               List<Map<String, Object>> alarmPayload,
                                               String traceId) {
        if (StrUtil.isBlank(imageBase64)) {
            return "";
        }
        try {
            byte[] payload = decodeBase64Payload(imageBase64);
            if (payload.length == 0) {
                return "";
            }
            if (annotateRawImage && alarmPayload != null && !alarmPayload.isEmpty()) {
                byte[] annotatedPayload = annotateImagePayload(payload, alarmPayload);
                if (annotatedPayload != null && annotatedPayload.length > 0) {
                    payload = annotatedPayload;
                }
            }
            File outputDir = ensureUploadDirectory();
            if (outputDir == null) {
                return "";
            }
            File outputFile = new File(outputDir, UUID.randomUUID().toString().replace("-", "") + ".jpg");
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                out.write(payload);
                out.flush();
            }
            return outputFile.getAbsolutePath();
        } catch (Exception ex) {
            log.warn("save annotated image from inference failed, trace_id={}, ex={}", traceId, ex.getMessage());
            return "";
        }
    }

    private byte[] decodeBase64Payload(String imageBase64) {
        String normalized = StrUtil.trimToEmpty(imageBase64);
        int markerIndex = normalized.indexOf("base64,");
        if (markerIndex >= 0) {
            normalized = normalized.substring(markerIndex + "base64,".length());
        } else if (normalized.startsWith("data:")) {
            int commaIndex = normalized.indexOf(',');
            if (commaIndex >= 0 && commaIndex + 1 < normalized.length()) {
                normalized = normalized.substring(commaIndex + 1);
            }
        }
        return Base64.getDecoder().decode(normalized);
    }

    private byte[] annotateImagePayload(byte[] payload, List<Map<String, Object>> alarmPayload) {
        if (payload == null || payload.length == 0 || alarmPayload == null || alarmPayload.isEmpty()) {
            return payload;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(payload));
            if (image == null) {
                return payload;
            }
            drawAlertOverlay(image, alarmPayload);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            log.warn("annotate image payload failed, ex={}", ex.getMessage());
            return payload;
        }
    }

    private File ensureUploadDirectory() {
        if (StrUtil.isBlank(uploadDir)) {
            return null;
        }
        File directory = new File(uploadDir);
        if (directory.exists() || directory.mkdirs()) {
            return directory;
        }
        log.warn("failed to create upload directory: {}", directory.getAbsolutePath());
        return null;
    }

    private String annotateSnapshot(String snapshotPath, List<Map<String, Object>> alarmPayload) {
        if (StrUtil.isBlank(snapshotPath)) {
            return "";
        }
        File imageFile = new File(snapshotPath);
        if (!imageFile.exists()) {
            return imageFile.getAbsolutePath();
        }
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                return imageFile.getAbsolutePath();
            }
            drawAlertOverlay(image, alarmPayload);
            ImageIO.write(image, "jpg", imageFile);
        } catch (Exception ex) {
            log.warn("annotate snapshot failed, path={}, ex={}", snapshotPath, ex.getMessage());
        }
        return imageFile.getAbsolutePath();
    }

    private void drawAlertOverlay(BufferedImage image, List<Map<String, Object>> alarmPayload) {
        if (image == null || alarmPayload == null || alarmPayload.isEmpty()) {
            return;
        }
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(255, 59, 48));
            graphics.setStroke(new BasicStroke(Math.max(2F, image.getWidth() / 480F)));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(14, image.getWidth() / 64)));
            for (Map<String, Object> item : alarmPayload) {
                List<Integer> bbox = normalizeBbox(item == null ? null : item.get("bbox"));
                if (bbox.size() != 4) {
                    continue;
                }
                int x1 = clamp(bbox.get(0), 0, image.getWidth() - 1);
                int y1 = clamp(bbox.get(1), 0, image.getHeight() - 1);
                int x2 = clamp(bbox.get(2), 0, image.getWidth() - 1);
                int y2 = clamp(bbox.get(3), 0, image.getHeight() - 1);
                int width = Math.max(1, x2 - x1);
                int height = Math.max(1, y2 - y1);
                graphics.drawRect(x1, y1, width, height);
                drawLabel(graphics, buildAlertLabelText(item), x1, y1, image.getWidth());
            }
        } finally {
            graphics.dispose();
        }
    }

    private void drawLabel(Graphics2D graphics, String text, int x, int y, int imageWidth) {
        String labelText = StrUtil.blankToDefault(text, "alert");
        int textWidth = graphics.getFontMetrics().stringWidth(labelText);
        int textHeight = graphics.getFontMetrics().getHeight();
        int boxX = clamp(x, 0, Math.max(0, imageWidth - textWidth - 12));
        int boxY = Math.max(0, y - textHeight - 6);
        graphics.setColor(new Color(255, 59, 48));
        graphics.fillRect(boxX, boxY, textWidth + 10, textHeight + 4);
        graphics.setColor(Color.WHITE);
        graphics.drawString(labelText, boxX + 5, boxY + textHeight - 4);
        graphics.setColor(new Color(255, 59, 48));
    }

    private String buildAlertLabelText(Map<String, Object> item) {
        String label = resolveAlertLabel(item);
        Double confidence = firstDouble(item == null ? null : item.get("score"), item == null ? null : item.get("confidence"));
        if (confidence == null) {
            return label;
        }
        return label + String.format(" %.2f", confidence);
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private void broadcastOverlay(Long cameraId, List<Map<String, Object>> alarmPayload, String traceId) {
        try {
            ReportMessage overlayMessage = new ReportMessage();
            overlayMessage.setType("REPORT");
            overlayMessage.setCameraId(String.valueOf(cameraId));
            overlayMessage.setParams(JSON.toJSONString(toOverlayItems(alarmPayload)));
            overlayMessage.setTraceId(traceId);
            reportWebsocket.sendToAll(JSON.toJSONString(overlayMessage));
        } catch (Exception ex) {
            log.warn("report overlay broadcast failed, trace_id={}, ex={}", traceId, ex.getMessage());
        }
    }

    private List<Map<String, Object>> toOverlayItems(List<Map<String, Object>> alarmPayload) {
        List<Map<String, Object>> overlayItems = new ArrayList<>();
        if (alarmPayload == null) {
            return overlayItems;
        }
        for (Map<String, Object> item : alarmPayload) {
            List<Integer> bbox = normalizeBbox(item == null ? null : item.get("bbox"));
            if (bbox.isEmpty()) {
                continue;
            }
            Map<String, Object> overlayItem = new HashMap<>();
            overlayItem.put("position", bbox);
            overlayItem.put("type", resolveAlertLabel(item));
            Double confidence = firstDouble(item == null ? null : item.get("score"), item == null ? null : item.get("confidence"));
            if (confidence != null) {
                overlayItem.put("confidence", confidence);
            }
            overlayItems.add(overlayItem);
        }
        return overlayItems;
    }

    private List<Integer> normalizeBbox(Object bboxObj) {
        List<Integer> bbox = new ArrayList<>();
        if (!(bboxObj instanceof List<?>)) {
            return bbox;
        }
        List<?> list = (List<?>) bboxObj;
        if (list.size() < 4) {
            return bbox;
        }
        for (int i = 0; i < 4; i++) {
            Object item = list.get(i);
            if (!(item instanceof Number)) {
                return new ArrayList<>();
            }
            bbox.add(((Number) item).intValue());
        }
        return bbox;
    }

    private Double firstDouble(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(String.valueOf(value));
                } catch (Exception ignored) {
                    // ignore invalid value and continue searching
                }
            }
        }
        return null;
    }

    private String resolveAlertLabel(Map<String, Object> item) {
        if (item == null) {
            return "";
        }
        String labelZh = String.valueOf(item.get("label_zh") == null ? "" : item.get("label_zh")).trim();
        if (StrUtil.isNotBlank(labelZh)) {
            return labelZh;
        }
        return String.valueOf(item.get("label") == null ? "" : item.get("label")).trim();
    }

    private String captureSnapshot(Camera camera) {
        if (camera == null || StrUtil.isBlank(camera.getRtspUrl()) || takePhoto == null) {
            return "";
        }
        try {
            String ffmpegBin = configService.getByValTag("media_ffmpeg_bin");
            String snapshotFileName = takePhoto.take(camera.getRtspUrl(), ffmpegBin);
            if (StrUtil.isBlank(snapshotFileName)) {
                return "";
            }
            File snapshotFile = new File(snapshotFileName);
            if (snapshotFile.isAbsolute() || StrUtil.isBlank(uploadDir)) {
                return snapshotFile.getAbsolutePath();
            }
            return new File(uploadDir, snapshotFileName).getAbsolutePath();
        } catch (Exception ex) {
            log.warn("capture snapshot failed for camera_id={}, ex={}", camera.getId(), ex.getMessage());
            return "";
        }
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
