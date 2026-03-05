package com.yihecode.camera.ai.web.api;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.service.InferenceIdempotencyService;
import com.yihecode.camera.ai.service.InferenceReportBridgeService;
import com.yihecode.camera.ai.service.InferenceRoutingService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping({"/api/inference"})
public class InferenceApiController {

    @Autowired
    private InferenceRoutingService inferenceRoutingService;

    @Autowired
    private InferenceReportBridgeService inferenceReportBridgeService;

    @Autowired
    private InferenceIdempotencyService inferenceIdempotencyService;

    @RequestMapping(value = {"/health"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult health() {
        String traceId = nextTraceId();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("backend_type", inferenceRoutingService.currentBackendType());
            data.put("upstream", inferenceRoutingService.health(traceId));
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference health api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("backend_type", inferenceRoutingService.currentBackendType());
            return JsonResultUtils.fail("inference health api failed", data);
        }
    }

    @RequestMapping(value = {"/test"}, method = {RequestMethod.POST})
    @ResponseBody
    public JsonResult test(@RequestBody(required = false) Map<String, Object> body,
                           @RequestParam(value = "camera_id", required = false) Long cameraId,
                           @RequestParam(value = "model_id", required = false) Long modelId,
                           @RequestParam(value = "source", required = false) String source) {
        String requestTraceId = nextTraceId();
        try {
            InferenceRequest request = buildTestRequest(requestTraceId, body, cameraId, modelId, source);
            String traceId = request.getTraceId();
            InferenceResult result = inferenceRoutingService.infer(request);
            String routedBackend = inferenceRoutingService.backendTypeForCamera(request.getCameraId());
            String backendType = firstString(result.getBackendType(), routedBackend, inferenceRoutingService.currentBackendType());

            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("backend_type", backendType);
            data.put("request", request.toPayload());
            data.put("result", result.toMap());
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference test api failed, trace_id={}", requestTraceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", requestTraceId);
            data.put("backend_type", inferenceRoutingService.currentBackendType());
            return JsonResultUtils.fail("inference test api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/dispatch"}, method = {RequestMethod.POST})
    @ResponseBody
    public JsonResult dispatch(@RequestBody(required = false) Map<String, Object> body,
                               @RequestParam(value = "camera_id", required = false) Long cameraId,
                               @RequestParam(value = "model_id", required = false) Long modelId,
                               @RequestParam(value = "algorithm_id", required = false) Long algorithmId,
                               @RequestParam(value = "source", required = false) String source,
                               @RequestParam(value = "persist_report", required = false) Integer persistReportFlag) {
        String requestTraceId = nextTraceId();
        try {
            Map<String, Object> payload = body == null ? new HashMap<>() : body;
            InferenceRequest request = buildTestRequest(requestTraceId, payload, cameraId, modelId, source);
            String traceId = request.getTraceId();
            String routedBackend = inferenceRoutingService.backendTypeForCamera(request.getCameraId());
            Long finalAlgorithmId = firstLong(toLong(payload.get("algorithm_id")), algorithmId, request.getModelId());
            boolean persistReport = toBooleanFlag(payload.get("persist_report"), persistReportFlag == null || persistReportFlag != 0);
            Long frameTimestampMs = extractFrameTimestampMs(request.getFrameMeta());

            Map<String, Object> idempotentData = buildIdempotentData(persistReport, traceId, request.getCameraId(), frameTimestampMs);
            boolean duplicate = toBooleanFlag(idempotentData.get("duplicate"), false);

            InferenceResult result;
            if (persistReport && duplicate) {
                result = new InferenceResult();
                result.setTraceId(traceId);
                result.setCameraId(request.getCameraId());
                result.setLatencyMs(0L);
                result.setDetections(new ArrayList<>());
                result.setBackendType(routedBackend);
                result.setAttempt(0);
            } else {
                result = inferenceRoutingService.infer(request);
            }
            String backendType = firstString(result.getBackendType(), routedBackend, inferenceRoutingService.currentBackendType());

            Map<String, Object> reportData = new HashMap<>();
            reportData.put("trace_id", traceId);
            reportData.put("enabled", persistReport);
            if (persistReport && duplicate) {
                reportData.put("status", "duplicate");
                reportData.put("reason", "idempotent duplicate request");
                reportData.put("persisted", false);
                reportData.put("broadcasted", false);
            } else if (persistReport) {
                reportData.putAll(inferenceReportBridgeService.persistAndBroadcast(request.getCameraId(), finalAlgorithmId, result, traceId));
            } else {
                reportData.put("status", "skipped");
                reportData.put("reason", "persist_report disabled");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("backend_type", backendType);
            data.put("request", request.toPayload());
            data.put("result", result.toMap());
            data.put("algorithm_id", finalAlgorithmId);
            data.put("idempotent", idempotentData);
            data.put("report", reportData);
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dispatch api failed, trace_id={}", requestTraceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", requestTraceId);
            data.put("backend_type", inferenceRoutingService.currentBackendType());
            return JsonResultUtils.fail("inference dispatch api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/route"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult route(@RequestBody(required = false) Map<String, Object> body,
                            @RequestParam(value = "camera_id", required = false) Long cameraId) {
        String traceId = nextTraceId();
        try {
            Map<String, Object> payload = body == null ? new HashMap<>() : body;
            Long finalCameraId = firstLong(toLong(payload.get("camera_id")), cameraId, 1L);
            String globalBackend = inferenceRoutingService.currentBackendType();
            String routedBackend = inferenceRoutingService.backendTypeForCamera(finalCameraId);

            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("camera_id", finalCameraId);
            data.put("global_backend_type", globalBackend);
            data.put("backend_type", routedBackend);
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference route api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            return JsonResultUtils.fail("inference route api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/idempotent/stats"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult idempotentStats() {
        String traceId = nextTraceId();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("idempotent", inferenceIdempotencyService.stats());
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference idempotent stats api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            return JsonResultUtils.fail("inference idempotent stats api failed: " + e.getMessage(), data);
        }
    }

    private InferenceRequest buildTestRequest(String traceId, Map<String, Object> body, Long cameraId, Long modelId, String source) {
        Map<String, Object> payload = body == null ? new HashMap<>() : body;

        String finalTraceId = firstString(payload.get("trace_id"), traceId, traceId);
        Long finalCameraId = firstLong(toLong(payload.get("camera_id")), cameraId, 1L);
        Long finalModelId = firstLong(toLong(payload.get("model_id")), modelId, 1L);
        String finalSource = firstString(payload.get("source"), source, "test://frame");
        List<Map<String, Object>> finalRoi = toMapList(payload.get("roi"));

        Map<String, Object> frame = new HashMap<>();
        Object frameObj = payload.get("frame");
        if (frameObj instanceof Map) {
            frame.putAll((Map<? extends String, ?>) frameObj);
        }
        Object frameMetaObj = payload.get("frame_meta");
        if (frameMetaObj instanceof Map) {
            frame.putAll((Map<? extends String, ?>) frameMetaObj);
        }
        if (!frame.containsKey("source")) {
            frame.put("source", finalSource);
        }
        if (!frame.containsKey("timestamp_ms")) {
            frame.put("timestamp_ms", System.currentTimeMillis());
        }

        InferenceRequest request = new InferenceRequest();
        request.setTraceId(finalTraceId);
        request.setCameraId(finalCameraId);
        request.setModelId(finalModelId);
        request.setFrameMeta(frame);
        request.setRoi(finalRoi);
        return request;
    }

    private Map<String, Object> buildIdempotentData(boolean persistReport, String traceId, Long cameraId, Long timestampMs) {
        if (!persistReport) {
            Map<String, Object> data = new HashMap<>();
            data.put("enabled", false);
            data.put("duplicate", false);
            data.put("status", "disabled");
            data.put("trace_id", traceId);
            data.put("camera_id", cameraId);
            data.put("timestamp_ms", timestampMs);
            return data;
        }
        return inferenceIdempotencyService.checkAndMark(traceId, cameraId, timestampMs);
    }

    private Long extractFrameTimestampMs(Map<String, Object> frameMeta) {
        if (frameMeta == null) {
            return System.currentTimeMillis();
        }
        Long timestampMs = toLong(frameMeta.get("timestamp_ms"));
        if (timestampMs == null || timestampMs <= 0) {
            timestampMs = System.currentTimeMillis();
            frameMeta.put("timestamp_ms", timestampMs);
        }
        return timestampMs;
    }

    private List<Map<String, Object>> toMapList(Object value) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!(value instanceof List)) {
            return list;
        }

        List<?> srcList = (List<?>) value;
        for (Object item : srcList) {
            if (item instanceof Map) {
                list.add(new HashMap<>((Map<? extends String, ?>) item));
            }
        }
        return list;
    }

    private boolean toBooleanFlag(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        return defaultValue;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Long firstLong(Long first, Long second, Long fallback) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return fallback;
    }

    private String firstString(Object first, String second, String fallback) {
        if (first != null && StrUtil.isNotBlank(String.valueOf(first))) {
            return String.valueOf(first);
        }
        if (StrUtil.isNotBlank(second)) {
            return second;
        }
        return fallback;
    }

    private String nextTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
