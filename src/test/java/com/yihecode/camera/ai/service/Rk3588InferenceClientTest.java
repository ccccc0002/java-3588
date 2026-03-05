package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Rk3588InferenceClientTest {

    @Mock
    private ConfigService configService;

    @Mock
    private InferenceHttpGateway inferenceHttpGateway;

    @InjectMocks
    private Rk3588InferenceClient rk3588InferenceClient;

    @Test
    void health_shouldReturnMisconfigured_whenServiceUrlBlank() {
        when(configService.getByValTag("infer_service_url")).thenReturn(" ");

        Map<String, Object> data = rk3588InferenceClient.health("trace-health-1");

        assertEquals("misconfigured", data.get("status"));
        assertEquals("infer_service_url is blank", data.get("error"));
        assertEquals("rk3588_rknn", data.get("backend"));
        verifyNoInteractions(inferenceHttpGateway);
    }

    @Test
    void health_shouldRetryAndReturnOk_onSecondAttempt() {
        when(configService.getByValTag("infer_service_url")).thenReturn("http://rkhost:18080/");
        when(configService.getByValTag("infer_timeout_ms")).thenReturn("1200");
        when(configService.getByValTag("infer_retry_count")).thenReturn("2");
        when(inferenceHttpGateway.get(eq("http://rkhost:18080/health"), eq(1200)))
                .thenReturn(InferenceHttpResponse.of(500, "{\"error\":\"busy\"}"))
                .thenReturn(InferenceHttpResponse.of(200, "{\"node\":\"rk-a\",\"ready\":true}"));

        Map<String, Object> data = rk3588InferenceClient.health("trace-health-2");

        assertEquals("ok", data.get("status"));
        assertEquals(200, data.get("http_status"));
        assertEquals(2, data.get("attempt"));
        assertEquals("rk-a", data.get("node"));
        assertEquals(Boolean.TRUE, data.get("ready"));
    }

    @Test
    void infer_shouldRetryAndFallbackTraceCamera_whenSecondAttemptSucceeds() {
        when(configService.getByValTag("infer_service_url")).thenReturn("http://rkhost:18080/");
        when(configService.getByValTag("infer_timeout_ms")).thenReturn("800");
        when(configService.getByValTag("infer_retry_count")).thenReturn("2");
        when(inferenceHttpGateway.postJson(eq("http://rkhost:18080/v1/infer"), eq(800), anyString()))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(InferenceHttpResponse.of(200, "{\"latency_ms\":25,\"detections\":[{\"label\":\"person\",\"score\":0.99}]}"));

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-infer-1");
        request.setCameraId(100L);
        request.setModelId(300L);
        request.setFrameMeta(new HashMap<>());

        InferenceResult result = rk3588InferenceClient.infer(request);

        assertNotNull(result);
        assertEquals("trace-infer-1", result.getTraceId());
        assertEquals(100L, result.getCameraId());
        assertEquals(25L, result.getLatencyMs());
        assertEquals("rk3588_rknn", result.getBackendType());
        assertEquals(2, result.getAttempt());
        List<Map<String, Object>> detections = result.getDetections();
        assertEquals(1, detections.size());
        assertEquals("person", detections.get(0).get("label"));
    }

    @Test
    void infer_shouldThrow_whenResponseBodyInvalidAfterRetry() {
        when(configService.getByValTag("infer_service_url")).thenReturn("http://rkhost:18080");
        when(configService.getByValTag("infer_retry_count")).thenReturn("2");
        when(inferenceHttpGateway.postJson(eq("http://rkhost:18080/v1/infer"), eq(3000), anyString()))
                .thenReturn(InferenceHttpResponse.of(200, "invalid-json"))
                .thenReturn(InferenceHttpResponse.of(200, ""));

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-infer-2");
        request.setCameraId(101L);
        request.setModelId(301L);
        request.setFrameMeta(new HashMap<>());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> rk3588InferenceClient.infer(request));
        assertTrue(ex.getMessage().contains("rk3588 inference failed"));
    }
}
