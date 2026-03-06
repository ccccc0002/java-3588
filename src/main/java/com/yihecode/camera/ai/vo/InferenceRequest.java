package com.yihecode.camera.ai.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class InferenceRequest {

    private String traceId;

    private Long cameraId;

    private Long modelId;

    private Map<String, Object> frameMeta;

    private List<Map<String, Object>> roi;

    private Map<String, Object> pluginRoute;

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("trace_id", traceId);
        payload.put("camera_id", cameraId);
        payload.put("model_id", modelId);
        payload.put("frame", frameMeta == null ? new HashMap<>() : new HashMap<>(frameMeta));
        payload.put("roi", roi == null ? new ArrayList<>() : new ArrayList<>(roi));
        if (pluginRoute != null && !pluginRoute.isEmpty()) {
            payload.put("plugin_route", new HashMap<>(pluginRoute));
        }
        return payload;
    }
}