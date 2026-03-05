package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
public class InferenceRoutingService {

    @Autowired
    private ConfigService configService;

    @Autowired
    private LegacyInferenceClient legacyInferenceClient;

    @Autowired
    private Rk3588InferenceClient rk3588InferenceClient;

    public String currentBackendType() {
        String backend = StrUtil.trim(configService.getByValTag("infer_backend_type"));
        if ("rk3588_rknn".equalsIgnoreCase(backend)) {
            return "rk3588_rknn";
        }
        return "legacy";
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
        String backend = currentBackendType();
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
        if ("rk3588_rknn".equalsIgnoreCase(backend)) {
            return rk3588InferenceClient;
        }
        return legacyInferenceClient;
    }
}
