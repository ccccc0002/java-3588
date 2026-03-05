package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;

import java.util.Map;

public interface InferenceClient {

    String getBackendType();

    Map<String, Object> health(String traceId);

    InferenceResult infer(InferenceRequest request);
}
