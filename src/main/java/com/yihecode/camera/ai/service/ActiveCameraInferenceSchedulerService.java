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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
public class ActiveCameraInferenceSchedulerService {

    private static final String CONFIG_ENABLED = "infer_scheduler_enabled";
    private static final String CONFIG_DEFAULT_PLUGIN_ID = "infer_default_plugin_id";
    private static final String CONFIG_MAX_CAMERAS = "infer_scheduler_max_cameras";
    private static final String CONFIG_COOLDOWN_MS = "infer_scheduler_cooldown_ms";
    private static final String CONFIG_LATENCY_FACTOR = "infer_scheduler_latency_factor";
    private static final String CONFIG_CONCURRENCY_BASELINE = "infer_scheduler_concurrency_baseline";
    private static final String CONFIG_MAX_WORKERS = "infer_scheduler_max_workers";
    private static final int DEFAULT_MAX_CAMERAS = 10;
    private static final long DEFAULT_COOLDOWN_MS = 5000L;
    private static final double DEFAULT_LATENCY_FACTOR = 1.0D;
    private static final int DEFAULT_CONCURRENCY_BASELINE = 4;
    private static final int DEFAULT_MAX_WORKERS = 3;
    private static final String FALLBACK_PLUGIN_ID = "yolov8n";

    private final ConcurrentMap<String, Long> lastDispatchAtMs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> observedLatencyMs = new ConcurrentHashMap<>();
    private volatile Map<String, Object> lastSummary = Collections.emptyMap();

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
        double latencyFactor = resolveLatencyFactor();
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        summary.put("enabled", isEnabled());
        summary.put("camera_count", 0);
        summary.put("binding_count", 0);
        summary.put("dispatch_count", 0);
        summary.put("skip_count", 0);
        summary.put("error_count", 0);
        summary.put("latency_factor", latencyFactor);
        summary.put("latency_update_count", 0);
        summary.put("max_declared_inference_ms", 0L);
        summary.put("max_observed_latency_ms", 0L);
        summary.put("max_effective_cooldown_ms", 0L);
        summary.put("concurrency_level", 0);
        summary.put("concurrency_pressure", 1.0D);
        summary.put("worker_count", 1);
        summary.put("skipped", skipped);
        summary.put("errors", errors);
        summary.put("executed_at_ms", nowMs);

        if (!Boolean.TRUE.equals(summary.get("enabled"))) {
            lastSummary = snapshotSummary(summary);
            return summary;
        }

        List<Camera> cameras = safeList(cameraService == null ? null : cameraService.listActives());
        int maxCameras = resolveMaxCameras();
        int selectedCount = Math.min(cameras.size(), maxCameras);
        summary.put("camera_count", selectedCount);

