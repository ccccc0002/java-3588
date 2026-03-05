package com.yihecode.camera.ai.service;

public class InferenceHttpResponse {

    private final int status;

    private final String body;

    public InferenceHttpResponse(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public static InferenceHttpResponse of(int status, String body) {
        return new InferenceHttpResponse(status, body);
    }
}
