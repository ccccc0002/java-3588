package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;

import java.util.HashMap;
import java.util.Map;

public interface InferenceClient {

    String getBackendType();

    Map<String, Object> health(String traceId);

    InferenceResult infer(InferenceRequest request);

    default Map<String, Object> circuitStatus(String traceId) {
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("backend", getBackendType());
        data.put("supported", false);
        data.put("status", "unsupported");
        data.put("circuit_open", false);
        data.put("circuit_open_until_ms", 0L);
        return data;
    }

    default Map<String, Object> resetCircuit(String traceId) {
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("backend", getBackendType());
        data.put("supported", false);
        data.put("status", "unsupported");
        data.put("reset", false);
        data.put("circuit_open", false);
        data.put("circuit_open_until_ms", 0L);
        return data;
    }
}
