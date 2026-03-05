package com.yihecode.camera.ai.web.api;

import com.yihecode.camera.ai.service.InferenceIdempotencyService;
import com.yihecode.camera.ai.service.InferenceReportBridgeService;
import com.yihecode.camera.ai.service.InferenceRoutingService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceApiControllerTest {

    @Mock
    private InferenceRoutingService inferenceRoutingService;

    @Mock
    private InferenceReportBridgeService inferenceReportBridgeService;

    @Mock
    private InferenceIdempotencyService inferenceIdempotencyService;

    @InjectMocks
    private InferenceApiController inferenceApiController;

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_shouldShortCircuit_whenIdempotentDuplicate() {
        Map<String, Object> body = new HashMap<>();
        body.put("trace_id", "trace-1");
        body.put("camera_id", 100L);
        body.put("model_id", 200L);
        Map<String, Object> frameMeta = new HashMap<>();
        frameMeta.put("timestamp_ms", 123L);
        body.put("frame_meta", frameMeta);

        Map<String, Object> idempotentData = new HashMap<>();
        idempotentData.put("enabled", true);
        idempotentData.put("duplicate", true);
        idempotentData.put("status", "duplicate");
        idempotentData.put("trace_id", "trace-1");
        idempotentData.put("camera_id", 100L);
        idempotentData.put("timestamp_ms", 123L);

        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceIdempotencyService.checkAndMark("trace-1", 100L, 123L)).thenReturn(idempotentData);

        JsonResult result = inferenceApiController.dispatch(body, null, null, null, null, 1);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(200L, ((Number) data.get("algorithm_id")).longValue());

        Map<String, Object> report = (Map<String, Object>) data.get("report");
        assertEquals("duplicate", report.get("status"));
        assertFalse((Boolean) report.get("persisted"));

        Map<String, Object> inferResult = (Map<String, Object>) data.get("result");
        assertEquals(0, ((Number) inferResult.get("attempt")).intValue());
        assertTrue(((List<?>) inferResult.get("detections")).isEmpty());

        verify(inferenceRoutingService, never()).infer(any());
        verify(inferenceReportBridgeService, never()).persistAndBroadcast(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_shouldCallInferAndPersist_whenFreshRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("trace_id", "trace-2");
        body.put("camera_id", 101L);
        body.put("model_id", 201L);
        body.put("algorithm_id", 301L);
        Map<String, Object> frameMeta = new HashMap<>();
        frameMeta.put("timestamp_ms", 456L);
        body.put("frame_meta", frameMeta);

        Map<String, Object> idempotentData = new HashMap<>();
        idempotentData.put("enabled", true);
        idempotentData.put("duplicate", false);
        idempotentData.put("status", "fresh");
        idempotentData.put("trace_id", "trace-2");
        idempotentData.put("camera_id", 101L);
        idempotentData.put("timestamp_ms", 456L);

        InferenceResult infer = new InferenceResult();
        infer.setTraceId("trace-2");
        infer.setCameraId(101L);
        infer.setLatencyMs(9L);
        infer.setBackendType("rk3588_rknn");
        infer.setAttempt(1);
        infer.setDetections(new ArrayList<>());

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("status", "ok");
        reportData.put("persisted", true);
        reportData.put("broadcasted", true);

        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(101L)).thenReturn("rk3588_rknn");
        when(inferenceIdempotencyService.checkAndMark("trace-2", 101L, 456L)).thenReturn(idempotentData);
        when(inferenceRoutingService.infer(any())).thenReturn(infer);
        when(inferenceReportBridgeService.persistAndBroadcast(eq(101L), eq(301L), eq(infer), eq("trace-2")))
                .thenReturn(reportData);

        JsonResult result = inferenceApiController.dispatch(body, null, null, null, null, 1);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(301L, ((Number) data.get("algorithm_id")).longValue());
        assertEquals("rk3588_rknn", data.get("backend_type"));

        Map<String, Object> report = (Map<String, Object>) data.get("report");
        assertEquals("ok", report.get("status"));
        assertTrue((Boolean) report.get("persisted"));

        verify(inferenceRoutingService).infer(any());
        verify(inferenceReportBridgeService).persistAndBroadcast(eq(101L), eq(301L), eq(infer), eq("trace-2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_shouldSkipIdempotentAndPersist_whenPersistReportDisabled() {
        Map<String, Object> body = new HashMap<>();
        body.put("trace_id", "trace-3");
        body.put("camera_id", 102L);
        body.put("model_id", 202L);
        body.put("persist_report", 0);
        Map<String, Object> frameMeta = new HashMap<>();
        frameMeta.put("timestamp_ms", 789L);
        body.put("frame_meta", frameMeta);

        InferenceResult infer = new InferenceResult();
        infer.setTraceId("trace-3");
        infer.setCameraId(102L);
        infer.setLatencyMs(3L);
        infer.setBackendType("legacy");
        infer.setAttempt(1);
        infer.setDetections(new ArrayList<>());

        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.infer(any())).thenReturn(infer);

        JsonResult result = inferenceApiController.dispatch(body, null, null, null, null, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Map<String, Object> idempotent = (Map<String, Object>) data.get("idempotent");
        Map<String, Object> report = (Map<String, Object>) data.get("report");

        assertFalse((Boolean) idempotent.get("enabled"));
        assertEquals("disabled", idempotent.get("status"));
        assertEquals("skipped", report.get("status"));

        verify(inferenceRoutingService).infer(any());
        verify(inferenceIdempotencyService, never()).checkAndMark(anyString(), anyLong(), anyLong());
        verify(inferenceReportBridgeService, never()).persistAndBroadcast(anyLong(), anyLong(), any(), anyString());
    }
}
