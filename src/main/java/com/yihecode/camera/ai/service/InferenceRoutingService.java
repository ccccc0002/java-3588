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

    public String overrideBackendForCamera(Long cameraId) {
        String overrideConfig = StrUtil.trim(configService.getByValTag("infer_backend_camera_overrides"));
        return resolveOverrideBackend(overrideConfig, cameraId);
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

    public InferenceResult infer(InferenceRequest request) {
        Long cameraId = request == null ? null : request.getCameraId();
        String backend = backendTypeForCamera(cameraId);
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

    private String resolveOverrideBackend(String configText, Long cameraId) {
        if (cameraId == null || StrUtil.isBlank(configText)) {
            return null;
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
            return null;
        }
        return null;
    }

    private String resolveFromObject(JSONObject root, Long cameraId) {
        if (root == null || cameraId == null) {
            return null;
        }

        String cameraKey = String.valueOf(cameraId);
        String direct = normalizeBackendStrict(root.getString(cameraKey));
        if (StrUtil.isNotBlank(direct)) {
            return direct;
        }

        Object cameraOverridesObj = root.get("camera_overrides");
        if (cameraOverridesObj instanceof JSONObject) {
            String nested = normalizeBackendStrict(((JSONObject) cameraOverridesObj).getString(cameraKey));
            if (StrUtil.isNotBlank(nested)) {
                return nested;
            }
        } else if (cameraOverridesObj instanceof JSONArray) {
            String nested = resolveFromArray((JSONArray) cameraOverridesObj, cameraId);
            if (StrUtil.isNotBlank(nested)) {
                return nested;
            }
        }

        Object overridesObj = root.get("overrides");
        if (overridesObj instanceof JSONArray) {
            String nested = resolveFromArray((JSONArray) overridesObj, cameraId);
            if (StrUtil.isNotBlank(nested)) {
                return nested;
            }
        }

        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String backend = normalizeBackendStrict(entry.getKey());
            if (StrUtil.isBlank(backend)) {
                continue;
            }
            if (containsCameraId(entry.getValue(), cameraId)) {
                return backend;
            }
        }
        return null;
    }

    private String resolveFromArray(JSONArray items, Long cameraId) {
        if (items == null || cameraId == null) {
            return null;
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
            boolean matchedByRange = containsCameraId(obj.get("camera_range"), cameraId)
                    || containsCameraId(obj.get("range"), cameraId)
                    || containsCameraId(obj.get("camera_ids"), cameraId)
                    || containsCameraId(obj.get("cameras"), cameraId);
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
                return normalized;
            }
        }
        return null;
    }

    private boolean containsCameraId(Object value, Long cameraId) {
        if (value == null || cameraId == null) {
            return false;
        }
        if (value instanceof Number) {
            return cameraId.equals(((Number) value).longValue());
        }
        if (value instanceof String) {
            if (matchesCameraExpression((String) value, cameraId)) {
                return true;
            }
            return false;
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.size(); i++) {
                if (containsCameraId(arr.get(i), cameraId)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof List) {
            List<?> arr = (List<?>) value;
            for (Object item : arr) {
                if (containsCameraId(item, cameraId)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private boolean matchesCameraExpression(String value, Long cameraId) {
        if (cameraId == null || StrUtil.isBlank(value)) {
            return false;
        }
        String text = StrUtil.trim(value);
        if (StrUtil.isBlank(text)) {
            return false;
        }
        if (text.contains(",")) {
            String[] parts = text.split(",");
            for (String part : parts) {
                if (matchesCameraExpression(part, cameraId)) {
                    return true;
                }
            }
            return false;
        }

        Long exact = toLong(text);
        if (exact != null) {
            return cameraId.equals(exact);
        }
        if (!text.contains("-")) {
            return false;
        }

        String[] parts = text.split("-", 2);
        if (parts.length != 2) {
            return false;
        }
        Long start = toLong(parts[0]);
        Long end = toLong(parts[1]);
        if (start == null || end == null) {
            return false;
        }
        long min = Math.min(start, end);
        long max = Math.max(start, end);
        return cameraId >= min && cameraId <= max;
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
}
