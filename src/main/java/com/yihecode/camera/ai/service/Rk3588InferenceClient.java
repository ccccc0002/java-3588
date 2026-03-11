package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class Rk3588InferenceClient implements InferenceClient {

    private static final int DEFAULT_TIMEOUT_MS = 3000;
    private static final int DEFAULT_RETRY_COUNT = 1;
    private static final int DEFAULT_CIRCUIT_FAIL_THRESHOLD = 3;
    private static final int DEFAULT_CIRCUIT_OPEN_SECONDS = 0;

    @Autowired
    private ConfigService configService;

    @Autowired
    private InferenceHttpGateway inferenceHttpGateway;

    private final Object circuitLock = new Object();
    private volatile long circuitOpenUntilMs = 0L;
    private volatile int consecutiveFailureCount = 0;

    @Override
    public String getBackendType() {
        return "rk3588_rknn";
    }

    @Override
    public Map<String, Object> health(String traceId) {
        String baseUrl = getServiceBaseUrl();
        int timeoutMs = getTimeoutMs();
        int retryCount = getRetryCount();
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("backend", getBackendType());
        data.put("service_url", baseUrl);
        data.put("timeout_ms", timeoutMs);
        data.put("retry_count", retryCount);
        Map<String, Object> decodeHints = buildDecodeHints();
        if (!decodeHints.isEmpty()) {
            data.put("decode_hints", decodeHints);
        }

        if (StrUtil.isBlank(baseUrl)) {
            data.put("status", "misconfigured");
            data.put("error", "infer_service_url is blank");
            return data;
        }
        CircuitSnapshot healthCircuit = getCircuitSnapshot();
        if (healthCircuit.open) {
            data.put("status", "down");
            data.put("error", buildCircuitOpenError(healthCircuit));
            data.put("circuit_open", true);
            data.put("circuit_open_until_ms", healthCircuit.openUntilMs);
            return data;
        }

        String url = baseUrl + "/health";
        String lastError = "";

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                InferenceHttpResponse response = inferenceHttpGateway.get(url, timeoutMs);
                int status = response.getStatus();
                String body = response.getBody();

                if (status == 200) {
                    JSONObject obj = safeParseObject(body);
                    if (obj != null) {
                        for (Map.Entry<String, Object> entry : obj.entrySet()) {
                            data.put(entry.getKey(), entry.getValue());
                        }
                    }
                    data.put("status", "ok");
                    data.put("http_status", status);
                    data.put("attempt", attempt);
                    data.put("circuit_open", false);
                    clearCircuitOnSuccess();
                    return data;
                }

                lastError = "non-200 response: " + status;
                log.warn("inference health check non-200, url={}, status={}, trace_id={}", url, status, traceId);
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("inference health check failed, url={}, attempt={}, trace_id={}, ex={}", url, attempt, traceId, e.getMessage());
            }
        }

        markCircuitFailure();
        CircuitSnapshot failCircuit = getCircuitSnapshot();
        data.put("status", "down");
        data.put("error", lastError);
        data.put("circuit_open", failCircuit.open);
        if (failCircuit.open) {
            data.put("circuit_open_until_ms", failCircuit.openUntilMs);
        }
        return data;
    }

    @Override
    public Map<String, Object> circuitStatus(String traceId) {
        CircuitSnapshot snapshot = getCircuitSnapshot();
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("backend", getBackendType());
        data.put("supported", true);
        data.put("circuit_open", snapshot.open);
        data.put("circuit_open_until_ms", snapshot.open ? snapshot.openUntilMs : 0L);
        data.put("consecutive_failures", consecutiveFailureCount);
        return data;
    }

    @Override
    public Map<String, Object> resetCircuit(String traceId) {
        clearCircuitOnSuccess();
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("backend", getBackendType());
        data.put("supported", true);
        data.put("reset", true);
        data.put("circuit_open", false);
        data.put("circuit_open_until_ms", 0L);
        data.put("consecutive_failures", 0);
        return data;
    }

    @Override
    public InferenceResult infer(InferenceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("inference request is null");
        }

        String baseUrl = getServiceBaseUrl();
        if (StrUtil.isBlank(baseUrl)) {
            throw new IllegalStateException("infer_service_url is blank");
        }
        CircuitSnapshot inferCircuit = getCircuitSnapshot();
        if (inferCircuit.open) {
            throw new IllegalStateException(buildCircuitOpenError(inferCircuit));
        }

        String url = baseUrl + "/v1/infer";
        int timeoutMs = getTimeoutMs();
        int retryCount = getRetryCount();
        String payload = buildPayload(request);
        String lastError = "";

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                InferenceHttpResponse response = inferenceHttpGateway.postJson(url, timeoutMs, payload);
                int status = response.getStatus();
                String body = response.getBody();

                if (status == 200) {
                    clearCircuitOnSuccess();
                    return parseInferResponse(body, request, attempt);
                }

                lastError = "non-200 response: " + status;
                log.warn("inference request non-200, url={}, status={}, attempt={}, trace_id={}", url, status, attempt, request.getTraceId());
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("inference request failed, url={}, attempt={}, trace_id={}, ex={}", url, attempt, request.getTraceId(), e.getMessage());
            }
        }

        markCircuitFailure();
        throw new IllegalStateException("rk3588 inference failed: " + lastError);
    }

    private InferenceResult parseInferResponse(String body, InferenceRequest request, int attempt) {
        JSONObject obj = safeParseObject(body);
        if (obj == null) {
            throw new IllegalStateException("invalid inference response body");
        }

        InferenceResult result = new InferenceResult();
        result.setTraceId(firstNonBlank(obj.getString("trace_id"), request.getTraceId()));
        result.setCameraId(firstLong(obj.getLong("camera_id"), request.getCameraId()));
        result.setLatencyMs(firstLong(obj.getLong("latency_ms"), 0L));
        result.setDetections(toDetectionList(obj.get("detections")));
        result.setAlerts(obj.containsKey("alerts") ? toDetectionList(obj.get("alerts")) : null);
        result.setEvents(obj.containsKey("events") ? toDetectionList(obj.get("events")) : null);
        result.setFrame(toObjectMap(obj.get("frame")));
        result.setPluginMeta(toObjectMap(obj.get("plugin")));
        result.setAttributes(toObjectMap(obj.get("attributes")));
        result.setBackendType(getBackendType());
        result.setAttempt(attempt);
        result.setRawBody(body);
        return result;
    }

    private List<Map<String, Object>> toDetectionList(Object detectionsObj) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (detectionsObj == null) {
            return list;
        }

        if (detectionsObj instanceof JSONArray) {
            JSONArray arr = (JSONArray) detectionsObj;
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                if (item == null) {
                    continue;
                }
                JSONObject itemObj = toJsonObject(item);
                if (itemObj != null) {
                    list.add(new HashMap<>(itemObj));
                }
            }
            return list;
        }

        if (detectionsObj instanceof List) {
            List<?> arr = (List<?>) detectionsObj;
            for (Object item : arr) {
                JSONObject itemObj = toJsonObject(item);
                if (itemObj != null) {
                    list.add(new HashMap<>(itemObj));
                }
            }
            return list;
        }

        JSONObject single = toJsonObject(detectionsObj);
        if (single != null) {
            list.add(new HashMap<>(single));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toObjectMap(Object value) {
        if (value instanceof Map) {
            return new HashMap<>((Map<String, Object>) value);
        }
        JSONObject obj = toJsonObject(value);
        if (obj != null) {
            return new HashMap<>(obj);
        }
        return new HashMap<>();
    }

    private JSONObject toJsonObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        try {
            return JSON.parseObject(JSON.toJSONString(value));
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject safeParseObject(String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        try {
            return JSON.parseObject(body);
        } catch (Exception e) {
            return null;
        }
    }

    private String getServiceBaseUrl() {
        String base = configService.getByValTag("infer_service_url");
        if (StrUtil.isBlank(base)) {
            return "";
        }
        return StrUtil.removeSuffix(StrUtil.trim(base), "/");
    }

    private String buildPayload(InferenceRequest request) {
        Map<String, Object> payload = new HashMap<>(request.toPayload());
        Map<String, Object> decodeHints = buildDecodeHints();
        if (!decodeHints.isEmpty()) {
            payload.put("decode", decodeHints);
        }
        return JSON.toJSONString(payload);
    }

    private Map<String, Object> buildDecodeHints() {
        Map<String, Object> decodeHints = new HashMap<>();
        putIfNotBlank(decodeHints, "backend", configService.getByValTag("infer_decode_backend"));
        putIfNotBlank(decodeHints, "hwaccel", configService.getByValTag("infer_decode_hwaccel"));
        putIfPositiveInt(decodeHints, "max_width", configService.getByValTag("infer_decode_max_width"));
        putIfPositiveInt(decodeHints, "max_height", configService.getByValTag("infer_decode_max_height"));
        return decodeHints;
    }

    private void putIfNotBlank(Map<String, Object> data, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            data.put(key, value.trim());
        }
    }

    private void putIfPositiveInt(Map<String, Object> data, String key, String value) {
        int num = toPositiveInt(value, -1);
        if (num > 0) {
            data.put(key, num);
        }
    }

    private int getTimeoutMs() {
        return toPositiveInt(configService.getByValTag("infer_timeout_ms"), DEFAULT_TIMEOUT_MS);
    }

    private int getRetryCount() {
        int value = toPositiveInt(configService.getByValTag("infer_retry_count"), DEFAULT_RETRY_COUNT);
        return Math.max(1, value);
    }

    private int getCircuitFailThreshold() {
        return toPositiveInt(configService.getByValTag("infer_circuit_fail_threshold"), DEFAULT_CIRCUIT_FAIL_THRESHOLD);
    }

    private int getCircuitOpenSeconds() {
        return toPositiveInt(configService.getByValTag("infer_circuit_open_seconds"), DEFAULT_CIRCUIT_OPEN_SECONDS);
    }

    private CircuitSnapshot getCircuitSnapshot() {
        long now = currentTimeMs();
        long openUntil = circuitOpenUntilMs;
        if (openUntil > now) {
            return new CircuitSnapshot(true, openUntil);
        }
        if (openUntil > 0) {
            synchronized (circuitLock) {
                if (circuitOpenUntilMs <= currentTimeMs()) {
                    circuitOpenUntilMs = 0L;
                }
            }
        }
        return new CircuitSnapshot(false, 0L);
    }

    private void clearCircuitOnSuccess() {
        synchronized (circuitLock) {
            consecutiveFailureCount = 0;
            circuitOpenUntilMs = 0L;
        }
    }

    private void markCircuitFailure() {
        int threshold = Math.max(1, getCircuitFailThreshold());
        int openSeconds = Math.max(0, getCircuitOpenSeconds());
        if (openSeconds <= 0) {
            return;
        }

        long now = currentTimeMs();
        synchronized (circuitLock) {
            if (circuitOpenUntilMs > now) {
                return;
            }
            consecutiveFailureCount++;
            if (consecutiveFailureCount >= threshold) {
                circuitOpenUntilMs = now + openSeconds * 1000L;
                consecutiveFailureCount = 0;
            }
        }
    }

    private String buildCircuitOpenError(CircuitSnapshot snapshot) {
        return "rk3588 inference circuit breaker open until " + snapshot.openUntilMs;
    }

    private long currentTimeMs() {
        return System.currentTimeMillis();
    }

    private int toPositiveInt(String value, int defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            int num = Integer.parseInt(value.trim());
            return num > 0 ? num : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Long firstLong(Long first, Long fallback) {
        return first != null ? first : fallback;
    }

    private String firstNonBlank(String first, String fallback) {
        if (StrUtil.isNotBlank(first)) {
            return first;
        }
        return fallback;
    }

    private static class CircuitSnapshot {
        private final boolean open;
        private final long openUntilMs;

        private CircuitSnapshot(boolean open, long openUntilMs) {
            this.open = open;
            this.openUntilMs = openUntilMs;
        }
    }
}
