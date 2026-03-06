package com.yihecode.camera.ai.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginInferenceDispatchServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private InferenceHttpGateway inferenceHttpGateway;

    @InjectMocks
    private PluginInferenceDispatchService pluginInferenceDispatchService;

    @Test
    void isDispatchable_shouldReturnFalse_whenPluginUnavailable() {
        Map<String, Object> pluginRoute = new HashMap<>();
        pluginRoute.put("requested", true);
        pluginRoute.put("matched", true);
        pluginRoute.put("available", false);
        pluginRoute.put("plugin", Map.of("health_url", "http://plugin-a:19090/health"));

        assertFalse(pluginInferenceDispatchService.isDispatchable(pluginRoute));
    }

    @Test
    void infer_shouldCallDerivedInferUrl_whenHealthUrlPresent() {
        when(configService.getByValTag("plugin_infer_timeout_ms")).thenReturn("2500");
        when(inferenceHttpGateway.postJson(eq("http://plugin-a:19090/v1/infer"), eq(2500), anyString()))
                .thenReturn(InferenceHttpResponse.of(200, "{\"trace_id\":\"trace-plugin-infer\",\"camera_id\":701,\"latency_ms\":6,\"detections\":[{\"label\":\"person\"}]}"));

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-plugin-infer");
        request.setCameraId(701L);
        request.setModelId(801L);
        request.setFrameMeta(new HashMap<>());

        Map<String, Object> pluginRoute = new HashMap<>();
        pluginRoute.put("requested", true);
        pluginRoute.put("matched", true);
        pluginRoute.put("available", true);
        pluginRoute.put("backend_hint", "rk3588_rknn");
        pluginRoute.put("plugin", Map.of(
                "registration_id", "face-detector:1.0.0",
                "runtime", "rk3588_rknn",
                "health_url", "http://plugin-a:19090/health"
        ));
        request.setPluginRoute(pluginRoute);

        InferenceResult result = pluginInferenceDispatchService.infer(request, pluginRoute);

        assertEquals("trace-plugin-infer", result.getTraceId());
        assertEquals(701L, result.getCameraId());
        assertEquals("rk3588_rknn", result.getBackendType());
        assertEquals(1, result.getAttempt());
        assertEquals(1, result.getDetections().size());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(inferenceHttpGateway).postJson(eq("http://plugin-a:19090/v1/infer"), eq(2500), payloadCaptor.capture());
        JSONObject payload = JSON.parseObject(payloadCaptor.getValue());
        assertNotNull(payload.getJSONObject("plugin_route"));
        assertEquals("face-detector:1.0.0", payload.getJSONObject("plugin_route").getJSONObject("plugin").getString("registration_id"));
    }

    @Test
    void infer_shouldThrow_whenPluginResponseIsInvalid() {
        when(inferenceHttpGateway.postJson(eq("http://plugin-a:19090/v1/infer"), eq(3000), anyString()))
                .thenReturn(InferenceHttpResponse.of(200, "not-json"));

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-plugin-invalid");
        request.setCameraId(702L);
        request.setModelId(802L);
        request.setFrameMeta(new HashMap<>());

        Map<String, Object> pluginRoute = new HashMap<>();
        pluginRoute.put("requested", true);
        pluginRoute.put("matched", true);
        pluginRoute.put("available", true);
        pluginRoute.put("plugin", Map.of("health_url", "http://plugin-a:19090/health", "runtime", "rk3588_rknn"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pluginInferenceDispatchService.infer(request, pluginRoute));
        assertTrue(ex.getMessage().contains("invalid plugin inference response body"));
    }
    @Test
    void infer_shouldPreferExplicitInferUrl_overHealthUrlDerivedPath() {
        when(inferenceHttpGateway.postJson(eq("http://plugin-c:29090/custom-infer"), eq(3000), anyString()))
                .thenReturn(InferenceHttpResponse.of(200, "{\"trace_id\":\"trace-plugin-explicit\",\"camera_id\":703,\"latency_ms\":2,\"detections\":[]}"));

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-plugin-explicit");
        request.setCameraId(703L);
        request.setModelId(803L);
        request.setFrameMeta(new HashMap<>());

        Map<String, Object> pluginRoute = new HashMap<>();
        pluginRoute.put("requested", true);
        pluginRoute.put("matched", true);
        pluginRoute.put("available", true);
        pluginRoute.put("plugin", Map.of(
                "infer_url", "http://plugin-c:29090/custom-infer",
                "health_url", "http://plugin-c:29090/health",
                "runtime", "rk3588_rknn"
        ));

        InferenceResult result = pluginInferenceDispatchService.infer(request, pluginRoute);

        assertEquals("trace-plugin-explicit", result.getTraceId());
        verify(inferenceHttpGateway).postJson(eq("http://plugin-c:29090/custom-infer"), eq(3000), anyString());
    }
    @Test
    void infer_shouldRetryWhenFirstAttemptThrows_thenSucceed() {
        when(configService.getByValTag("plugin_infer_retry_count")).thenReturn("2");
        when(configService.getByValTag("plugin_infer_timeout_ms")).thenReturn("3000");
        when(inferenceHttpGateway.postJson(eq("http://plugin-d:19090/v1/infer"), eq(3000), anyString()))
                .thenThrow(new IllegalStateException("timeout"))
                .thenReturn(InferenceHttpResponse.of(200, "{\"trace_id\":\"trace-plugin-retry\",\"camera_id\":704,\"latency_ms\":9,\"detections\":[]}"));

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-plugin-retry");
        request.setCameraId(704L);
        request.setModelId(804L);
        request.setFrameMeta(new HashMap<>());

        Map<String, Object> pluginRoute = new HashMap<>();
        pluginRoute.put("requested", true);
        pluginRoute.put("matched", true);
        pluginRoute.put("available", true);
        pluginRoute.put("plugin", Map.of(
                "health_url", "http://plugin-d:19090/health",
                "runtime", "rk3588_rknn"
        ));

        InferenceResult result = pluginInferenceDispatchService.infer(request, pluginRoute);

        assertEquals("trace-plugin-retry", result.getTraceId());
        assertEquals(2, result.getAttempt());
        verify(inferenceHttpGateway, times(2)).postJson(eq("http://plugin-d:19090/v1/infer"), eq(3000), anyString());
    }
}
