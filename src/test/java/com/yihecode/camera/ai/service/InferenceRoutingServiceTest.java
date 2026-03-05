package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.vo.InferenceRequest;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceRoutingServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private LegacyInferenceClient legacyInferenceClient;

    @Mock
    private Rk3588InferenceClient rk3588InferenceClient;

    @InjectMocks
    private InferenceRoutingService inferenceRoutingService;

    @Test
    void currentBackendType_shouldReturnRk3588_whenConfigIsRk3588() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("rk3588_rknn");

        String backend = inferenceRoutingService.currentBackendType();

        assertEquals("rk3588_rknn", backend);
    }

    @Test
    void health_shouldFillTraceIdAndBackend_whenUpstreamMissingFields() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("legacy");
        Map<String, Object> upstream = new HashMap<>();
        upstream.put("status", "ok");
        when(legacyInferenceClient.health("trace-1")).thenReturn(upstream);

        Map<String, Object> result = inferenceRoutingService.health("trace-1");

        assertEquals("trace-1", result.get("trace_id"));
        assertEquals("legacy", result.get("backend"));
        assertEquals("legacy", result.get("route_backend"));
        assertEquals("ok", result.get("status"));
        verify(legacyInferenceClient).health("trace-1");
        verifyNoInteractions(rk3588InferenceClient);
    }

    @Test
    void infer_shouldReturnFallbackResult_whenClientReturnsNull() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("legacy");
        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-2");
        request.setCameraId(101L);
        when(legacyInferenceClient.infer(request)).thenReturn(null);

        InferenceResult result = inferenceRoutingService.infer(request);

        assertNotNull(result);
        assertEquals("trace-2", result.getTraceId());
        assertEquals(101L, result.getCameraId());
        assertEquals(0L, result.getLatencyMs());
        assertEquals("legacy", result.getBackendType());
        assertNotNull(result.getDetections());
        assertTrue(result.getDetections().isEmpty());
        verify(legacyInferenceClient).infer(request);
        verifyNoInteractions(rk3588InferenceClient);
    }
}
