package com.yihecode.camera.ai.web.api;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.service.InferenceDeadLetterService;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping({"/api/inference"})
public class InferenceApiController {

    private static final int ROUTE_BATCH_MAX_CAMERA_IDS = 500;

    @Autowired
    private InferenceRoutingService inferenceRoutingService;

    @Autowired
    private InferenceReportBridgeService inferenceReportBridgeService;

    @Autowired
    private InferenceIdempotencyService inferenceIdempotencyService;

    @Autowired
    private InferenceDeadLetterService inferenceDeadLetterService;

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
            Map<String, Object> deadLetter = recordDispatchDeadLetter(requestTraceId, body, cameraId, modelId, algorithmId, persistReportFlag, e);
            data.put("dead_letter", deadLetter);
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
            String overrideBackend = inferenceRoutingService.overrideBackendForCamera(finalCameraId);
            String overrideSource = inferenceRoutingService.overrideSourceForCamera(finalCameraId);
            String routedBackend = inferenceRoutingService.backendTypeForCamera(finalCameraId);

            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("camera_id", finalCameraId);
            data.put("global_backend_type", globalBackend);
            data.put("backend_type", routedBackend);
            data.put("override_hit", StrUtil.isNotBlank(overrideBackend));
            if (StrUtil.isNotBlank(overrideBackend)) {
                data.put("override_backend_type", overrideBackend);
                data.put("override_source", overrideSource);
            }
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

