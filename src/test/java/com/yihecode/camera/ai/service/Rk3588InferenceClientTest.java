package com.yihecode.camera.ai.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.lenient;
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

    @BeforeEach
    void setUp() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
    }

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
        when(configService.getByValTag("infer_timeout_ms")).thenReturn("3000");
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

    @Test
    void infer_shouldIncludeDecodeHints_whenDecodeConfigProvided() {
        when(configService.getByValTag("infer_service_url")).thenReturn("http://rkhost:18080");
        when(configService.getByValTag("infer_timeout_ms")).thenReturn("1000");
        when(configService.getByValTag("infer_retry_count")).thenReturn("1");
        when(configService.getByValTag("infer_decode_backend")).thenReturn("mpp");
        when(configService.getByValTag("infer_decode_hwaccel")).thenReturn("rga");
        when(configService.getByValTag("infer_decode_max_width")).thenReturn("1920");
        when(configService.getByValTag("infer_decode_max_height")).thenReturn("1080");
        when(inferenceHttpGateway.postJson(eq("http://rkhost:18080/v1/infer"), eq(1000), anyString()))
                .thenReturn(InferenceHttpResponse.of(200, "{\"trace_id\":\"trace-decode\",\"camera_id\":501,\"latency_ms\":5,\"detections\":[]}"));

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-decode");
        request.setCameraId(501L);
        request.setModelId(900L);
        request.setFrameMeta(new HashMap<>());

        InferenceResult result = rk3588InferenceClient.infer(request);

        assertEquals("trace-decode", result.getTraceId());
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(inferenceHttpGateway).postJson(eq("http://rkhost:18080/v1/infer"), eq(1000), payloadCaptor.capture());
        JSONObject payload = JSON.parseObject(payloadCaptor.getValue());
        JSONObject decode = payload.getJSONObject("decode");
        assertNotNull(decode);
        assertEquals("mpp", decode.getString("backend"));
        assertEquals("rga", decode.getString("hwaccel"));
        assertEquals(1920, decode.getIntValue("max_width"));
        assertEquals(1080, decode.getIntValue("max_height"));
    }

    @Test
    void infer_shouldNotIncludeDecodeHints_whenDecodeConfigMissing() {
        when(configService.getByValTag("infer_service_url")).thenReturn("http://rkhost:18080");
        when(configService.getByValTag("infer_timeout_ms")).thenReturn("1000");
        when(configService.getByValTag("infer_retry_count")).thenReturn("1");
        when(inferenceHttpGateway.postJson(eq("http://rkhost:18080/v1/infer"), eq(1000), anyString()))
                .thenReturn(InferenceHttpResponse.of(200, "{\"trace_id\":\"trace-nodecode\",\"camera_id\":502,\"latency_ms\":6,\"detections\":[]}"));

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-nodecode");
        request.setCameraId(502L);
        request.setModelId(901L);
        request.setFrameMeta(new HashMap<>());

        InferenceResult result = rk3588InferenceClient.infer(request);

        assertEquals("trace-nodecode", result.getTraceId());
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(inferenceHttpGateway).postJson(eq("http://rkhost:18080/v1/infer"), eq(1000), payloadCaptor.capture());
        JSONObject payload = JSON.parseObject(payloadCaptor.getValue());
        assertTrue(payload.getJSONObject("decode") == null);
    }

    @Test
    void infer_shouldConvertSingleDetectionObjectToList() {
        when(configService.getByValTag("infer_service_url")).thenReturn("http://rkhost:18080");
        when(configService.getByValTag("infer_timeout_ms")).thenReturn("1000");
        when(configService.getByValTag("infer_retry_count")).thenReturn("1");
        when(inferenceHttpGateway.postJson(eq("http://rkhost:18080/v1/infer"), eq(1000), anyString()))
                .thenReturn(InferenceHttpResponse.of(200, "{\"trace_id\":\"trace-one\",\"camera_id\":503,\"latency_ms\":11,\"detections\":{\"label\":\"forklift\",\"score\":0.88}}"));

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-one");
        request.setCameraId(503L);
        request.setModelId(902L);
        request.setFrameMeta(new HashMap<>());

        InferenceResult result = rk3588InferenceClient.infer(request);

        assertNotNull(result.getDetections());
        assertEquals(1, result.getDetections().size());
        assertEquals("forklift", result.getDetections().get(0).get("label"));
    }
}
