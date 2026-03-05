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

    private String backendType;

    private Integer attempt;

    private String rawBody;

    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("camera_id", cameraId);
        data.put("latency_ms", latencyMs);
        data.put("detections", detections == null ? new ArrayList<>() : detections);
        data.put("backend_type", backendType);
        data.put("attempt", attempt);
        return data;
    }
}
