package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.web.api.InferenceApiController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class ActiveCameraInferenceSchedulerService {

    private static final String CONFIG_ENABLED = "infer_scheduler_enabled";
    private static final String CONFIG_DEFAULT_PLUGIN_ID = "infer_default_plugin_id";
    private static final String CONFIG_MAX_CAMERAS = "infer_scheduler_max_cameras";
    private static final String CONFIG_COOLDOWN_MS = "infer_scheduler_cooldown_ms";
    private static final String CONFIG_LATENCY_FACTOR = "infer_scheduler_latency_factor";
    private static final int DEFAULT_MAX_CAMERAS = 10;
    private static final long DEFAULT_COOLDOWN_MS = 5000L;
    private static final double DEFAULT_LATENCY_FACTOR = 1.0D;
    private static final String FALLBACK_PLUGIN_ID = "yolov8n";

    private final ConcurrentMap<String, Long> lastDispatchAtMs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> observedLatencyMs = new ConcurrentHashMap<>();

    @Autowired
    private CameraService cameraService;

    @Autowired
    private CameraAlgorithmService cameraAlgorithmService;

    @Autowired
    private AlgorithmService algorithmService;

    @Autowired(required = false)
    private ConfigService configService;

    @Autowired
    private InferenceApiController inferenceApiController;

    public Map<String, Object> dispatchActiveCameras() {
        long nowMs = currentTimeMillis();
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        summary.put("enabled", isEnabled());
        summary.put("camera_count", 0);
        summary.put("binding_count", 0);
        summary.put("dispatch_count", 0);
        summary.put("skip_count", 0);
        summary.put("error_count", 0);
        summary.put("skipped", skipped);
        summary.put("errors", errors);
        summary.put("executed_at_ms", nowMs);

        if (!Boolean.TRUE.equals(summary.get("enabled"))) {
            return summary;
        }

        List<Camera> cameras = safeList(cameraService == null ? null : cameraService.listActives());
        int maxCameras = resolveMaxCameras();
        int selectedCount = Math.min(cameras.size(), maxCameras);
        summary.put("camera_count", selectedCount);

        for (int cameraIndex = 0; cameraIndex < selectedCount; cameraIndex++) {
            Camera camera = cameras.get(cameraIndex);
            if (camera == null || camera.getId() == null) {
                addSkipped(summary, skipped, null, null, "invalid_camera");
                continue;
            }
            if (StrUtil.isBlank(camera.getRtspUrl())) {
                addSkipped(summary, skipped, camera.getId(), null, "blank_rtsp_url");
                continue;
            }

            List<CameraAlgorithm> bindings = safeListCameraAlgorithms(cameraAlgorithmService == null ? null : cameraAlgorithmService.listByCamera(camera.getId()));
            if (bindings.isEmpty()) {
                addSkipped(summary, skipped, camera.getId(), null, "no_algorithm_binding");
                continue;
            }

            for (CameraAlgorithm binding : bindings) {
                increment(summary, "binding_count");
                if (binding == null || binding.getAlgorithmId() == null) {
                    addSkipped(summary, skipped, camera.getId(), null, "invalid_binding");
                    continue;
                }

                Algorithm algorithm = algorithmService == null ? null : algorithmService.getById(binding.getAlgorithmId());
                if (algorithm == null || algorithm.getId() == null) {
                    addSkipped(summary, skipped, camera.getId(), binding.getAlgorithmId(), "algorithm_not_found");
                    continue;
                }

                String dispatchKey = buildDispatchKey(camera.getId(), algorithm.getId());
                long cooldownMs = resolveCooldownMs(dispatchKey, camera, algorithm);
                Long lastDispatchMs = lastDispatchAtMs.get(dispatchKey);
                if (cooldownMs > 0 && lastDispatchMs != null && (nowMs - lastDispatchMs) < cooldownMs) {
                    addSkipped(summary, skipped, camera.getId(), algorithm.getId(), "cooldown");
                    continue;
                }

                Map<String, Object> body = buildDispatchBody(camera, algorithm, nowMs);
                try {
                    JsonResult result = inferenceApiController.dispatch(body, null, null, null, null, 1);
                    if (result != null && Integer.valueOf(0).equals(result.getCode())) {
                        lastDispatchAtMs.put(dispatchKey, nowMs);
                        updateObservedLatency(dispatchKey, extractLatencyMs(result.getData()));
                        increment(summary, "dispatch_count");
                    } else {
                        addError(summary, errors, camera.getId(), algorithm.getId(), result == null ? "dispatch_result_null" : String.valueOf(result.getMsg()));
                    }
                } catch (Exception ex) {
                    log.warn("scheduled inference dispatch failed, camera_id={}, algorithm_id={}", camera.getId(), algorithm.getId(), ex);
                    addError(summary, errors, camera.getId(), algorithm.getId(), ex.getMessage());
                }
            }
        }

        return summary;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private Map<String, Object> buildDispatchBody(Camera camera, Algorithm algorithm, long nowMs) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("source", camera.getRtspUrl());
        frame.put("timestamp_ms", nowMs);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("camera_id", camera.getId());
        body.put("model_id", algorithm.getId());
        body.put("algorithm_id", algorithm.getId());
        body.put("plugin_id", resolvePluginId(algorithm));
        body.put("persist_report", 1);
        body.put("frame", frame);
        return body;
    }

    private boolean isEnabled() {
        String value = getConfig(CONFIG_ENABLED);
        if (StrUtil.isBlank(value)) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private int resolveMaxCameras() {
        String value = getConfig(CONFIG_MAX_CAMERAS);
        try {
            int parsed = Integer.parseInt(StrUtil.blankToDefault(value, String.valueOf(DEFAULT_MAX_CAMERAS)).trim());
            return parsed > 0 ? parsed : DEFAULT_MAX_CAMERAS;
        } catch (Exception ex) {
            return DEFAULT_MAX_CAMERAS;
        }
    }

    private long resolveCooldownMs(String dispatchKey, Camera camera, Algorithm algorithm) {
        long algorithmCooldown = secondsToMillis(algorithm == null ? null : algorithm.getIntervalTime());
        if (algorithmCooldown > 0) {
            return Math.max(algorithmCooldown, resolveLatencyCooldownMs(dispatchKey, algorithm));
        }
        long cameraCooldown = secondsToMillis(camera == null ? null : camera.getIntervalTime());
        if (cameraCooldown > 0) {
            return Math.max(cameraCooldown, resolveLatencyCooldownMs(dispatchKey, algorithm));
        }
        String value = getConfig(CONFIG_COOLDOWN_MS);
        long fallback;
        try {
            long parsed = Long.parseLong(StrUtil.blankToDefault(value, String.valueOf(DEFAULT_COOLDOWN_MS)).trim());
            fallback = Math.max(parsed, 0L);
        } catch (Exception ex) {
            fallback = DEFAULT_COOLDOWN_MS;
        }
        return Math.max(fallback, resolveLatencyCooldownMs(dispatchKey, algorithm));
    }

    private long resolveLatencyCooldownMs(String dispatchKey, Algorithm algorithm) {
        Long observed = observedLatencyMs.get(dispatchKey);
        Long declared = extractDeclaredInferenceMs(algorithm == null ? null : algorithm.getParams());
        long baseline = Math.max(observed == null ? 0L : observed, declared == null ? 0L : declared);
        if (baseline <= 0L) {
            return 0L;
        }
        double factor = resolveLatencyFactor();
        return Math.max(Math.round(baseline * factor), 0L);
    }

    private double resolveLatencyFactor() {
        String raw = getConfig(CONFIG_LATENCY_FACTOR);
        if (StrUtil.isBlank(raw)) {
            return DEFAULT_LATENCY_FACTOR;
        }
        try {
            double parsed = Double.parseDouble(raw.trim());
            return parsed > 0D ? parsed : DEFAULT_LATENCY_FACTOR;
        } catch (Exception ex) {
            return DEFAULT_LATENCY_FACTOR;
        }
    }

    private String resolvePluginId(Algorithm algorithm) {
        String paramsPluginId = extractPluginIdFromParams(algorithm == null ? null : algorithm.getParams());
        if (StrUtil.isNotBlank(paramsPluginId)) {
            return paramsPluginId;
        }
        String configured = getConfig(CONFIG_DEFAULT_PLUGIN_ID);
        if (StrUtil.isNotBlank(configured)) {
            return configured.trim();
        }
        return FALLBACK_PLUGIN_ID;
    }

    private String extractPluginIdFromParams(String params) {
        if (StrUtil.isBlank(params)) {
            return null;
        }
        try {
            JSONObject obj = JSON.parseObject(params);
            if (obj == null) {
                return null;
            }
            String pluginId = firstNonBlank(obj.getString("plugin_id"), obj.getString("pluginId"));
            return StrUtil.isBlank(pluginId) ? null : pluginId.trim();
        } catch (Exception ex) {
            log.debug("ignore invalid algorithm params while resolving plugin_id, params={}", params, ex);
            return null;
        }
    }

    private Long extractDeclaredInferenceMs(String params) {
        if (StrUtil.isBlank(params)) {
            return null;
        }
        try {
            JSONObject obj = JSON.parseObject(params);
            if (obj == null) {
                return null;
            }
            Long fromMs = firstPositiveLong(obj.getLong("inference_time_ms"), obj.getLong("infer_time_ms"), obj.getLong("latency_ms"));
            if (fromMs != null) {
                return fromMs;
            }
            Long fromSec = firstPositiveLong(obj.getLong("inference_time_s"), obj.getLong("infer_time_s"));
            return fromSec == null ? null : Math.max(fromSec, 0L) * 1000L;
        } catch (Exception ex) {
            log.debug("ignore invalid algorithm params while resolving inference time, params={}", params, ex);
            return null;
        }
    }

    private long extractLatencyMs(Object data) {
        if (!(data instanceof Map)) {
            return 0L;
        }
        Map<?, ?> map = (Map<?, ?>) data;
        Object direct = map.get("latency_ms");
        long directLatency = toLong(direct);
        if (directLatency > 0L) {
            return directLatency;
        }
        Object result = map.get("result");
        if (result instanceof Map) {
            return Math.max(toLong(((Map<?, ?>) result).get("latency_ms")), 0L);
        }
        return 0L;
    }

    private void updateObservedLatency(String dispatchKey, long latestLatencyMs) {
        if (latestLatencyMs <= 0L || StrUtil.isBlank(dispatchKey)) {
            return;
        }
        observedLatencyMs.compute(dispatchKey, (k, prev) -> {
            if (prev == null || prev <= 0L) {
                return latestLatencyMs;
            }
            return Math.round(prev * 0.7D + latestLatencyMs * 0.3D);
        });
    }

    private Long firstPositiveLong(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null && value > 0L) {
                return value;
            }
        }
        return null;
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ex) {
            return 0L;
        }
    }

    private String getConfig(String tag) {
        try {
            return configService == null ? null : configService.getByValTag(tag);
        } catch (Exception ex) {
            log.debug("failed to read config, tag={}", tag, ex);
            return null;
        }
    }

    private long secondsToMillis(Number seconds) {
        if (seconds == null) {
            return 0L;
        }
        double value = seconds.doubleValue();
        if (value <= 0D) {
            return 0L;
        }
        return Math.round(value * 1000D);
    }

    private String buildDispatchKey(Long cameraId, Long algorithmId) {
        return String.valueOf(cameraId) + "#" + String.valueOf(algorithmId);
    }

    private List<Camera> safeList(List<Camera> cameras) {
        return cameras == null ? new ArrayList<>() : new ArrayList<>(cameras);
    }

    private List<CameraAlgorithm> safeListCameraAlgorithms(List<CameraAlgorithm> bindings) {
        return bindings == null ? new ArrayList<>() : new ArrayList<>(bindings);
    }

    private void addSkipped(Map<String, Object> summary,
                            List<Map<String, Object>> skipped,
                            Long cameraId,
                            Long algorithmId,
                            String reason) {
        increment(summary, "skip_count");
        Map<String, Object> item = new HashMap<>();
        item.put("camera_id", cameraId);
        item.put("algorithm_id", algorithmId);
        item.put("reason", reason);
        skipped.add(item);
    }

    private void addError(Map<String, Object> summary,
                          List<Map<String, Object>> errors,
                          Long cameraId,
                          Long algorithmId,
                          String errorMessage) {
        increment(summary, "error_count");
        Map<String, Object> item = new HashMap<>();
        item.put("camera_id", cameraId);
        item.put("algorithm_id", algorithmId);
        item.put("error", StrUtil.blankToDefault(errorMessage, "dispatch_failed"));
        errors.add(item);
    }

    private void increment(Map<String, Object> summary, String key) {
        Number current = (Number) summary.getOrDefault(key, 0);
        summary.put(key, current.intValue() + 1);
    }

    private String firstNonBlank(String first, String second) {
        if (StrUtil.isNotBlank(first)) {
            return first;
        }
        return StrUtil.isNotBlank(second) ? second : null;
    }
}
