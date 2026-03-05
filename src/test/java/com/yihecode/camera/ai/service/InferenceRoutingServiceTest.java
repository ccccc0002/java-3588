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
import static org.mockito.Mockito.never;
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
        when(configService.getByValTag("infer_backend_camera_overrides")).thenReturn("");
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

    @Test
    void infer_shouldUseCameraOverrideBackend_whenOverrideConfiguredAsMap() {
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("{\"100\":\"rk3588_rknn\"}");

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-override-map");
        request.setCameraId(100L);

        InferenceResult rkResult = new InferenceResult();
        rkResult.setTraceId("trace-override-map");
        rkResult.setCameraId(100L);
        rkResult.setLatencyMs(8L);
        rkResult.setBackendType("rk3588_rknn");
        when(rk3588InferenceClient.infer(request)).thenReturn(rkResult);

        InferenceResult result = inferenceRoutingService.infer(request);

        assertEquals("rk3588_rknn", result.getBackendType());
        verify(rk3588InferenceClient).infer(request);
        verify(legacyInferenceClient, never()).infer(request);
    }

    @Test
    void infer_shouldUseCameraOverrideBackend_whenOverrideConfiguredAsArray() {
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("[{\"camera_id\":101,\"backend_type\":\"rk3588_rknn\"}]");

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-override-array");
        request.setCameraId(101L);

        InferenceResult rkResult = new InferenceResult();
        rkResult.setTraceId("trace-override-array");
        rkResult.setCameraId(101L);
        rkResult.setLatencyMs(12L);
        rkResult.setBackendType("rk3588_rknn");
        when(rk3588InferenceClient.infer(request)).thenReturn(rkResult);

        InferenceResult result = inferenceRoutingService.infer(request);

        assertEquals("rk3588_rknn", result.getBackendType());
        verify(rk3588InferenceClient).infer(request);
        verify(legacyInferenceClient, never()).infer(request);
    }

    @Test
    void infer_shouldFallbackToGlobalBackend_whenOverrideBackendInvalid() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("legacy");
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("{\"100\":\"unknown_backend\"}");

        InferenceRequest request = new InferenceRequest();
        request.setTraceId("trace-invalid-override");
        request.setCameraId(100L);

        InferenceResult legacyResult = new InferenceResult();
        legacyResult.setTraceId("trace-invalid-override");
        legacyResult.setCameraId(100L);
        legacyResult.setLatencyMs(6L);
        legacyResult.setBackendType("legacy");
        when(legacyInferenceClient.infer(request)).thenReturn(legacyResult);

        InferenceResult result = inferenceRoutingService.infer(request);

        assertEquals("legacy", result.getBackendType());
        verify(legacyInferenceClient).infer(request);
        verify(rk3588InferenceClient, never()).infer(request);
    }

    @Test
    void backendTypeForCamera_shouldResolveFromBackendGroupedArray() {
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("{\"rk3588_rknn\":[200,201],\"legacy\":[300]}");

        String backend201 = inferenceRoutingService.backendTypeForCamera(201L);
        String backend300 = inferenceRoutingService.backendTypeForCamera(300L);

        assertEquals("rk3588_rknn", backend201);
        assertEquals("legacy", backend300);
    }

    @Test
    void backendTypeForCamera_shouldResolveFromNestedCameraOverrides_thenFallbackGlobal() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("legacy");
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("{\"camera_overrides\":{\"100\":\"rk3588_rknn\"}}");

        String backend100 = inferenceRoutingService.backendTypeForCamera(100L);
        String backend101 = inferenceRoutingService.backendTypeForCamera(101L);

        assertEquals("rk3588_rknn", backend100);
        assertEquals("legacy", backend101);
    }

    @Test
    void backendTypeForCamera_shouldResolveFromOverridesArrayNode() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("legacy");
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("{\"overrides\":[{\"id\":888,\"backend\":\"rk3588_rknn\"}]}");

        String backend888 = inferenceRoutingService.backendTypeForCamera(888L);
        String backend889 = inferenceRoutingService.backendTypeForCamera(889L);

        assertEquals("rk3588_rknn", backend888);
        assertEquals("legacy", backend889);
    }

    @Test
    void backendTypeForCamera_shouldFallbackGlobal_whenOverrideJsonMalformed() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("legacy");
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("{not-json");

        String backend = inferenceRoutingService.backendTypeForCamera(1000L);

        assertEquals("legacy", backend);
    }

    @Test
    void backendTypeForCamera_shouldResolveWhenBackendGroupContainsRangeText() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("legacy");
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("{\"rk3588_rknn\":[\"100-105\"],\"legacy\":[\"200-205\"]}");

        String backend102 = inferenceRoutingService.backendTypeForCamera(102L);
        String backend206 = inferenceRoutingService.backendTypeForCamera(206L);

        assertEquals("rk3588_rknn", backend102);
        assertEquals("legacy", backend206);
    }

    @Test
    void backendTypeForCamera_shouldResolveFromArrayItemCameraRange() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("legacy");
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("[{\"camera_range\":\"300-320\",\"backend_type\":\"rk3588_rknn\"}]");

        String backend310 = inferenceRoutingService.backendTypeForCamera(310L);
        String backend330 = inferenceRoutingService.backendTypeForCamera(330L);

        assertEquals("rk3588_rknn", backend310);
        assertEquals("legacy", backend330);
    }

    @Test
    void backendTypeForCamera_shouldResolveFromArrayItemRangeAlias() {
        when(configService.getByValTag("infer_backend_type")).thenReturn("legacy");
        when(configService.getByValTag("infer_backend_camera_overrides"))
                .thenReturn("[{\"range\":\"420-400\",\"backend\":\"rk3588_rknn\"}]");

        String backend410 = inferenceRoutingService.backendTypeForCamera(410L);
        String backend430 = inferenceRoutingService.backendTypeForCamera(430L);

        assertEquals("rk3588_rknn", backend410);
        assertEquals("legacy", backend430);
    }
}
