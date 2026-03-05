package com.yihecode.camera.ai.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.springframework.stereotype.Service;

@Service
public class HutoolInferenceHttpGateway implements InferenceHttpGateway {

    @Override
    public InferenceHttpResponse get(String url, int timeoutMs) {
        HttpResponse response = HttpRequest.get(url)
                .timeout(timeoutMs)
                .execute();
        return InferenceHttpResponse.of(response.getStatus(), response.body());
    }

    @Override
    public InferenceHttpResponse postJson(String url, int timeoutMs, String body) {
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .timeout(timeoutMs)
                .body(body)
                .execute();
        return InferenceHttpResponse.of(response.getStatus(), response.body());
    }
}
