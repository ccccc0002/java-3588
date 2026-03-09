package com.yihecode.camera.ai.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class InferenceResult {

    private String traceId;

    private Long cameraId;

    private Long latencyMs;

    private List<Map<String, Object>> detections;

    /**
     * Explicit alert payload from plugin/runtime. When this field is non-null, it controls
     * whether alarm persistence and broadcast should happen.
     */
    private List<Map<String, Object>> alerts;

    /**
     * Structured alert events emitted by plugin/runtime for downstream bridge usage.
     */
    private List<Map<String, Object>> events;

    private String backendType;

    private Integer attempt;

    private String rawBody;

    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("camera_id", cameraId);
        data.put("latency_ms", latencyMs);
        data.put("detections", detections == null ? new ArrayList<>() : detections);
        data.put("alerts", alerts == null ? new ArrayList<>() : alerts);
        data.put("events", events == null ? new ArrayList<>() : events);
        data.put("backend_type", backendType);
        data.put("attempt", attempt);
        return data;
    }

    public boolean hasExplicitAlerts() {
        return alerts != null;
    }

    public List<Map<String, Object>> resolveAlarmPayload() {
        if (alerts != null) {
            return alerts;
        }
        return detections == null ? new ArrayList<>() : detections;
    }
}
