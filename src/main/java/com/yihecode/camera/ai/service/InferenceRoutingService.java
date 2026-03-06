package com.yihecode.camera.ai.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InferenceRoutingService {

    private static final String BACKEND_LEGACY = "legacy";
    private static final String BACKEND_RK3588 = "rk3588_rknn";

    private static final OverrideDecision NO_OVERRIDE = new OverrideDecision(null, null);

    @Autowired
    private ConfigService configService;

    @Autowired
    private LegacyInferenceClient legacyInferenceClient;

    @Autowired
    private Rk3588InferenceClient rk3588InferenceClient;

    public String currentBackendType() {
        String backend = StrUtil.trim(configService.getByValTag("infer_backend_type"));
        return normalizeBackendOrDefault(backend);
    }

    public String backendTypeForCamera(Long cameraId) {
        String overrideBackend = overrideBackendForCamera(cameraId);
        if (StrUtil.isNotBlank(overrideBackend)) {
            return overrideBackend;
        }
        return currentBackendType();
    }

    public String backendTypeForCamera(Long cameraId, String backendHint) {
        String normalizedHint = normalizeBackendStrict(backendHint);
        if (StrUtil.isNotBlank(normalizedHint)) {
            return normalizedHint;
        }
        return backendTypeForCamera(cameraId);
    }

    public String overrideBackendForCamera(Long cameraId) {
        String overrideConfig = StrUtil.trim(configService.getByValTag("infer_backend_camera_overrides"));
        return resolveOverrideDecision(overrideConfig, cameraId).backend;
    }

    public String overrideSourceForCamera(Long cameraId) {
        String overrideConfig = StrUtil.trim(configService.getByValTag("infer_backend_camera_overrides"));
        return resolveOverrideDecision(overrideConfig, cameraId).source;
    }

    public boolean isCameraOverrideHit(Long cameraId) {
        return StrUtil.isNotBlank(overrideBackendForCamera(cameraId));
    }

    public Map<String, Object> health(String traceId) {
        String backend = currentBackendType();
        InferenceClient client = resolveClient(backend);
        Map<String, Object> data = client.health(traceId);
        if (data == null) {
            data = new HashMap<>();
        }
        if (!data.containsKey("trace_id")) {
            data.put("trace_id", traceId);
        }
        if (!data.containsKey("backend")) {
            data.put("backend", backend);
        }
        data.put("route_backend", backend);
        return data;
    }

    public Map<String, Object> circuitStatus(String traceId) {
        String backend = currentBackendType();
        InferenceClient client = resolveClient(backend);
        Map<String, Object> data = client.circuitStatus(traceId);
        if (data == null) {
            data = new HashMap<>();
        }
        if (!data.containsKey("trace_id")) {
            data.put("trace_id", traceId);
        }
        if (!data.containsKey("backend")) {
            data.put("backend", backend);
        }
        data.put("route_backend", backend);
        return data;
    }

    public Map<String, Object> resetCircuit(String traceId) {
        String backend = currentBackendType();
        InferenceClient client = resolveClient(backend);
        Map<String, Object> data = client.resetCircuit(traceId);
        if (data == null) {
            data = new HashMap<>();
        }
        if (!data.containsKey("trace_id")) {
            data.put("trace_id", traceId);
        }
        if (!data.containsKey("backend")) {
            data.put("backend", backend);
        }
        data.put("route_backend", backend);
        return data;
    }

    public InferenceResult infer(InferenceRequest request) {
        return infer(request, null);
    }

    public InferenceResult infer(InferenceRequest request, String backendHint) {
        Long cameraId = request == null ? null : request.getCameraId();
        String backend = backendTypeForCamera(cameraId, backendHint);
        InferenceClient client = resolveClient(backend);

        InferenceResult result = client.infer(request);
        if (result == null) {
            result = new InferenceResult();
            if (request != null) {
                result.setTraceId(request.getTraceId());
                result.setCameraId(request.getCameraId());
            }
            result.setLatencyMs(0L);
            result.setDetections(new ArrayList<>());
        }

        if (StrUtil.isBlank(result.getBackendType())) {
            result.setBackendType(backend);
        }
        return result;
    }

    private InferenceClient resolveClient(String backend) {
        if (BACKEND_RK3588.equalsIgnoreCase(backend)) {
            return rk3588InferenceClient;
        }
        return legacyInferenceClient;
    }

    private OverrideDecision resolveOverrideDecision(String configText, Long cameraId) {
        if (cameraId == null || StrUtil.isBlank(configText)) {
            return NO_OVERRIDE;
        }
        try {
            Object parsed = JSON.parse(configText);
            if (parsed instanceof JSONObject) {
                return resolveFromObject((JSONObject) parsed, cameraId);
            }
            if (parsed instanceof JSONArray) {
                return resolveFromArray((JSONArray) parsed, cameraId);
            }
        } catch (Exception ignored) {
            return NO_OVERRIDE;
        }
        return NO_OVERRIDE;
    }

    private OverrideDecision resolveFromObject(JSONObject root, Long cameraId) {
        if (root == null || cameraId == null) {
            return NO_OVERRIDE;
        }

        String cameraKey = String.valueOf(cameraId);
        String direct = normalizeBackendStrict(root.getString(cameraKey));
        if (StrUtil.isNotBlank(direct)) {
            return new OverrideDecision(direct, "direct_map");
        }

        Object cameraOverridesObj = root.get("camera_overrides");
        if (cameraOverridesObj instanceof JSONObject) {
            String nested = normalizeBackendStrict(((JSONObject) cameraOverridesObj).getString(cameraKey));
            if (StrUtil.isNotBlank(nested)) {
                return new OverrideDecision(nested, "camera_overrides_map");
            }
        } else if (cameraOverridesObj instanceof JSONArray) {
            OverrideDecision nested = resolveFromArray((JSONArray) cameraOverridesObj, cameraId);
            if (StrUtil.isNotBlank(nested.backend)) {
                return new OverrideDecision(nested.backend, "camera_overrides_" + nested.source);
            }
        }

        Object overridesObj = root.get("overrides");
        if (overridesObj instanceof JSONArray) {
            OverrideDecision nested = resolveFromArray((JSONArray) overridesObj, cameraId);
            if (StrUtil.isNotBlank(nested.backend)) {
                return new OverrideDecision(nested.backend, "overrides_" + nested.source);
            }
        }

        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String backend = normalizeBackendStrict(entry.getKey());
            if (StrUtil.isBlank(backend)) {
                continue;
            }
            String groupedSource = detectCameraMatchSource(entry.getValue(), cameraId);
            if (StrUtil.isNotBlank(groupedSource)) {
                return new OverrideDecision(backend, "backend_group_" + groupedSource);
            }
        }
        return NO_OVERRIDE;
    }

    private OverrideDecision resolveFromArray(JSONArray items, Long cameraId) {
        if (items == null || cameraId == null) {
            return NO_OVERRIDE;
        }
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject obj = (JSONObject) item;
            Long itemCameraId = firstLong(
                    toLong(obj.get("camera_id")),
                    toLong(obj.get("cameraId")),
                    toLong(obj.get("id"))
            );
            boolean matchedByCameraId = itemCameraId != null && cameraId.equals(itemCameraId);
            boolean matchedByCameraRange = containsCameraId(obj.get("camera_range"), cameraId);
            boolean matchedByRangeAlias = containsCameraId(obj.get("range"), cameraId);
            boolean matchedByCameraIds = containsCameraId(obj.get("camera_ids"), cameraId);
            boolean matchedByCamerasAlias = containsCameraId(obj.get("cameras"), cameraId);
            boolean matchedByRange = matchedByCameraRange || matchedByRangeAlias || matchedByCameraIds || matchedByCamerasAlias;
            if (!matchedByCameraId && !matchedByRange) {
                continue;
            }

            String backend = firstNonBlank(
                    obj.getString("backend_type"),
                    obj.getString("backend"),
                    obj.getString("type")
            );
            String normalized = normalizeBackendStrict(backend);
            if (StrUtil.isNotBlank(normalized)) {
                String source = "array_match";
                if (matchedByCameraId) {
                    source = "array_camera_id";
                } else if (matchedByCameraRange) {
                    source = "array_camera_range";
                } else if (matchedByRangeAlias) {
                    source = "array_range";
                } else if (matchedByCameraIds) {
                    source = "array_camera_ids";
                } else if (matchedByCamerasAlias) {
                    source = "array_cameras";
                }
                return new OverrideDecision(normalized, source);
            }
        }
        return NO_OVERRIDE;
    }

    private boolean containsCameraId(Object value, Long cameraId) {
        return StrUtil.isNotBlank(detectCameraMatchSource(value, cameraId));
    }

    private boolean matchesCameraExpression(String value, Long cameraId) {
        return StrUtil.isNotBlank(cameraExpressionMatchSource(value, cameraId));
    }

    private String detectCameraMatchSource(Object value, Long cameraId) {
        if (value == null || cameraId == null) {
            return null;
        }
        if (value instanceof Number) {
            return cameraId.equals(((Number) value).longValue()) ? "exact" : null;
        }
        if (value instanceof String) {
            return cameraExpressionMatchSource((String) value, cameraId);
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.size(); i++) {
                String source = detectCameraMatchSource(arr.get(i), cameraId);
                if (StrUtil.isNotBlank(source)) {
                    return source;
                }
            }
            return null;
        }
        if (value instanceof List) {
            List<?> arr = (List<?>) value;
            for (Object item : arr) {
                String source = detectCameraMatchSource(item, cameraId);
                if (StrUtil.isNotBlank(source)) {
                    return source;
                }
            }
            return null;
        }
        return null;
    }

    private String cameraExpressionMatchSource(String value, Long cameraId) {
        if (cameraId == null || StrUtil.isBlank(value)) {
            return null;
        }
        String text = StrUtil.trim(value);
        if (StrUtil.isBlank(text)) {
            return null;
        }
        if (text.contains(",")) {
            String[] parts = text.split(",");
            for (String part : parts) {
                if (StrUtil.isNotBlank(cameraExpressionMatchSource(part, cameraId))) {
                    return "comma";
                }
            }
            return null;
        }

        Long exact = toLong(text);
        if (exact != null) {
            return cameraId.equals(exact) ? "exact" : null;
        }
        if (!text.contains("-")) {
            return null;
        }

        String[] parts = text.split("-", 2);
        if (parts.length != 2) {
            return null;
        }
        Long start = toLong(parts[0]);
        Long end = toLong(parts[1]);
        if (start == null || end == null) {
            return null;
        }
        long min = Math.min(start, end);
        long max = Math.max(start, end);
        return (cameraId >= min && cameraId <= max) ? "range" : null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long firstLong(Long first, Long second, Long third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    private String firstNonBlank(String first, String second, String third) {
        if (StrUtil.isNotBlank(first)) {
            return first;
        }
        if (StrUtil.isNotBlank(second)) {
            return second;
        }
        return third;
    }

    private String normalizeBackendOrDefault(String backend) {
        String normalized = normalizeBackendStrict(backend);
        return StrUtil.isBlank(normalized) ? BACKEND_LEGACY : normalized;
    }

    private String normalizeBackendStrict(String backend) {
        String value = StrUtil.trim(backend);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        if (BACKEND_RK3588.equalsIgnoreCase(value)) {
            return BACKEND_RK3588;
        }
        if (BACKEND_LEGACY.equalsIgnoreCase(value)) {
            return BACKEND_LEGACY;
        }
        return null;
    }

    private static class OverrideDecision {
        private final String backend;
        private final String source;

        private OverrideDecision(String backend, String source) {
            this.backend = backend;
            this.source = source;
        }
    }
}