    @RequestMapping(value = {"/circuit/status"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult circuitStatus() {
        String traceId = nextTraceId();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("backend_type", inferenceRoutingService.currentBackendType());
            data.put("circuit", inferenceRoutingService.circuitStatus(traceId));
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference circuit status api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("backend_type", inferenceRoutingService.currentBackendType());
            return JsonResultUtils.fail("inference circuit status api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/circuit/reset"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult circuitReset() {
        String traceId = nextTraceId();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("backend_type", inferenceRoutingService.currentBackendType());
            data.put("circuit", inferenceRoutingService.resetCircuit(traceId));
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference circuit reset api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("backend_type", inferenceRoutingService.currentBackendType());
            return JsonResultUtils.fail("inference circuit reset api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/dead-letter/stats"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult deadLetterStats() {
        String traceId = nextTraceId();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("dead_letter", inferenceDeadLetterService.stats());
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dead-letter stats api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            return JsonResultUtils.fail("inference dead-letter stats api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/dead-letter/latest"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult deadLetterLatest(@RequestParam(value = "limit", required = false) Integer limit) {
        String traceId = nextTraceId();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("dead_letter", inferenceDeadLetterService.latest(limit));
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dead-letter latest api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            return JsonResultUtils.fail("inference dead-letter latest api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/dead-letter/clear"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult deadLetterClear() {
        String traceId = nextTraceId();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("dead_letter", inferenceDeadLetterService.clear());
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dead-letter clear api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            return JsonResultUtils.fail("inference dead-letter clear api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/route/batch"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult routeBatch(@RequestBody(required = false) Map<String, Object> body,
                                 @RequestParam(value = "camera_ids", required = false) String cameraIdsText,
                                 @RequestParam(value = "cameras", required = false) String camerasText,
                                 @RequestParam(value = "camera_range", required = false) String cameraRangeText,
                                 @RequestParam(value = "range", required = false) String rangeText,
                                 @RequestParam(value = "max_camera_ids", required = false) Integer maxCameraIds) {
        return routeBatchInternal(body, cameraIdsText, camerasText, cameraRangeText, rangeText, maxCameraIds);
    }

    JsonResult routeBatch(Map<String, Object> body, String cameraIdsText) {
        return routeBatchInternal(body, cameraIdsText, null, null, null, null);
    }

    JsonResult routeBatch(Map<String, Object> body,
                          String cameraIdsText,
                          String camerasText,
                          String cameraRangeText,
                          String rangeText) {
        return routeBatchInternal(body, cameraIdsText, camerasText, cameraRangeText, rangeText, null);
    }

    private JsonResult routeBatchInternal(Map<String, Object> body,
                                          String cameraIdsText,
                                          String camerasText,
                                          String cameraRangeText,
                                          String rangeText,
                                          Integer maxCameraIds) {
        String traceId = nextTraceId();
        try {
            Map<String, Object> payload = body == null ? new HashMap<>() : body;
            int effectiveMaxCameraIds = resolveMaxCameraIds(payload, maxCameraIds);
            CameraIdResolveResult resolveResult = resolveCameraIds(payload, cameraIdsText, camerasText, cameraRangeText, rangeText, effectiveMaxCameraIds);
            List<Long> cameraIds = resolveResult.cameraIds;
            boolean defaultFallbackUsed = cameraIds.isEmpty();
            if (defaultFallbackUsed) {
                cameraIds.add(1L);
            }

            String globalBackend = inferenceRoutingService.currentBackendType();
            List<Map<String, Object>> routeList = new ArrayList<>();
            for (Long itemCameraId : cameraIds) {
                String overrideBackend = inferenceRoutingService.overrideBackendForCamera(itemCameraId);
                String overrideSource = inferenceRoutingService.overrideSourceForCamera(itemCameraId);
                String routedBackend = inferenceRoutingService.backendTypeForCamera(itemCameraId);

                Map<String, Object> route = new HashMap<>();
                route.put("camera_id", itemCameraId);
                route.put("backend_type", routedBackend);
                route.put("override_hit", StrUtil.isNotBlank(overrideBackend));
                if (StrUtil.isNotBlank(overrideBackend)) {
                    route.put("override_backend_type", overrideBackend);
                    route.put("override_source", overrideSource);
                }
                routeList.add(route);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("global_backend_type", globalBackend);
            data.put("route_list", routeList);
            data.put("resolved_camera_count", routeList.size());
            data.put("default_fallback_used", defaultFallbackUsed);
            data.put("truncated", resolveResult.truncated);
            data.put("max_camera_ids", effectiveMaxCameraIds);
            data.put("max_camera_ids_cap", ROUTE_BATCH_MAX_CAMERA_IDS);
            data.put("input_token_count", resolveResult.inputTokenCount);
            data.put("expanded_candidate_count", resolveResult.expandedCandidateCount);
            data.put("invalid_token_count", resolveResult.invalidTokenCount);
            data.put("duplicate_filtered_count", resolveResult.duplicateFilteredCount);
            data.put("hit_sources", resolveResult.hitSources);
            data.put("truncated_source", resolveResult.truncatedSource);
            data.put("source_stats", resolveResult.sourceStats);
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference route batch api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            return JsonResultUtils.fail("inference route batch api failed: " + e.getMessage(), data);
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

    private Map<String, Object> recordDispatchDeadLetter(String requestTraceId,
                                                         Map<String, Object> body,
                                                         Long cameraId,
                                                         Long modelId,
                                                         Long algorithmId,
                                                         Integer persistReportFlag,
                                                         Exception exception) {
        Map<String, Object> payload = body == null ? new HashMap<>() : body;
        Map<String, Object> event = new HashMap<>();
        String traceId = firstString(payload.get("trace_id"), requestTraceId, requestTraceId);
        event.put("status", "dispatch_exception");
        event.put("trace_id", traceId);
        event.put("request_trace_id", requestTraceId);
        event.put("camera_id", firstLong(toLong(payload.get("camera_id")), cameraId, null));
        event.put("model_id", firstLong(toLong(payload.get("model_id")), modelId, null));
        event.put("algorithm_id", firstLong(toLong(payload.get("algorithm_id")), algorithmId, null));
        event.put("persist_report", toBooleanFlag(payload.get("persist_report"), persistReportFlag == null || persistReportFlag != 0));
        event.put("backend_type", inferenceRoutingService.currentBackendType());
        event.put("error_type", exception == null ? null : exception.getClass().getSimpleName());
        event.put("error_message", exception == null ? "" : exception.getMessage());
        event.put("created_at_ms", System.currentTimeMillis());
        try {
            return inferenceDeadLetterService.record(event);
        } catch (Exception deadLetterEx) {
            Map<String, Object> fallback = new HashMap<>(event);
            fallback.put("dead_letter_recorded", false);
            fallback.put("dead_letter_error", deadLetterEx.getMessage());
            return fallback;
        }
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

    private CameraIdResolveResult resolveCameraIds(Map<String, Object> payload,
                                                   String queryCameraIds,
                                                   String queryCameras,
                                                   String queryCameraRange,
                                                   String queryRange,
                                                   int maxCameraIds) {
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        CameraIdParseStats stats = new CameraIdParseStats();
        Object bodyCameraIds = payload == null ? null : payload.get("camera_ids");
        Object bodyCameras = payload == null ? null : payload.get("cameras");
        Object bodyCameraRange = payload == null ? null : payload.get("camera_range");
        Object bodyRange = payload == null ? null : payload.get("range");

        boolean truncated = addCameraIds(ordered, bodyCameraIds, maxCameraIds, stats, "body_camera_ids");
        if (!truncated) {
            truncated = addCameraIds(ordered, bodyCameras, maxCameraIds, stats, "body_cameras");
        }
        if (!truncated) {
            truncated = addCameraIds(ordered, bodyCameraRange, maxCameraIds, stats, "body_camera_range");
        }
        if (!truncated) {
            truncated = addCameraIds(ordered, bodyRange, maxCameraIds, stats, "body_range");
        }
        if (!truncated) {
            truncated = addCameraIds(ordered, queryCameraIds, maxCameraIds, stats, "query_camera_ids");
        }
        if (!truncated) {
            truncated = addCameraIds(ordered, queryCameras, maxCameraIds, stats, "query_cameras");
        }
        if (!truncated) {
            truncated = addCameraIds(ordered, queryCameraRange, maxCameraIds, stats, "query_camera_range");
        }
        if (!truncated) {
            truncated = addCameraIds(ordered, queryRange, maxCameraIds, stats, "query_range");
        }
        return new CameraIdResolveResult(
                new ArrayList<>(ordered),
                truncated,
                stats.inputTokenCount,
                stats.expandedCandidateCount,
                stats.invalidTokenCount,
                stats.duplicateFilteredCount,
                new ArrayList<>(stats.hitSources),
                stats.truncatedSource,
                stats.toSourceStatsMap()
        );
    }

    private int resolveMaxCameraIds(Map<String, Object> payload, Integer queryMaxCameraIds) {
        Long bodyMaxCameraIds = payload == null ? null : toLong(payload.get("max_camera_ids"));
        if (bodyMaxCameraIds != null) {
            return normalizeMaxCameraIds(bodyMaxCameraIds);
        }
        if (queryMaxCameraIds != null) {
            return normalizeMaxCameraIds(queryMaxCameraIds.longValue());
        }
        return ROUTE_BATCH_MAX_CAMERA_IDS;
    }

    private int normalizeMaxCameraIds(Long rawMaxCameraIds) {
        if (rawMaxCameraIds == null) {
            return ROUTE_BATCH_MAX_CAMERA_IDS;
        }
        long value = rawMaxCameraIds;
        if (value <= 0) {
            return 1;
        }
        if (value > ROUTE_BATCH_MAX_CAMERA_IDS) {
            return ROUTE_BATCH_MAX_CAMERA_IDS;
        }
        return (int) value;
    }

    private boolean addCameraIds(LinkedHashSet<Long> ordered,
                                 Object value,
                                 int maxSize,
                                 CameraIdParseStats stats,
                                 String sourceName) {
        if (ordered == null || value == null) {
            return false;
        }
        if (ordered.size() >= maxSize) {
            markTruncatedSource(stats, sourceName);
            return true;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (Object item : list) {
                if (addCameraIds(ordered, item, maxSize, stats, sourceName)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof String) {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return false;
            }
            String[] parts = text.split(",");
            for (String part : parts) {
                if (addCameraIdToken(ordered, part, maxSize, stats, sourceName)) {
                    return true;
                }
            }
            return false;
        }
        stats.inputTokenCount += 1;
        SourceParseStats sourceStats = getOrCreateSourceStats(stats, sourceName);
        sourceStats.inputTokenCount += 1;
        Long cameraId = toLong(value);
        if (cameraId == null) {
            stats.invalidTokenCount += 1;
            sourceStats.invalidTokenCount += 1;
            return false;
        }
        return addCameraIdValue(ordered, cameraId, maxSize, stats, sourceName);
    }

    private boolean addCameraIdToken(LinkedHashSet<Long> ordered,
                                     String rawToken,
                                     int maxSize,
                                     CameraIdParseStats stats,
                                     String sourceName) {
        if (ordered == null || rawToken == null || ordered.size() >= maxSize) {
            markTruncatedSource(stats, sourceName);
            return ordered != null && ordered.size() >= maxSize;
        }
        String token = rawToken.trim();
        if (token.isEmpty()) {
            return false;
        }
        stats.inputTokenCount += 1;
        SourceParseStats sourceStats = getOrCreateSourceStats(stats, sourceName);
        sourceStats.inputTokenCount += 1;

        String[] rangeParts = token.split("-", -1);
        if (rangeParts.length == 2) {
            Long start = toLong(rangeParts[0].trim());
            Long end = toLong(rangeParts[1].trim());
            if (start != null && end != null) {
                long step = start <= end ? 1L : -1L;
                long current = start;
                while (true) {
                    if (addCameraIdValue(ordered, current, maxSize, stats, sourceName)) {
                        return true;
                    }
                    if (current == end) {
                        return false;
                    }
                    current += step;
                }
            }
        }

        Long cameraId = toLong(token);
        if (cameraId == null) {
            stats.invalidTokenCount += 1;
            sourceStats.invalidTokenCount += 1;
            return false;
        }
        return addCameraIdValue(ordered, cameraId, maxSize, stats, sourceName);
    }

    private boolean addCameraIdValue(LinkedHashSet<Long> ordered,
                                     Long cameraId,
                                     int maxSize,
                                     CameraIdParseStats stats,
                                     String sourceName) {
        if (ordered == null || cameraId == null) {
            return false;
        }
        markSourceHit(stats, sourceName);
        SourceParseStats sourceStats = getOrCreateSourceStats(stats, sourceName);
        if (ordered.size() >= maxSize) {
            markTruncatedSource(stats, sourceName);
            sourceStats.truncated = true;
            return true;
        }
        if (stats != null) {
            stats.expandedCandidateCount += 1;
        }
        sourceStats.expandedCandidateCount += 1;
        if (ordered.contains(cameraId)) {
            if (stats != null) {
                stats.duplicateFilteredCount += 1;
            }
            sourceStats.duplicateFilteredCount += 1;
            return false;
        }
        ordered.add(cameraId);
        sourceStats.uniqueAddedCount += 1;
        return false;
    }

    private void markSourceHit(CameraIdParseStats stats, String sourceName) {
        if (stats == null || StrUtil.isBlank(sourceName)) {
            return;
        }
        stats.hitSources.add(sourceName);
    }

    private void markTruncatedSource(CameraIdParseStats stats, String sourceName) {
        if (stats == null || StrUtil.isBlank(sourceName)) {
            return;
        }
        if (StrUtil.isBlank(stats.truncatedSource)) {
            stats.truncatedSource = sourceName;
        }
        SourceParseStats sourceStats = getOrCreateSourceStats(stats, sourceName);
        sourceStats.truncated = true;
    }

    private SourceParseStats getOrCreateSourceStats(CameraIdParseStats stats, String sourceName) {
        if (stats == null || StrUtil.isBlank(sourceName)) {
            return new SourceParseStats();
        }
        SourceParseStats sourceStats = stats.sourceStats.get(sourceName);
        if (sourceStats != null) {
            return sourceStats;
        }
        SourceParseStats created = new SourceParseStats();
        stats.sourceStats.put(sourceName, created);
        return created;
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

    private static class CameraIdResolveResult {
        private final List<Long> cameraIds;
        private final boolean truncated;
        private final int inputTokenCount;
        private final int expandedCandidateCount;
        private final int invalidTokenCount;
        private final int duplicateFilteredCount;
        private final List<String> hitSources;
        private final String truncatedSource;
        private final Map<String, Map<String, Object>> sourceStats;

        private CameraIdResolveResult(List<Long> cameraIds,
                                      boolean truncated,
                                      int inputTokenCount,
                                      int expandedCandidateCount,
                                      int invalidTokenCount,
                                      int duplicateFilteredCount,
                                      List<String> hitSources,
                                      String truncatedSource,
                                      Map<String, Map<String, Object>> sourceStats) {
            this.cameraIds = cameraIds;
            this.truncated = truncated;
            this.inputTokenCount = inputTokenCount;
            this.expandedCandidateCount = expandedCandidateCount;
            this.invalidTokenCount = invalidTokenCount;
            this.duplicateFilteredCount = duplicateFilteredCount;
            this.hitSources = hitSources == null ? new ArrayList<>() : hitSources;
            this.truncatedSource = truncatedSource;
            this.sourceStats = sourceStats == null ? new LinkedHashMap<>() : sourceStats;
        }
    }

    private static class CameraIdParseStats {
        private int inputTokenCount;
        private int expandedCandidateCount;
        private int invalidTokenCount;
        private int duplicateFilteredCount;
        private final LinkedHashSet<String> hitSources = new LinkedHashSet<>();
        private String truncatedSource;
        private final Map<String, SourceParseStats> sourceStats = new LinkedHashMap<>();

        private Map<String, Map<String, Object>> toSourceStatsMap() {
            Map<String, Map<String, Object>> data = new LinkedHashMap<>();
            for (Map.Entry<String, SourceParseStats> entry : sourceStats.entrySet()) {
                data.put(entry.getKey(), entry.getValue().toMap());
            }
            return data;
        }
    }

    private static class SourceParseStats {
        private int inputTokenCount;
        private int expandedCandidateCount;
        private int invalidTokenCount;
        private int duplicateFilteredCount;
        private int uniqueAddedCount;
        private boolean truncated;

        private Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("input_token_count", inputTokenCount);
            data.put("expanded_candidate_count", expandedCandidateCount);
            data.put("invalid_token_count", invalidTokenCount);
            data.put("duplicate_filtered_count", duplicateFilteredCount);
            data.put("unique_added_count", uniqueAddedCount);
            data.put("truncated", truncated);
            return data;
        }
    }
}