        List<DispatchContext> dispatchContexts = new ArrayList<>();
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
                dispatchContexts.add(new DispatchContext(camera, algorithm, buildDispatchKey(camera.getId(), algorithm.getId())));
            }
        }

        int concurrencyLevel = dispatchContexts.size();
        double concurrencyPressure = resolveConcurrencyPressure(concurrencyLevel);
        int workerCount = resolveWorkerCount(concurrencyLevel);
        summary.put("concurrency_level", concurrencyLevel);
        summary.put("concurrency_pressure", concurrencyPressure);
        summary.put("worker_count", workerCount);

        List<DispatchContext> readyContexts = new ArrayList<>();
        for (DispatchContext context : dispatchContexts) {
            Camera camera = context.getCamera();
            Algorithm algorithm = context.getAlgorithm();
            String dispatchKey = context.getDispatchKey();
            CooldownDecision cooldown = resolveCooldownMs(dispatchKey, camera, algorithm, latencyFactor, concurrencyLevel, concurrencyPressure);
            updateMax(summary, "max_declared_inference_ms", cooldown.getDeclaredLatencyMs());
            updateMax(summary, "max_observed_latency_ms", cooldown.getObservedLatencyMs());
            updateMax(summary, "max_effective_cooldown_ms", cooldown.getEffectiveCooldownMs());
            Long lastDispatchMs = lastDispatchAtMs.get(dispatchKey);
            if (cooldown.getEffectiveCooldownMs() > 0 && lastDispatchMs != null && (nowMs - lastDispatchMs) < cooldown.getEffectiveCooldownMs()) {
                Map<String, Object> cooldownMeta = new LinkedHashMap<>();
                cooldownMeta.put("cooldown_source", cooldown.getSource());
                cooldownMeta.put("base_cooldown_source", cooldown.getBaseSource());
                cooldownMeta.put("base_cooldown_ms", cooldown.getBaseCooldownMs());
                cooldownMeta.put("latency_cooldown_ms", cooldown.getLatencyCooldownMs());
                cooldownMeta.put("effective_cooldown_ms", cooldown.getEffectiveCooldownMs());
                cooldownMeta.put("declared_inference_ms", cooldown.getDeclaredLatencyMs());
                cooldownMeta.put("observed_latency_ms", cooldown.getObservedLatencyMs());
                cooldownMeta.put("concurrency_level", cooldown.getConcurrencyLevel());
                cooldownMeta.put("concurrency_pressure", cooldown.getConcurrencyPressure());
                cooldownMeta.put("elapsed_since_last_dispatch_ms", Math.max(nowMs - lastDispatchMs, 0L));
                addSkipped(summary, skipped, camera.getId(), algorithm.getId(), "cooldown", cooldownMeta);
                continue;
            }
            readyContexts.add(context);
        }

        List<DispatchOutcome> outcomes = dispatchReadyContexts(readyContexts, nowMs, workerCount);
        for (DispatchOutcome outcome : outcomes) {
            DispatchContext context = outcome.getContext();
            Camera camera = context == null ? null : context.getCamera();
            Algorithm algorithm = context == null ? null : context.getAlgorithm();
            String dispatchKey = context == null ? null : context.getDispatchKey();

            if (outcome.isSuccess()) {
                if (StrUtil.isNotBlank(dispatchKey)) {
                    lastDispatchAtMs.put(dispatchKey, nowMs);
                }
                if (updateObservedLatency(dispatchKey, outcome.getLatestLatencyMs())) {
                    increment(summary, "latency_update_count");
                    updateMax(summary, "max_observed_latency_ms", observedLatencyMs.get(dispatchKey));
                }
                increment(summary, "dispatch_count");
            } else {
                addError(summary, errors,
                        camera == null ? null : camera.getId(),
                        algorithm == null ? null : algorithm.getId(),
                        outcome.getErrorMessage());
            }
        }

        lastSummary = snapshotSummary(summary);
        return summary;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public Map<String, Object> getLastSummary() {
        return snapshotSummary(lastSummary);
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

    private CooldownDecision resolveCooldownMs(String dispatchKey,
                                               Camera camera,
                                               Algorithm algorithm,
                                               double latencyFactor,
                                               int concurrencyLevel,
                                               double concurrencyPressure) {
        long algorithmCooldown = secondsToMillis(algorithm == null ? null : algorithm.getIntervalTime());
        long baseCooldown = algorithmCooldown;
        String baseSource = "algorithm_interval";
        if (algorithmCooldown > 0) {
            Long declared = extractDeclaredInferenceMs(algorithm == null ? null : algorithm.getParams());
            Long observed = observedLatencyMs.get(dispatchKey);
            long latencyCooldown = resolveLatencyCooldownMs(declared, observed, latencyFactor, concurrencyPressure);
            return buildCooldownDecision(baseCooldown, latencyCooldown, declared, observed, baseSource, concurrencyLevel, concurrencyPressure);
        }

        long cameraCooldown = secondsToMillis(camera == null ? null : camera.getIntervalTime());
        baseCooldown = cameraCooldown;
        baseSource = "camera_interval";
        if (cameraCooldown > 0) {
            Long declared = extractDeclaredInferenceMs(algorithm == null ? null : algorithm.getParams());
            Long observed = observedLatencyMs.get(dispatchKey);
            long latencyCooldown = resolveLatencyCooldownMs(declared, observed, latencyFactor, concurrencyPressure);
            return buildCooldownDecision(baseCooldown, latencyCooldown, declared, observed, baseSource, concurrencyLevel, concurrencyPressure);
        }

        String value = getConfig(CONFIG_COOLDOWN_MS);
        long fallback = DEFAULT_COOLDOWN_MS;
        try {
            long parsed = Long.parseLong(StrUtil.blankToDefault(value, String.valueOf(DEFAULT_COOLDOWN_MS)).trim());
            fallback = Math.max(parsed, 0L);
        } catch (Exception ex) {
            fallback = Math.max(DEFAULT_COOLDOWN_MS, 0L);
        }
        baseCooldown = fallback;
        baseSource = "config_default";

        Long declared = extractDeclaredInferenceMs(algorithm == null ? null : algorithm.getParams());
        Long observed = observedLatencyMs.get(dispatchKey);
        long latencyCooldown = resolveLatencyCooldownMs(declared, observed, latencyFactor, concurrencyPressure);
        return buildCooldownDecision(baseCooldown, latencyCooldown, declared, observed, baseSource, concurrencyLevel, concurrencyPressure);
    }

    private long resolveLatencyCooldownMs(Long declared, Long observed, double latencyFactor, double concurrencyPressure) {
        long baseline = Math.max(observed == null ? 0L : observed, declared == null ? 0L : declared);
        if (baseline <= 0L) {
            return 0L;
        }
        double normalizedPressure = concurrencyPressure > 0D ? concurrencyPressure : 1.0D;
        return Math.max(Math.round(baseline * latencyFactor * normalizedPressure), 0L);
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

    private int resolveConcurrencyBaseline() {
        String raw = getConfig(CONFIG_CONCURRENCY_BASELINE);
        if (StrUtil.isBlank(raw)) {
            return DEFAULT_CONCURRENCY_BASELINE;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : DEFAULT_CONCURRENCY_BASELINE;
        } catch (Exception ex) {
            return DEFAULT_CONCURRENCY_BASELINE;
        }
    }

    private double resolveConcurrencyPressure(int concurrencyLevel) {
        int normalizedLevel = Math.max(concurrencyLevel, 1);
        int baseline = resolveConcurrencyBaseline();
        return Math.max((double) normalizedLevel / (double) baseline, 1.0D);
    }

    private int resolveWorkerCount(int concurrencyLevel) {
        int level = Math.max(concurrencyLevel, 1);
        String raw = getConfig(CONFIG_MAX_WORKERS);
        int configured = DEFAULT_MAX_WORKERS;
        if (StrUtil.isNotBlank(raw)) {
            try {
                int parsed = Integer.parseInt(raw.trim());
                configured = parsed > 0 ? parsed : DEFAULT_MAX_WORKERS;
            } catch (Exception ex) {
                configured = DEFAULT_MAX_WORKERS;
            }
        }
        return Math.max(Math.min(configured, level), 1);
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

    private boolean updateObservedLatency(String dispatchKey, long latestLatencyMs) {
        if (latestLatencyMs <= 0L || StrUtil.isBlank(dispatchKey)) {
            return false;
        }
        observedLatencyMs.compute(dispatchKey, (k, prev) -> {
            if (prev == null || prev <= 0L) {
                return latestLatencyMs;
            }
            return Math.round(prev * 0.7D + latestLatencyMs * 0.3D);
        });
        return true;
    }

    private CooldownDecision buildCooldownDecision(long baseCooldown,
                                                   long latencyCooldown,
                                                   Long declared,
                                                   Long observed,
                                                   String baseSource,
                                                   int concurrencyLevel,
                                                   double concurrencyPressure) {
        long normalizedBase = Math.max(baseCooldown, 0L);
        long normalizedLatency = Math.max(latencyCooldown, 0L);
        long effective = Math.max(normalizedBase, normalizedLatency);

        String source;
        if (normalizedBase > 0L && normalizedLatency > 0L) {
            source = "base+latency";
        } else if (normalizedLatency > 0L) {
            source = "latency";
        } else if (normalizedBase > 0L) {
            source = "base";
        } else {
            source = "none";
        }

        return new CooldownDecision(
                effective,
                normalizedBase,
                normalizedLatency,
                declared == null ? 0L : Math.max(declared, 0L),
                observed == null ? 0L : Math.max(observed, 0L),
                StrUtil.blankToDefault(baseSource, "unknown"),
                source,
                Math.max(concurrencyLevel, 0),
                Math.max(concurrencyPressure, 1.0D)
        );
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
        addSkipped(summary, skipped, cameraId, algorithmId, reason, null);
    }

    private void addSkipped(Map<String, Object> summary,
                            List<Map<String, Object>> skipped,
                            Long cameraId,
                            Long algorithmId,
                            String reason,
                            Map<String, Object> details) {
        increment(summary, "skip_count");
        Map<String, Object> item = new HashMap<>();
        item.put("camera_id", cameraId);
        item.put("algorithm_id", algorithmId);
        item.put("reason", reason);
        if (details != null && !details.isEmpty()) {
            item.putAll(details);
        }
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

    private void updateMax(Map<String, Object> summary, String key, Number candidate) {
        if (candidate == null) {
            return;
        }
        long value = Math.max(candidate.longValue(), 0L);
        Number current = (Number) summary.getOrDefault(key, 0L);
        long max = Math.max(current == null ? 0L : current.longValue(), value);
        summary.put(key, max);
    }

    private String firstNonBlank(String first, String second) {
        if (StrUtil.isNotBlank(first)) {
            return first;
        }
        return StrUtil.isNotBlank(second) ? second : null;
    }

    private List<DispatchOutcome> dispatchReadyContexts(List<DispatchContext> readyContexts, long nowMs, int workerCount) {
        if (readyContexts == null || readyContexts.isEmpty()) {
            return Collections.emptyList();
        }
        int normalizedWorker = Math.max(workerCount, 1);
        if (normalizedWorker <= 1 || readyContexts.size() <= 1) {
            List<DispatchOutcome> outcomes = new ArrayList<>(readyContexts.size());
            for (DispatchContext context : readyContexts) {
                outcomes.add(dispatchSingle(context, nowMs));
            }
            return outcomes;
        }

        ExecutorService executor = Executors.newFixedThreadPool(normalizedWorker);
        try {
            List<Future<DispatchOutcome>> futures = new ArrayList<>(readyContexts.size());
            for (DispatchContext context : readyContexts) {
                futures.add(executor.submit(() -> dispatchSingle(context, nowMs)));
            }

            List<DispatchOutcome> outcomes = new ArrayList<>(readyContexts.size());
            for (int i = 0; i < futures.size(); i++) {
                DispatchContext context = readyContexts.get(i);
                Future<DispatchOutcome> future = futures.get(i);
                try {
                    outcomes.add(future.get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    outcomes.add(DispatchOutcome.failed(context, "dispatch_interrupted"));
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    String message = cause == null ? ex.getMessage() : cause.getMessage();
                    outcomes.add(DispatchOutcome.failed(context, StrUtil.blankToDefault(message, "dispatch_failed")));
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private DispatchOutcome dispatchSingle(DispatchContext context, long nowMs) {
        if (context == null) {
            return DispatchOutcome.failed(null, "dispatch_context_null");
        }
        Camera camera = context.getCamera();
        Algorithm algorithm = context.getAlgorithm();
        try {
            Map<String, Object> body = buildDispatchBody(camera, algorithm, nowMs);
            JsonResult result = inferenceApiController.dispatch(body, null, null, null, null, 1);
            if (result != null && Integer.valueOf(0).equals(result.getCode())) {
                return DispatchOutcome.success(context, extractLatencyMs(result.getData()));
            }
            String msg = result == null ? "dispatch_result_null" : String.valueOf(result.getMsg());
            return DispatchOutcome.failed(context, msg);
        } catch (Exception ex) {
            log.warn("scheduled inference dispatch failed, camera_id={}, algorithm_id={}",
                    camera == null ? null : camera.getId(),
                    algorithm == null ? null : algorithm.getId(),
                    ex);
            return DispatchOutcome.failed(context, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotSummary(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), new LinkedHashMap<>((Map<String, Object>) value));
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                List<Object> copiedList = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof Map) {
                        copiedList.add(new LinkedHashMap<>((Map<String, Object>) item));
                    } else {
                        copiedList.add(item);
                    }
                }
                copy.put(entry.getKey(), copiedList);
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private static final class DispatchContext {
        private final Camera camera;
        private final Algorithm algorithm;
        private final String dispatchKey;

        private DispatchContext(Camera camera, Algorithm algorithm, String dispatchKey) {
            this.camera = camera;
            this.algorithm = algorithm;
            this.dispatchKey = dispatchKey;
        }

        private Camera getCamera() {
            return camera;
        }

        private Algorithm getAlgorithm() {
            return algorithm;
        }

        private String getDispatchKey() {
            return dispatchKey;
        }
    }

    private static final class DispatchOutcome {
        private final DispatchContext context;
        private final boolean success;
        private final long latestLatencyMs;
        private final String errorMessage;

        private DispatchOutcome(DispatchContext context, boolean success, long latestLatencyMs, String errorMessage) {
            this.context = context;
            this.success = success;
            this.latestLatencyMs = Math.max(latestLatencyMs, 0L);
            this.errorMessage = errorMessage;
        }

        private static DispatchOutcome success(DispatchContext context, long latestLatencyMs) {
            return new DispatchOutcome(context, true, latestLatencyMs, null);
        }

        private static DispatchOutcome failed(DispatchContext context, String errorMessage) {
            return new DispatchOutcome(context, false, 0L, StrUtil.blankToDefault(errorMessage, "dispatch_failed"));
        }

        private DispatchContext getContext() {
            return context;
        }

        private boolean isSuccess() {
            return success;
        }

        private long getLatestLatencyMs() {
            return latestLatencyMs;
        }

        private String getErrorMessage() {
            return errorMessage;
        }
    }

    private static final class CooldownDecision {
        private final long effectiveCooldownMs;
        private final long baseCooldownMs;
        private final long latencyCooldownMs;
        private final long declaredLatencyMs;
        private final long observedLatencyMs;
        private final String baseSource;
        private final String source;
        private final int concurrencyLevel;
        private final double concurrencyPressure;

        private CooldownDecision(long effectiveCooldownMs,
                                 long baseCooldownMs,
                                 long latencyCooldownMs,
                                 long declaredLatencyMs,
                                 long observedLatencyMs,
                                 String baseSource,
                                 String source,
                                 int concurrencyLevel,
                                 double concurrencyPressure) {
            this.effectiveCooldownMs = effectiveCooldownMs;
            this.baseCooldownMs = baseCooldownMs;
            this.latencyCooldownMs = latencyCooldownMs;
            this.declaredLatencyMs = declaredLatencyMs;
            this.observedLatencyMs = observedLatencyMs;
            this.baseSource = baseSource;
            this.source = source;
            this.concurrencyLevel = concurrencyLevel;
            this.concurrencyPressure = concurrencyPressure;
        }

        private long getEffectiveCooldownMs() {
            return effectiveCooldownMs;
        }

        private long getBaseCooldownMs() {
            return baseCooldownMs;
        }

        private long getLatencyCooldownMs() {
            return latencyCooldownMs;
        }

        private long getDeclaredLatencyMs() {
            return declaredLatencyMs;
        }

        private long getObservedLatencyMs() {
            return observedLatencyMs;
        }

        private String getBaseSource() {
            return baseSource;
        }

        private String getSource() {
            return source;
        }

        private int getConcurrencyLevel() {
            return concurrencyLevel;
        }

        private double getConcurrencyPressure() {
            return concurrencyPressure;
        }
    }
}
