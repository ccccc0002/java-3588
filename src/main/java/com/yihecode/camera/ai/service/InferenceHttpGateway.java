package com.yihecode.camera.ai.service;

public interface InferenceHttpGateway {

    InferenceHttpResponse get(String url, int timeoutMs);

    InferenceHttpResponse postJson(String url, int timeoutMs, String body);
}
