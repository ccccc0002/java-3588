package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
public class LegacyInferenceClient implements InferenceClient {

    @Override
    public String getBackendType() {
        return "legacy";
    }

    @Override
    public Map<String, Object> health(String traceId) {
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("backend", getBackendType());
        data.put("status", "ok");
        data.put("runtime", "legacy");
        data.put("note", "legacy fallback mode");
        return data;
    }

    @Override
    public InferenceResult infer(InferenceRequest request) {
        InferenceResult result = new InferenceResult();
        result.setTraceId(request == null ? null : request.getTraceId());
        result.setCameraId(request == null ? null : request.getCameraId());
        result.setLatencyMs(0L);
        result.setDetections(new ArrayList<>());
        result.setBackendType(getBackendType());
        result.setAttempt(1);
        return result;
    }
}
