package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PluginInferenceDispatchService {

    private static final int DEFAULT_TIMEOUT_MS = 3000;
    private static final int DEFAULT_RETRY_COUNT = 1;

    @Autowired(required = false)
    private ConfigService configService;

    @Autowired
    private InferenceHttpGateway inferenceHttpGateway;

    public boolean isDispatchable(Map<String, Object> pluginRoute) {
        if (pluginRoute == null) {
            return false;
        }
        if (!toBoolean(pluginRoute.get("requested")) || !toBoolean(pluginRoute.get("matched")) || !toBoolean(pluginRoute.get("available"))) {
            return false;
        }
        return StrUtil.isNotBlank(resolveInferUrl(pluginRoute));
    }

    public InferenceResult infer(InferenceRequest request, Map<String, Object> pluginRoute) {
        if (!isDispatchable(pluginRoute)) {
            throw new IllegalStateException("plugin route is not dispatchable");
        }
        String inferUrl = resolveInferUrl(pluginRoute);
        int timeoutMs = getTimeoutMs();
        int retryCount = getRetryCount();
        String payload = JSON.toJSONString(request.toPayload());
        String lastError = "";

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                InferenceHttpResponse response = inferenceHttpGateway.postJson(inferUrl, timeoutMs, payload);
                if (response == null) {
                    lastError = "plugin inference response is null";
                    continue;
                }
                if (response.getStatus() != 200) {
                    lastError = "plugin inference non-200 response: " + response.getStatus();
                    continue;
                }
                JSONObject obj = safeParseObject(response.getBody());
                if (obj == null) {
                    lastError = "invalid plugin inference response body";
                    continue;
                }
                return toInferenceResult(obj, request, pluginRoute, response.getBody(), attempt);
            } catch (Exception e) {
                lastError = e.getMessage();
            }
        }
        throw new IllegalStateException(StrUtil.isBlank(lastError) ? "plugin inference failed" : lastError);
    }

    public String resolveInferUrl(Map<String, Object> pluginRoute) {
        Map<String, Object> plugin = toMap(pluginRoute == null ? null : pluginRoute.get("plugin"));
        String explicit = firstNonBlank(asString(plugin.get("infer_url")), asString(pluginRoute == null ? null : pluginRoute.get("infer_url")));
        if (StrUtil.isNotBlank(explicit)) {
            return normalizeUrl(explicit);
        }
        String healthUrl = firstNonBlank(asString(plugin.get("health_url")), asString(pluginRoute == null ? null : pluginRoute.get("health_url")));
        if (StrUtil.isBlank(healthUrl)) {
            return null;
        }
        String normalized = normalizeUrl(healthUrl);
        if (normalized.endsWith("/health")) {
            return normalized.substring(0, normalized.length() - "/health".length()) + "/v1/infer";
        }
        if (normalized.endsWith("/healthz")) {
            return normalized.substring(0, normalized.length() - "/healthz".length()) + "/v1/infer";
        }
        return StrUtil.removeSuffix(normalized, "/") + "/v1/infer";
    }

    private InferenceResult toInferenceResult(JSONObject obj,
                                              InferenceRequest request,
                                              Map<String, Object> pluginRoute,
                                              String rawBody,
                                              int attempt) {
        InferenceResult result = new InferenceResult();
        result.setTraceId(firstNonBlank(obj.getString("trace_id"), request == null ? null : request.getTraceId()));
        result.setCameraId(firstLong(obj.getLong("camera_id"), request == null ? null : request.getCameraId()));
        result.setLatencyMs(firstLong(obj.getLong("latency_ms"), 0L));
        result.setDetections(toDetectionList(obj.get("detections")));
        result.setAlerts(obj.containsKey("alerts") ? toDetectionList(obj.get("alerts")) : null);
        result.setEvents(obj.containsKey("events") ? toDetectionList(obj.get("events")) : null);
        result.setFrame(toObjectMap(obj.get("frame")));
        result.setPluginMeta(toObjectMap(obj.get("plugin")));
        result.setAttributes(toObjectMap(obj.get("attributes")));
        result.setBackendType(resolveRuntime(pluginRoute));
        result.setAttempt(attempt);
        result.setRawBody(rawBody);
        return result;
    }

    private String resolveRuntime(Map<String, Object> pluginRoute) {
        Map<String, Object> plugin = toMap(pluginRoute == null ? null : pluginRoute.get("plugin"));
        return firstNonBlank(asString(plugin.get("runtime")), asString(pluginRoute == null ? null : pluginRoute.get("backend_hint")));
    }

    private int getTimeoutMs() {
        if (configService == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        return toPositiveInt(configService.getByValTag("plugin_infer_timeout_ms"), DEFAULT_TIMEOUT_MS);
    }

    private int getRetryCount() {
        if (configService == null) {
            return DEFAULT_RETRY_COUNT;
        }
        return Math.max(1, toPositiveInt(configService.getByValTag("plugin_infer_retry_count"), DEFAULT_RETRY_COUNT));
    }

    private int toPositiveInt(String value, int defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception e) {
            return defaultValue;
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

    private List<Map<String, Object>> toDetectionList(Object detectionsObj) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (detectionsObj == null) {
            return list;
        }
        if (detectionsObj instanceof JSONArray) {
            JSONArray arr = (JSONArray) detectionsObj;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject item = toJsonObject(arr.get(i));
                if (item != null) {
                    list.add(new HashMap<>(item));
                }
            }
            return list;
        }
        if (detectionsObj instanceof List) {
            for (Object itemObj : (List<?>) detectionsObj) {
                JSONObject item = toJsonObject(itemObj);
                if (item != null) {
                    list.add(new HashMap<>(item));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String normalizeUrl(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (StrUtil.isNotBlank(first)) {
            return first;
        }
        return second;
    }

    private Long firstLong(Long first, Long second) {
        return first != null ? first : second;
    }
}