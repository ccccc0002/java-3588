package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpPluginHealthProbeServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private InferenceHttpGateway inferenceHttpGateway;

    @InjectMocks
    private HttpPluginHealthProbeService httpPluginHealthProbeService;

    @Test
    void probe_shouldReturnMisconfiguredWhenHealthUrlBlank() {
        Map<String, Object> data = httpPluginHealthProbeService.probe("trace-plugin-health-1", " ");

        assertEquals("misconfigured", data.get("status"));
        assertEquals(Boolean.FALSE, data.get("healthy"));
        assertEquals("plugin health_url is blank", data.get("error"));
        verifyNoInteractions(configService, inferenceHttpGateway);
    }

    @Test
    void probe_shouldReturnOkAndMergeJsonBody() {
        when(configService.getByValTag("plugin_probe_timeout_ms")).thenReturn("1500");
        when(inferenceHttpGateway.get(eq("http://plugin-a:19090/health"), eq(1500)))
                .thenReturn(InferenceHttpResponse.of(200, "{\"status\":\"ok\",\"ready\":true,\"runtime\":\"rk3588_rknn\"}"));

        Map<String, Object> data = httpPluginHealthProbeService.probe("trace-plugin-health-2", "http://plugin-a:19090/health");

        assertEquals(Boolean.TRUE, data.get("healthy"));
        assertEquals("ok", data.get("status"));
        assertEquals(200, data.get("http_status"));
        assertEquals(1500, ((Number) data.get("timeout_ms")).intValue());
        assertEquals(Boolean.TRUE, data.get("ready"));
        assertEquals("rk3588_rknn", data.get("runtime"));
    }

    @Test
    void probe_shouldReturnDownWhenGatewayReturnsNon200() {
        when(configService.getByValTag("plugin_probe_timeout_ms")).thenReturn("800");
        when(inferenceHttpGateway.get(eq("http://plugin-b:19090/health"), eq(800)))
                .thenReturn(InferenceHttpResponse.of(503, "{\"status\":\"down\"}"));

        Map<String, Object> data = httpPluginHealthProbeService.probe("trace-plugin-health-3", "http://plugin-b:19090/health");

        assertEquals(Boolean.FALSE, data.get("healthy"));
        assertEquals("down", data.get("status"));
        assertEquals(503, data.get("http_status"));
        assertTrue(String.valueOf(data.get("error")).contains("503"));
    }
}
