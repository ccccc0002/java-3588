package com.yihecode.camera.ai.web.api;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.InferenceDeadLetterService;
import com.yihecode.camera.ai.service.InferenceIdempotencyService;
import com.yihecode.camera.ai.service.InferenceReportBridgeService;
import com.yihecode.camera.ai.service.InferenceRoutingService;
import com.yihecode.camera.ai.service.PluginRouteResolverService;
import com.yihecode.camera.ai.service.PluginInferenceDispatchService;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final String DEAD_LETTER_REPLAY_BATCH_MAX_LIMIT_CONFIG_KEY = "infer_dead_letter_replay_batch_max_limit";
    private static final int DEAD_LETTER_REPLAY_BATCH_DEFAULT_LIMIT = 200;
    private static final int DEAD_LETTER_REPLAY_BATCH_HARD_CAP = 2000;

    @Autowired
    private InferenceRoutingService inferenceRoutingService;

    @Autowired
    private InferenceReportBridgeService inferenceReportBridgeService;

    @Autowired
    private InferenceIdempotencyService inferenceIdempotencyService;

    @Autowired
    private InferenceDeadLetterService inferenceDeadLetterService;

    @Autowired
    private ConfigService configService;

    @Autowired(required = false)
    private PluginRouteResolverService pluginRouteResolverService;

    @Autowired(required = false)
    private PluginInferenceDispatchService pluginInferenceDispatchService;

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
            Map<String, Object> payload = body == null ? new HashMap<>() : body;
            InferenceRequest request = buildTestRequest(requestTraceId, payload, cameraId, modelId, source);
            Map<String, Object> pluginRoute = resolvePluginRoute(payload);
            applyPluginRoute(request, pluginRoute);
            String pluginBackendHint = extractPluginBackendHint(pluginRoute);
            boolean pluginDispatchable = isPluginDispatchable(pluginRoute);
            boolean pluginDispatched = false;
            String pluginDispatchError = null;
            String traceId = request.getTraceId();
            InferenceResult result;
            if (pluginDispatchable) {
                try {
                    result = pluginInferenceDispatchService.infer(request, pluginRoute);
                    pluginDispatched = true;
                } catch (Exception pluginEx) {
                    pluginDispatchError = pluginEx.getMessage();
                    result = StrUtil.isNotBlank(pluginBackendHint)
                            ? inferenceRoutingService.infer(request, pluginBackendHint)
                            : inferenceRoutingService.infer(request);
                }
            } else {
                result = StrUtil.isNotBlank(pluginBackendHint)
                        ? inferenceRoutingService.infer(request, pluginBackendHint)
                        : inferenceRoutingService.infer(request);
            }
            String routedBackend = StrUtil.isNotBlank(pluginBackendHint)
                    ? inferenceRoutingService.backendTypeForCamera(request.getCameraId(), pluginBackendHint)
                    : inferenceRoutingService.backendTypeForCamera(request.getCameraId());
            String backendType = firstString(result.getBackendType(), routedBackend, inferenceRoutingService.currentBackendType());
            Map<String, Object> pluginDispatch = buildPluginDispatchData(pluginRoute, pluginDispatched,
                    pluginDispatchable ? (pluginDispatched ? null : "plugin_dispatch_failed")
                            : (shouldAttachPluginRoute(pluginRoute) ? "plugin_not_dispatchable" : null),
                    pluginDispatchError);

            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("backend_type", backendType);
            data.put("request", request.toPayload());
            data.put("result", result.toMap());
            if (shouldAttachPluginRoute(pluginRoute)) {
                data.put("plugin_route", pluginRoute);
                data.put("plugin_dispatch", pluginDispatch);
            }
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
        Map<String, Object> resolvedPluginRoute = new HashMap<>();
        String pluginDispatchError = null;
        try {
            Map<String, Object> payload = body == null ? new HashMap<>() : body;
            InferenceRequest request = buildTestRequest(requestTraceId, payload, cameraId, modelId, source);
            Map<String, Object> pluginRoute = resolvePluginRoute(payload);
            resolvedPluginRoute = pluginRoute;
            applyPluginRoute(request, pluginRoute);
            String pluginBackendHint = extractPluginBackendHint(pluginRoute);
            boolean pluginDispatchable = isPluginDispatchable(pluginRoute);
            boolean pluginDispatched = false;
            String traceId = request.getTraceId();
            String routedBackend = StrUtil.isNotBlank(pluginBackendHint)
                    ? inferenceRoutingService.backendTypeForCamera(request.getCameraId(), pluginBackendHint)
                    : inferenceRoutingService.backendTypeForCamera(request.getCameraId());
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
            } else if (pluginDispatchable) {
                try {
                    result = pluginInferenceDispatchService.infer(request, pluginRoute);
                    pluginDispatched = true;
                } catch (Exception pluginEx) {
                    pluginDispatchError = pluginEx.getMessage();
                    result = StrUtil.isNotBlank(pluginBackendHint)
                            ? inferenceRoutingService.infer(request, pluginBackendHint)
                            : inferenceRoutingService.infer(request);
                }
            } else {
                result = StrUtil.isNotBlank(pluginBackendHint)
                        ? inferenceRoutingService.infer(request, pluginBackendHint)
                        : inferenceRoutingService.infer(request);
            }
            String backendType = firstString(result.getBackendType(), routedBackend, inferenceRoutingService.currentBackendType());
            Map<String, Object> pluginDispatch = buildPluginDispatchData(pluginRoute, pluginDispatched,
                    pluginDispatchable ? (pluginDispatched ? null : "plugin_dispatch_failed")
                            : (shouldAttachPluginRoute(pluginRoute) && !duplicate ? "plugin_not_dispatchable" : null),
                    pluginDispatchError);

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
            if (shouldAttachPluginRoute(pluginRoute)) {
                data.put("plugin_route", pluginRoute);
                data.put("plugin_dispatch", pluginDispatch);
            }
            data.put("idempotent", idempotentData);
            data.put("report", reportData);
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dispatch api failed, trace_id={}", requestTraceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", requestTraceId);
            data.put("backend_type", inferenceRoutingService.currentBackendType());
            Map<String, Object> deadLetter = recordDispatchDeadLetter(requestTraceId, body, cameraId, modelId, algorithmId, persistReportFlag, resolvedPluginRoute, pluginDispatchError, e);
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
            Map<String, Object> pluginRoute = resolvePluginRoute(payload);
            String pluginBackendHint = extractPluginBackendHint(pluginRoute);
            String routedBackend = StrUtil.isNotBlank(pluginBackendHint)
                    ? inferenceRoutingService.backendTypeForCamera(finalCameraId, pluginBackendHint)
                    : inferenceRoutingService.backendTypeForCamera(finalCameraId);

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
            if (shouldAttachPluginRoute(pluginRoute)) {
                data.put("plugin_route", pluginRoute);
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

    public JsonResult deadLetterStats() {
        return deadLetterStats(null, null, null, null, null, null);
    }

    @RequestMapping(value = {"/dead-letter/stats"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult deadLetterStats(@RequestParam(value = "only_retryable", required = false) Integer onlyRetryableFlag,
                                      @RequestParam(value = "only_exhausted", required = false) Integer onlyExhaustedFlag,
                                      @RequestParam(value = "backend_type", required = false) String backendType,
                                      @RequestParam(value = "plugin_id", required = false) String pluginId,
                                      @RequestParam(value = "plugin_registration_id", required = false) String pluginRegistrationId,
                                      @RequestParam(value = "error_type", required = false) String errorType) {
        String traceId = nextTraceId();
        try {
            boolean onlyRetryable = toBooleanFlag(onlyRetryableFlag, false);
            boolean onlyExhausted = toBooleanFlag(onlyExhaustedFlag, false);
            String selectedBackendType = trimToNull(backendType);
            String selectedPluginId = trimToNull(pluginId);
            String selectedPluginRegistrationId = trimToNull(pluginRegistrationId);
            String selectedErrorType = trimToNull(errorType);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("dead_letter", inferenceDeadLetterService.stats(
                    onlyRetryable,
                    onlyExhausted,
                    selectedBackendType,
                    selectedPluginId,
                    selectedPluginRegistrationId,
                    selectedErrorType
            ));
            data.putAll(buildDeadLetterFilterData(onlyRetryable, onlyExhausted, selectedBackendType,
                    selectedPluginId, selectedPluginRegistrationId, selectedErrorType));
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dead-letter stats api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            return JsonResultUtils.fail("inference dead-letter stats api failed: " + e.getMessage(), data);
        }
    }

    public JsonResult deadLetterLatest(Integer limit,
                                       Integer onlyRetryableFlag,
                                       Integer onlyExhaustedFlag,
                                       String backendType,
                                       String pluginId,
                                       String pluginRegistrationId) {
        return deadLetterLatest(limit, onlyRetryableFlag, onlyExhaustedFlag, backendType, pluginId, pluginRegistrationId, null);
    }

    @RequestMapping(value = {"/dead-letter/latest"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult deadLetterLatest(@RequestParam(value = "limit", required = false) Integer limit,
                                       @RequestParam(value = "only_retryable", required = false) Integer onlyRetryableFlag,
                                       @RequestParam(value = "only_exhausted", required = false) Integer onlyExhaustedFlag,
                                       @RequestParam(value = "backend_type", required = false) String backendType,
                                       @RequestParam(value = "plugin_id", required = false) String pluginId,
                                       @RequestParam(value = "plugin_registration_id", required = false) String pluginRegistrationId,
                                       @RequestParam(value = "error_type", required = false) String errorType) {
        String traceId = nextTraceId();
        try {
            boolean onlyRetryable = toBooleanFlag(onlyRetryableFlag, false);
            boolean onlyExhausted = toBooleanFlag(onlyExhaustedFlag, false);
            String selectedBackendType = trimToNull(backendType);
            String selectedPluginId = trimToNull(pluginId);
            String selectedPluginRegistrationId = trimToNull(pluginRegistrationId);
            String selectedErrorType = trimToNull(errorType);
            List<Map<String, Object>> latest = inferenceDeadLetterService.latest(
                    limit,
                    onlyRetryable,
                    onlyExhausted,
                    selectedBackendType,
                    selectedPluginId,
                    selectedPluginRegistrationId,
                    selectedErrorType
            );
            List<Map<String, Object>> display = new ArrayList<>();
            for (Map<String, Object> item : latest) {
                display.add(enrichDeadLetterReplayBudget(item));
            }
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("requested_limit", limit);
            data.put("returned_count", display.size());
            data.put("dead_letter", display);
            data.putAll(buildDeadLetterFilterData(onlyRetryable, onlyExhausted, selectedBackendType,
                    selectedPluginId, selectedPluginRegistrationId, selectedErrorType));
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dead-letter latest api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            return JsonResultUtils.fail("inference dead-letter latest api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/dead-letter/get"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult deadLetterGet(@RequestParam(value = "dead_letter_id", required = false) Long deadLetterId) {
        String traceId = nextTraceId();
        try {
            Map<String, Object> deadLetter = inferenceDeadLetterService.findById(deadLetterId);
            if (deadLetter == null) {
                Map<String, Object> data = new HashMap<>();
                data.put("trace_id", traceId);
                data.put("dead_letter_id", deadLetterId);
                return JsonResultUtils.fail("inference dead-letter get failed: dead letter not found", data);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("dead_letter", enrichDeadLetterReplayBudget(deadLetter));
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dead-letter get api failed, trace_id={}, dead_letter_id={}", traceId, deadLetterId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("dead_letter_id", deadLetterId);
            return JsonResultUtils.fail("inference dead-letter get api failed: " + e.getMessage(), data);
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

    @RequestMapping(value = {"/dead-letter/remove"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult deadLetterRemove(@RequestParam(value = "dead_letter_id", required = false) Long deadLetterId) {
        String traceId = nextTraceId();
        try {
            boolean removed = inferenceDeadLetterService.removeById(deadLetterId);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("dead_letter_id", deadLetterId);
            data.put("removed", removed);
            if (!removed) {
                return JsonResultUtils.fail("inference dead-letter remove failed: dead letter not found", data);
            }
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dead-letter remove api failed, trace_id={}, dead_letter_id={}", traceId, deadLetterId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("dead_letter_id", deadLetterId);
            data.put("removed", false);
            return JsonResultUtils.fail("inference dead-letter remove api failed: " + e.getMessage(), data);
        }
    }

    @RequestMapping(value = {"/dead-letter/replay"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult deadLetterReplay(@RequestParam(value = "dead_letter_id", required = false) Long deadLetterId,
                                       @RequestParam(value = "persist_report", required = false) Integer persistReportFlag,
                                       @RequestParam(value = "ack_on_success", required = false) Integer ackOnSuccessFlag) {
        String traceId = nextTraceId();
        boolean replayLockAcquired = false;
        Map<String, Object> replayPluginRoute = null;
        boolean pluginDispatchable = false;
        boolean pluginDispatched = false;
        String pluginDispatchError = null;
        InferenceRequest replayRequest = null;
        Long replayAlgorithmId = null;
        String replayBackendType = null;
        try {
            int maxReplayAttempts = inferenceDeadLetterService.maxReplayAttempts();
            Map<String, Object> acquireResult = inferenceDeadLetterService.tryAcquireReplay(deadLetterId, traceId, maxReplayAttempts);
            boolean exists = toBooleanFlag(acquireResult.get("exists"), false);
            boolean acquired = toBooleanFlag(acquireResult.get("acquired"), false);
            String acquireReason = firstString(acquireResult.get("reason"), "unknown", "unknown");
            Map<String, Object> entry = toMap(acquireResult.get("entry"));
            if (!exists || entry.isEmpty()) {
                return JsonResultUtils.fail("inference dead-letter replay failed: dead letter not found",
                        buildReplayFailureData(traceId, deadLetterId, maxReplayAttempts, "not_found", false, false));
            }
            if (!acquired) {
                Map<String, Object> data = buildReplayFailureData(traceId, deadLetterId, maxReplayAttempts,
                        acquireReason, "in_progress".equals(acquireReason), false);
                if ("replay_exhausted".equals(acquireReason)) {
                    enrichReplayFailureBudget(data, maxReplayAttempts, toInt(entry.get("replay_count"), 0));
                    data.put("replay_exhausted", true);
                    return JsonResultUtils.fail("inference dead-letter replay failed: replay attempts exhausted", data);
                }
                if ("in_progress".equals(acquireReason)) {
                    enrichReplayFailureBudget(data, maxReplayAttempts, toInt(entry.get("replay_count"), 0));
                    data.put("replay_lock_trace_id", entry.get("replay_lock_trace_id"));
                    data.put("replay_lock_at_ms", entry.get("replay_lock_at_ms"));
                    return JsonResultUtils.fail("inference dead-letter replay failed: replay already in progress", data);
                }
                return JsonResultUtils.fail("inference dead-letter replay failed: dead letter unavailable", data);
            }
            replayLockAcquired = true;

            int replayCount = toInt(entry.get("replay_count"), 0);

            Map<String, Object> payload = extractReplayPayload(entry);
            payload.put("trace_id", traceId);
            Map<String, Object> frame = toMap(payload.get("frame"));
            frame.put("replay_source", "dead_letter_replay");
            frame.put("replay_dead_letter_id", deadLetterId);
            frame.put("replay_count", replayCount + 1);
            payload.put("frame", frame);
            InferenceRequest request = buildTestRequest(traceId, payload, null, null, null);
            replayRequest = request;
            replayPluginRoute = firstPluginRoute(payload, entry);
            applyPluginRoute(request, replayPluginRoute);
            String pluginBackendHint = extractPluginBackendHint(replayPluginRoute);
            pluginDispatchable = isPluginDispatchable(replayPluginRoute);
            pluginDispatched = false;
            pluginDispatchError = null;
            String routedBackend = StrUtil.isNotBlank(pluginBackendHint)
                    ? inferenceRoutingService.backendTypeForCamera(request.getCameraId(), pluginBackendHint)
                    : inferenceRoutingService.backendTypeForCamera(request.getCameraId());
            replayBackendType = routedBackend;
            replayAlgorithmId = firstLong(toLong(entry.get("algorithm_id")), null, request.getModelId());
            InferenceResult result;
            if (pluginDispatchable) {
                try {
                    result = pluginInferenceDispatchService.infer(request, replayPluginRoute);
                    pluginDispatched = true;
                } catch (Exception pluginEx) {
                    pluginDispatchError = pluginEx.getMessage();
                    result = StrUtil.isNotBlank(pluginBackendHint)
                            ? inferenceRoutingService.infer(request, pluginBackendHint)
                            : inferenceRoutingService.infer(request);
                }
            } else {
                result = StrUtil.isNotBlank(pluginBackendHint)
                        ? inferenceRoutingService.infer(request, pluginBackendHint)
                        : inferenceRoutingService.infer(request);
            }
            String backendType = firstString(result.getBackendType(), routedBackend, inferenceRoutingService.currentBackendType());
            replayBackendType = backendType;
            Map<String, Object> pluginDispatch = buildPluginDispatchData(replayPluginRoute, pluginDispatched,
                    pluginDispatchable ? (pluginDispatched ? null : "plugin_dispatch_failed")
                            : (shouldAttachPluginRoute(replayPluginRoute) ? "plugin_not_dispatchable" : null),
                    pluginDispatchError);

            boolean entryPersistReport = toBooleanFlag(entry.get("persist_report"), false);
            boolean persistReport = toBooleanFlag(persistReportFlag, entryPersistReport);

            Map<String, Object> reportData = new HashMap<>();
            reportData.put("trace_id", traceId);
            reportData.put("enabled", persistReport);
            if (persistReport) {
                reportData.putAll(inferenceReportBridgeService.persistAndBroadcast(request.getCameraId(), replayAlgorithmId, result, traceId));
            } else {
                reportData.put("status", "skipped");
                reportData.put("reason", "persist_report disabled");
            }

            Map<String, Object> replayMeta = inferenceDeadLetterService.markReplay(deadLetterId, true, traceId, "ok");
            boolean ackOnSuccess = toBooleanFlag(ackOnSuccessFlag, false);
            boolean acked = ackOnSuccess && inferenceDeadLetterService.removeById(deadLetterId);
            int replayCountAfter = replayMeta == null ? replayCount + 1 : toInt(replayMeta.get("replay_count"), replayCount + 1);
            Map<String, Object> replayBudget = buildReplayBudget(maxReplayAttempts, replayCountAfter);

            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("dead_letter_id", deadLetterId);
            data.put("backend_type", replayBackendType);
            data.put("request", request.toPayload());
            data.put("result", result.toMap());
            data.put("algorithm_id", replayAlgorithmId);
            if (shouldAttachPluginRoute(replayPluginRoute)) {
                data.put("plugin_route", replayPluginRoute);
                data.put("plugin_dispatch", pluginDispatch);
            }
            data.put("report", reportData);
            data.put("replay_in_progress", false);
            data.put("replay_exhausted", replayBudget.get("replay_exhausted"));
            data.put("acked", acked);
            data.put("replay_meta", replayMeta);
            data.put("replay_budget", replayBudget);
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            Map<String, Object> replayMeta = null;
            if (replayLockAcquired) {
                replayMeta = inferenceDeadLetterService.markReplay(deadLetterId, false, traceId, e.getMessage());
            }
            log.error("inference dead-letter replay api failed, trace_id={}, dead_letter_id={}", traceId, deadLetterId, e);
            int maxReplayAttempts = inferenceDeadLetterService.maxReplayAttempts();
            Map<String, Object> data = buildReplayFailureData(traceId, deadLetterId, maxReplayAttempts,
                    "execution_error", false, false);
            if (StrUtil.isNotBlank(replayBackendType)) {
                data.put("backend_type", replayBackendType);
            }
            if (replayRequest != null) {
                data.put("request", replayRequest.toPayload());
            }
            if (replayAlgorithmId != null) {
                data.put("algorithm_id", replayAlgorithmId);
            }
            if (replayMeta != null) {
                int replayCount = toInt(replayMeta.get("replay_count"), 0);
                Map<String, Object> replayBudget = buildReplayBudget(maxReplayAttempts, replayCount);
                data.put("replay_exhausted", replayBudget.get("replay_exhausted"));
                data.put("replay_meta", replayMeta);
                data.put("replay_budget", replayBudget);
            }
            if (shouldAttachPluginRoute(replayPluginRoute)) {
                data.put("plugin_route", replayPluginRoute);
                data.put("plugin_dispatch", buildPluginDispatchData(replayPluginRoute, pluginDispatched,
                        pluginDispatchable ? (pluginDispatched ? null : "plugin_dispatch_failed") : "plugin_not_dispatchable",
                        pluginDispatchError));
            }
            return JsonResultUtils.fail("inference dead-letter replay api failed: " + e.getMessage(), data);
        } finally {
            if (replayLockAcquired) {
                inferenceDeadLetterService.releaseReplay(deadLetterId, traceId);
            }
        }
    }

    public JsonResult deadLetterReplayBatch(@RequestBody(required = false) Map<String, Object> body,
                                            Integer limit,
                                            Integer persistReportFlag,
                                            Integer ackOnSuccessFlag,
                                            Integer onlyRetryableFlag,
                                            Integer onlyExhaustedFlag,
                                            Integer dryRunFlag,
                                            String deadLetterIdsText,
                                            String idsText,
                                            Integer stopOnErrorFlag,
                                            Integer offset,
                                            Integer strictResumeFlag,
                                            Integer expectedTotalSelectedCountFlag) {
        return deadLetterReplayBatch(body, limit, persistReportFlag, ackOnSuccessFlag, onlyRetryableFlag, onlyExhaustedFlag, dryRunFlag,
                deadLetterIdsText, idsText, stopOnErrorFlag, offset, strictResumeFlag, expectedTotalSelectedCountFlag, null, null, null, null);
    }

    @RequestMapping(value = {"/dead-letter/replay/batch"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public JsonResult deadLetterReplayBatch(@RequestBody(required = false) Map<String, Object> body,
                                            @RequestParam(value = "limit", required = false) Integer limit,
                                            @RequestParam(value = "persist_report", required = false) Integer persistReportFlag,
                                            @RequestParam(value = "ack_on_success", required = false) Integer ackOnSuccessFlag,
                                            @RequestParam(value = "only_retryable", required = false) Integer onlyRetryableFlag,
                                            @RequestParam(value = "only_exhausted", required = false) Integer onlyExhaustedFlag,
                                            @RequestParam(value = "dry_run", required = false) Integer dryRunFlag,
                                            @RequestParam(value = "dead_letter_ids", required = false) String deadLetterIdsText,
                                            @RequestParam(value = "ids", required = false) String idsText,
                                            @RequestParam(value = "stop_on_error", required = false) Integer stopOnErrorFlag,
                                            @RequestParam(value = "offset", required = false) Integer offset,
                                            @RequestParam(value = "strict_resume", required = false) Integer strictResumeFlag,
                                            @RequestParam(value = "expected_total_selected_count", required = false) Integer expectedTotalSelectedCountFlag,
                                            @RequestParam(value = "backend_type", required = false) String backendType,
                                            @RequestParam(value = "plugin_id", required = false) String pluginId,
                                            @RequestParam(value = "plugin_registration_id", required = false) String pluginRegistrationId,
                                            @RequestParam(value = "error_type", required = false) String errorType) {
        String traceId = nextTraceId();
        try {
            Map<String, Object> payload = body == null ? new HashMap<>() : body;
            Integer requestedLimit = firstInteger(payload.get("limit"), limit);
            Integer requestedOffset = firstInteger(payload.get("offset"), offset);
            Integer effectivePersistReportFlag = firstInteger(payload.get("persist_report"), persistReportFlag);
            Integer effectiveAckOnSuccessFlag = firstInteger(payload.get("ack_on_success"), ackOnSuccessFlag);
            Integer expectedTotalSelectedCount = firstInteger(payload.get("expected_total_selected_count"), expectedTotalSelectedCountFlag);
            String expectedWindowFingerprint = firstString(payload.get("expected_window_fingerprint"), null, null);
            String expectedResumeToken = firstString(payload.get("expected_resume_token"), null, null);
            Object bodyDeadLetterIds = payload.get("dead_letter_ids");
            Object bodyIds = payload.get("ids");
            boolean onlyRetryable = toBooleanFlag(firstNonNull(payload.get("only_retryable"), onlyRetryableFlag), true);
            boolean onlyExhausted = toBooleanFlag(firstNonNull(payload.get("only_exhausted"), onlyExhaustedFlag), false);
            String selectedBackendType = trimToNull(firstString(payload.get("backend_type"), backendType, null));
            String selectedPluginId = trimToNull(firstString(payload.get("plugin_id"), pluginId, null));
            String selectedPluginRegistrationId = trimToNull(firstString(payload.get("plugin_registration_id"), pluginRegistrationId, null));
            String selectedErrorType = trimToNull(firstString(payload.get("error_type"), errorType, null));
            boolean dryRun = toBooleanFlag(firstNonNull(payload.get("dry_run"), dryRunFlag), false);
            boolean stopOnError = toBooleanFlag(firstNonNull(payload.get("stop_on_error"), stopOnErrorFlag), false);
            boolean strictResume = toBooleanFlag(firstNonNull(payload.get("strict_resume"), strictResumeFlag), false);
            int maxLimit = resolveDeadLetterReplayBatchMaxLimit();
            int effectiveLimit = normalizeDeadLetterReplayBatchLimit(requestedLimit, maxLimit);
            int effectiveOffset = normalizeReplayBatchOffset(requestedOffset);
            int fetchLimit = resolveReplayBatchFetchLimit(effectiveLimit, effectiveOffset, maxLimit);
            boolean truncated = requestedLimit != null && requestedLimit > 0 && requestedLimit > effectiveLimit;
            List<Long> selectedIds = resolveDeadLetterIds(fetchLimit, bodyDeadLetterIds, bodyIds, deadLetterIdsText, idsText);
            boolean explicitIdsMode = !selectedIds.isEmpty();
            List<Map<String, Object>> sourceCandidates;
            if (explicitIdsMode) {
                sourceCandidates = new ArrayList<>();
                for (Long itemId : selectedIds) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("dead_letter_id", itemId);
                    sourceCandidates.add(item);
                }
            } else if (selectedBackendType == null && selectedPluginId == null && selectedPluginRegistrationId == null && selectedErrorType == null) {
                sourceCandidates = inferenceDeadLetterService.latest(fetchLimit, onlyRetryable, onlyExhausted);
            } else {
                sourceCandidates = inferenceDeadLetterService.latest(fetchLimit, onlyRetryable, onlyExhausted,
                        selectedBackendType, selectedPluginId, selectedPluginRegistrationId, selectedErrorType);
            }

            int totalSelectedCount = sourceCandidates.size();
            String actualWindowFingerprint = computeReplayBatchWindowFingerprint(sourceCandidates);
            int appliedOffset = Math.min(effectiveOffset, totalSelectedCount);
            String selectionSource = explicitIdsMode ? "explicit_ids" : "latest";
            String actualResumeToken = computeReplayBatchResumeToken(selectionSource, totalSelectedCount, effectiveLimit, appliedOffset, actualWindowFingerprint);
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (int i = appliedOffset; i < totalSelectedCount && candidates.size() < effectiveLimit; i++) {
                candidates.add(sourceCandidates.get(i));
            }
            int nextOffset = appliedOffset + candidates.size();
            boolean hasMore = nextOffset < totalSelectedCount;
            if (strictResume) {
                if (expectedTotalSelectedCount == null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("trace_id", traceId);
                    data.put("strict_resume", true);
                    data.put("expected_total_selected_count", null);
                    data.put("actual_total_selected_count", totalSelectedCount);
                    data.put("effective_limit", effectiveLimit);
                    data.put("effective_offset", appliedOffset);
                    return JsonResultUtils.fail("inference dead-letter replay batch failed: expected_total_selected_count required when strict_resume enabled", data);
                }
                if (!expectedTotalSelectedCount.equals(totalSelectedCount)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("trace_id", traceId);
                    data.put("strict_resume", true);
                    data.put("expected_total_selected_count", expectedTotalSelectedCount);
                    data.put("actual_total_selected_count", totalSelectedCount);
                    data.put("expected_window_fingerprint", expectedWindowFingerprint);
                    data.put("actual_window_fingerprint", actualWindowFingerprint);
                    data.put("expected_resume_token", expectedResumeToken);
                    data.put("actual_resume_token", actualResumeToken);
                    data.put("effective_limit", effectiveLimit);
                    data.put("effective_offset", appliedOffset);
                    return JsonResultUtils.fail("inference dead-letter replay batch failed: selected window changed", data);
                }
                if (StrUtil.isNotBlank(expectedWindowFingerprint) && !expectedWindowFingerprint.equals(actualWindowFingerprint)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("trace_id", traceId);
                    data.put("strict_resume", true);
                    data.put("expected_total_selected_count", expectedTotalSelectedCount);
                    data.put("actual_total_selected_count", totalSelectedCount);
                    data.put("expected_window_fingerprint", expectedWindowFingerprint);
                    data.put("actual_window_fingerprint", actualWindowFingerprint);
                    data.put("expected_resume_token", expectedResumeToken);
                    data.put("actual_resume_token", actualResumeToken);
                    data.put("effective_limit", effectiveLimit);
                    data.put("effective_offset", appliedOffset);
                    return JsonResultUtils.fail("inference dead-letter replay batch failed: selected window fingerprint changed", data);
                }
                if (StrUtil.isNotBlank(expectedResumeToken) && !expectedResumeToken.equals(actualResumeToken)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("trace_id", traceId);
                    data.put("strict_resume", true);
                    data.put("expected_total_selected_count", expectedTotalSelectedCount);
                    data.put("actual_total_selected_count", totalSelectedCount);
                    data.put("expected_window_fingerprint", expectedWindowFingerprint);
                    data.put("actual_window_fingerprint", actualWindowFingerprint);
                    data.put("expected_resume_token", expectedResumeToken);
                    data.put("actual_resume_token", actualResumeToken);
                    data.put("effective_limit", effectiveLimit);
                    data.put("effective_offset", appliedOffset);
                    return JsonResultUtils.fail("inference dead-letter replay batch failed: resume token changed", data);
                }
            }

            int successCount = 0;
            int failedCount = 0;
            int failedReplayInProgressCount = 0;
            int failedReplayExhaustedCount = 0;
            int failedOtherCount = 0;
            int dryRunCount = 0;
            List<Long> successDeadLetterIds = new ArrayList<>();
            List<Long> failedDeadLetterIds = new ArrayList<>();
            List<Long> dryRunDeadLetterIds = new ArrayList<>();
            boolean stoppedOnError = false;
            Long stoppedDeadLetterId = null;
            String stoppedReason = null;
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> candidate : candidates) {
                Long deadLetterId = toLong(candidate.get("dead_letter_id"));
                if (deadLetterId == null) {
                    Map<String, Object> invalid = new HashMap<>();
                    invalid.put("dead_letter_id", null);
                    invalid.put("code", -1);
                    invalid.put("msg", "invalid dead letter id");
                    results.add(invalid);
                    failedCount++;
                    failedOtherCount++;
                    if (stopOnError) {
                        stoppedOnError = true;
                        stoppedReason = "invalid dead letter id";
                        break;
                    }
                    continue;
                }

                if (dryRun) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("dead_letter_id", deadLetterId);
                    item.put("code", 0);
                    item.put("msg", "dry_run");
                    item.put("planned", true);
                    results.add(item);
                    dryRunCount++;
                    dryRunDeadLetterIds.add(deadLetterId);
                    continue;
                }

                JsonResult replayResp = deadLetterReplay(deadLetterId, effectivePersistReportFlag, effectiveAckOnSuccessFlag);
                Map<String, Object> replayData = toMap(replayResp.getData());
                Map<String, Object> replayBudget = toMap(replayData.get("replay_budget"));
                Map<String, Object> item = new HashMap<>();
                item.put("dead_letter_id", deadLetterId);
                item.put("code", replayResp.getCode());
                item.put("msg", replayResp.getMsg());
                item.put("trace_id", replayData.get("trace_id"));
                item.put("backend_type", replayData.get("backend_type"));
                item.put("plugin_route", replayData.get("plugin_route"));
                item.put("plugin_dispatch", replayData.get("plugin_dispatch"));
                item.put("algorithm_id", replayData.get("algorithm_id"));
                item.put("request", replayData.get("request"));
                item.put("result", replayData.get("result"));
                item.put("acked", replayData.get("acked"));
                item.put("report", replayData.get("report"));
                item.put("replay_meta", replayData.get("replay_meta"));
                item.put("failure_reason", replayData.get("failure_reason"));
                item.put("max_replay_attempts", replayData.containsKey("max_replay_attempts") ? replayData.get("max_replay_attempts") : replayBudget.get("max_replay_attempts"));
                item.put("replay_lock_trace_id", replayData.get("replay_lock_trace_id"));
                item.put("replay_lock_at_ms", replayData.get("replay_lock_at_ms"));
                item.put("replay_count", replayData.containsKey("replay_count") ? replayData.get("replay_count") : replayBudget.get("replay_count"));
                item.put("remaining_replay_attempts", replayData.containsKey("remaining_replay_attempts") ? replayData.get("remaining_replay_attempts") : replayBudget.get("remaining_replay_attempts"));
                item.put("replay_budget", replayData.get("replay_budget"));
                item.put("replay_exhausted", replayData.get("replay_exhausted"));
                item.put("replay_in_progress", replayData.get("replay_in_progress"));
                results.add(item);
                if (replayResp.getCode() == 0) {
                    successCount++;
                    successDeadLetterIds.add(deadLetterId);
                } else {
                    failedCount++;
                    failedDeadLetterIds.add(deadLetterId);
                    boolean replayInProgress = toBooleanFlag(replayData.get("replay_in_progress"), false);
                    boolean replayExhausted = toBooleanFlag(replayData.get("replay_exhausted"), false);
                    if (replayInProgress) {
                        failedReplayInProgressCount++;
                    } else if (replayExhausted) {
                        failedReplayExhaustedCount++;
                    } else {
                        failedOtherCount++;
                    }
                    if (stopOnError) {
                        stoppedOnError = true;
                        stoppedDeadLetterId = deadLetterId;
                        stoppedReason = firstString(replayResp.getMsg(), "replay failed", "replay failed");
                        break;
                    }
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            data.put("requested_limit", requestedLimit);
            data.put("requested_offset", requestedOffset == null ? 0 : requestedOffset);
            data.put("effective_limit", effectiveLimit);
            data.put("effective_offset", appliedOffset);
            data.put("next_offset", nextOffset);
            data.put("has_more", hasMore);
            data.put("total_selected_count", totalSelectedCount);
            data.put("strict_resume", strictResume);
            data.put("expected_total_selected_count", expectedTotalSelectedCount);
            data.put("actual_total_selected_count", totalSelectedCount);
            data.put("expected_window_fingerprint", expectedWindowFingerprint);
            data.put("actual_window_fingerprint", actualWindowFingerprint);
            data.put("expected_resume_token", expectedResumeToken);
            data.put("resume_token", actualResumeToken);
            data.put("max_limit", maxLimit);
            data.put("truncated", truncated);
            data.put("only_retryable", onlyRetryable);
            data.put("only_exhausted", onlyExhausted);
            data.put("backend_type", selectedBackendType);
            data.put("plugin_id", selectedPluginId);
            data.put("plugin_registration_id", selectedPluginRegistrationId);
            data.put("error_type", selectedErrorType);
            data.put("dry_run", dryRun);
            data.put("stop_on_error", stopOnError);
            data.put("stopped_on_error", stoppedOnError);
            data.put("stopped_dead_letter_id", stoppedDeadLetterId);
            data.put("stopped_reason", stoppedReason);
            data.put("selection_source", selectionSource);
            data.put("selected_count", candidates.size());
            data.put("processed_count", results.size());
            data.put("success_count", successCount);
            data.put("failed_count", failedCount);
            data.put("failed_replay_in_progress_count", failedReplayInProgressCount);
            data.put("failed_replay_exhausted_count", failedReplayExhaustedCount);
            data.put("failed_other_count", failedOtherCount);
            data.put("dry_run_count", dryRunCount);
            data.put("success_dead_letter_ids", successDeadLetterIds);
            data.put("failed_dead_letter_ids", failedDeadLetterIds);
            data.put("dry_run_dead_letter_ids", dryRunDeadLetterIds);
            data.put("remaining_count", Math.max(candidates.size() - results.size(), 0));
            data.put("results", results);
            return JsonResultUtils.success(data);
        } catch (Exception e) {
            log.error("inference dead-letter replay batch api failed, trace_id={}", traceId, e);
            Map<String, Object> data = new HashMap<>();
            data.put("trace_id", traceId);
            return JsonResultUtils.fail("inference dead-letter replay batch api failed: " + e.getMessage(), data);
        }
    }

    private List<Long> resolveDeadLetterIds(int maxSize, Object... idSources) {
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        if (idSources != null) {
            for (Object source : idSources) {
                addDeadLetterIds(ordered, source, maxSize);
            }
        }
        return new ArrayList<>(ordered);
    }

    private void addDeadLetterIds(LinkedHashSet<Long> ordered, Object value, int maxSize) {
        if (ordered == null || value == null || ordered.size() >= maxSize) {
            return;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (Object item : list) {
                if (ordered.size() >= maxSize) {
                    return;
                }
                addDeadLetterIds(ordered, item, maxSize);
            }
            return;
        }
        if (value instanceof String) {
            String idsText = String.valueOf(value);
            if (StrUtil.isBlank(idsText)) {
                return;
            }
            String[] parts = idsText.split(",");
            for (String part : parts) {
                if (ordered.size() >= maxSize) {
                    return;
                }
                if (part == null) {
                    continue;
                }
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                Long id = toLong(token);
                if (id == null) {
                    continue;
                }
                ordered.add(id);
            }
            return;
        }
        Long id = toLong(value);
        if (id != null) {
            ordered.add(id);
        }
    }

    private int resolveDeadLetterReplayBatchMaxLimit() {
        return toPositiveIntWithinRange(
                configService == null ? null : configService.getByValTag(DEAD_LETTER_REPLAY_BATCH_MAX_LIMIT_CONFIG_KEY),
                DEAD_LETTER_REPLAY_BATCH_DEFAULT_LIMIT,
                DEAD_LETTER_REPLAY_BATCH_HARD_CAP
        );
    }

    private int normalizeDeadLetterReplayBatchLimit(Integer requestedLimit, int maxLimit) {
        int effectiveMax = Math.max(1, maxLimit);
        int defaultLimit = Math.min(DEAD_LETTER_REPLAY_BATCH_DEFAULT_LIMIT, effectiveMax);
        if (requestedLimit == null || requestedLimit <= 0) {
            return defaultLimit;
        }
        return Math.min(requestedLimit, effectiveMax);
    }

    private int normalizeReplayBatchOffset(Integer requestedOffset) {
        if (requestedOffset == null || requestedOffset <= 0) {
            return 0;
        }
        return requestedOffset;
    }

    private int resolveReplayBatchFetchLimit(int effectiveLimit, int effectiveOffset, int maxLimit) {
        int effectiveMax = Math.max(1, maxLimit);
        long desired = (long) effectiveLimit + (long) effectiveOffset;
        if (desired <= 0) {
            return Math.min(effectiveLimit, effectiveMax);
        }
        return (int) Math.min(desired, (long) effectiveMax);
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


    private Map<String, Object> resolvePluginRoute(Map<String, Object> payload) {
        if (pluginRouteResolverService == null || !pluginRouteResolverService.hasSelector(payload)) {
            return null;
        }
        Map<String, Object> resolved = pluginRouteResolverService.resolve(payload);
        return resolved == null ? null : new LinkedHashMap<>(resolved);
    }

    private void applyPluginRoute(InferenceRequest request, Map<String, Object> pluginRoute) {
        if (request == null || !shouldAttachPluginRoute(pluginRoute)) {
            return;
        }
        request.setPluginRoute(new LinkedHashMap<>(pluginRoute));
    }

    private boolean shouldAttachPluginRoute(Map<String, Object> pluginRoute) {
        return pluginRoute != null && toBooleanFlag(pluginRoute.get("requested"), false);
    }

    private String extractPluginBackendHint(Map<String, Object> pluginRoute) {
        if (!shouldAttachPluginRoute(pluginRoute)) {
            return null;
        }
        return firstString(pluginRoute.get("backend_hint"), null, null);
    }

    private boolean isPluginDispatchable(Map<String, Object> pluginRoute) {
        return pluginInferenceDispatchService != null && shouldAttachPluginRoute(pluginRoute)
                && pluginInferenceDispatchService.isDispatchable(pluginRoute);
    }

    private Map<String, Object> buildPluginDispatchData(Map<String, Object> pluginRoute,
                                                        boolean dispatched,
                                                        String fallbackReason,
                                                        String errorMessage) {
        if (!shouldAttachPluginRoute(pluginRoute)) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> plugin = toMap(pluginRoute.get("plugin"));
        data.put("requested", true);
        data.put("dispatched", dispatched);
        data.put("fallback", !dispatched);
        data.put("fallback_reason", fallbackReason);
        data.put("registration_id", plugin.get("registration_id"));
        data.put("plugin_id", plugin.get("plugin_id"));
        data.put("runtime", firstString(plugin.get("runtime"), extractPluginBackendHint(pluginRoute), null));
        data.put("health_url", plugin.get("health_url"));
        data.put("error_message", errorMessage);
        return data;
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
                                                         Map<String, Object> pluginRoute,
                                                         String pluginDispatchError,
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
        if (shouldAttachPluginRoute(pluginRoute)) {
            event.put("plugin_route", new HashMap<>(pluginRoute));
            event.put("plugin_dispatch", buildPluginDispatchData(pluginRoute, false,
                    isPluginDispatchable(pluginRoute) ? "plugin_dispatch_failed" : "plugin_not_dispatchable",
                    pluginDispatchError));
        }
        event.put("request_payload", buildDeadLetterRequestPayload(payload, requestTraceId, cameraId, modelId, null, pluginRoute));
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

    private Map<String, Object> buildDeadLetterRequestPayload(Map<String, Object> payload,
                                                              String requestTraceId,
                                                              Long cameraId,
                                                              Long modelId,
                                                              String source,
                                                              Map<String, Object> pluginRoute) {
        try {
            InferenceRequest request = buildTestRequest(requestTraceId, payload, cameraId, modelId, source);
            applyPluginRoute(request, pluginRoute);
            return new HashMap<>(request.toPayload());
        } catch (Exception ignored) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("trace_id", firstString(payload.get("trace_id"), requestTraceId, requestTraceId));
            fallback.put("camera_id", firstLong(toLong(payload.get("camera_id")), cameraId, 1L));
            fallback.put("model_id", firstLong(toLong(payload.get("model_id")), modelId, 1L));
            fallback.put("frame", new HashMap<>());
            fallback.put("roi", Collections.emptyList());
            return fallback;
        }
    }

    private Map<String, Object> extractReplayPayload(Map<String, Object> entry) {
        if (entry == null) {
            return new HashMap<>();
        }
        Object payloadObj = entry.get("request_payload");
        if (payloadObj instanceof Map) {
            return new HashMap<>((Map<? extends String, ?>) payloadObj);
        }
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("trace_id", entry.get("trace_id"));
        fallback.put("camera_id", entry.get("camera_id"));
        fallback.put("model_id", entry.get("model_id"));
        fallback.put("frame", new HashMap<>());
        fallback.put("roi", Collections.emptyList());
        return fallback;
    }


    private Map<String, Object> firstPluginRoute(Map<String, Object> payload, Map<String, Object> entry) {
        Map<String, Object> fromPayload = toMap(payload == null ? null : payload.get("plugin_route"));
        if (shouldAttachPluginRoute(fromPayload)) {
            return fromPayload;
        }
        Map<String, Object> fromEntry = toMap(entry == null ? null : entry.get("plugin_route"));
        if (shouldAttachPluginRoute(fromEntry)) {
            return fromEntry;
        }
        return fromPayload.isEmpty() ? fromEntry : fromPayload;
    }

    private Map<String, Object> buildReplayFailureData(String traceId,
                                                     Long deadLetterId,
                                                     int maxReplayAttempts,
                                                     String failureReason,
                                                     boolean replayInProgress,
                                                     boolean replayExhausted) {
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("dead_letter_id", deadLetterId);
        data.put("max_replay_attempts", maxReplayAttempts);
        data.put("failure_reason", failureReason);
        data.put("replay_in_progress", replayInProgress);
        data.put("replay_exhausted", replayExhausted);
        return data;
    }

    private void enrichReplayFailureBudget(Map<String, Object> data,
                                           int maxReplayAttempts,
                                           int replayCount) {
        Map<String, Object> replayBudget = buildReplayBudget(maxReplayAttempts, replayCount);
        data.put("replay_count", replayCount);
        data.put("remaining_replay_attempts", replayBudget.get("remaining_replay_attempts"));
        data.put("replay_exhausted", replayBudget.get("replay_exhausted"));
        data.put("replay_budget", replayBudget);
    }

    private Map<String, Object> buildDeadLetterFilterData(boolean onlyRetryable,
                                                          boolean onlyExhausted,
                                                          String backendType,
                                                          String pluginId,
                                                          String pluginRegistrationId,
                                                          String errorType) {
        Map<String, Object> data = new HashMap<>();
        data.put("only_retryable", onlyRetryable);
        data.put("only_exhausted", onlyExhausted);
        data.put("backend_type", backendType);
        data.put("plugin_id", pluginId);
        data.put("plugin_registration_id", pluginRegistrationId);
        data.put("error_type", errorType);
        data.put("filter_active", onlyRetryable
                || onlyExhausted
                || StrUtil.isNotBlank(backendType)
                || StrUtil.isNotBlank(pluginId)
                || StrUtil.isNotBlank(pluginRegistrationId)
                || StrUtil.isNotBlank(errorType));
        return data;
    }

    private Map<String, Object> enrichDeadLetterReplayBudget(Map<String, Object> entry) {
        Map<String, Object> data = entry == null ? new HashMap<>() : new HashMap<>(entry);
        int replayCount = toInt(data.get("replay_count"), 0);
        int maxReplayAttempts = inferenceDeadLetterService.maxReplayAttempts();
        int remainingReplayAttempts = Math.max(0, maxReplayAttempts - replayCount);
        data.put("replay_in_progress", toBooleanFlag(data.get("replay_in_progress"), false));
        data.put("replay_count", replayCount);
        data.put("max_replay_attempts", maxReplayAttempts);
        data.put("remaining_replay_attempts", remainingReplayAttempts);
        data.put("replay_exhausted", remainingReplayAttempts <= 0);
        return data;
    }

    private Map<String, Object> buildReplayBudget(int maxReplayAttempts, int replayCount) {
        int remainingReplayAttempts = Math.max(0, maxReplayAttempts - replayCount);
        Map<String, Object> data = new HashMap<>();
        data.put("max_replay_attempts", maxReplayAttempts);
        data.put("replay_count", replayCount);
        data.put("remaining_replay_attempts", remainingReplayAttempts);
        data.put("replay_exhausted", remainingReplayAttempts <= 0);
        return data;
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map) {
            return new HashMap<>((Map<? extends String, ?>) value);
        }
        return new HashMap<>();
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
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

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int toPositiveIntWithinRange(String value, int defaultValue, int maxValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                return defaultValue;
            }
            return Math.min(parsed, maxValue);
        } catch (Exception ignored) {
            return defaultValue;
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

    private Integer firstInteger(Object first, Integer second) {
        Integer firstValue = toInteger(first);
        if (firstValue != null) {
            return firstValue;
        }
        return second;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object firstNonNull(Object first, Object second) {
        if (first != null) {
            return first;
        }
        return second;
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

    private String computeReplayBatchWindowFingerprint(List<Map<String, Object>> sourceCandidates) {
        StringBuilder payload = new StringBuilder();
        if (sourceCandidates != null) {
            for (Map<String, Object> candidate : sourceCandidates) {
                if (payload.length() > 0) {
                    payload.append(",");
                }
                Long deadLetterId = candidate == null ? null : toLong(candidate.get("dead_letter_id"));
                payload.append(deadLetterId == null ? "null" : deadLetterId);
            }
        }
        return sha256Hex(payload.toString());
    }

    private String computeReplayBatchResumeToken(String selectionSource,
                                                 int totalSelectedCount,
                                                 int effectiveLimit,
                                                 int appliedOffset,
                                                 String actualWindowFingerprint) {
        String payload = String.format("source=%s;total=%d;limit=%d;offset=%d;window=%s",
                selectionSource,
                totalSelectedCount,
                effectiveLimit,
                appliedOffset,
                actualWindowFingerprint == null ? "" : actualWindowFingerprint);
        return sha256Hex(payload);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                String part = Integer.toHexString(b & 0xFF);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("failed to compute replay batch window fingerprint", e);
        }
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
