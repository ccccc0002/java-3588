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

    @Test
    @SuppressWarnings("unchecked")
    void route_shouldReturnResolvedBackendForCamera() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(100L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.overrideBackendForCamera(100L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.overrideSourceForCamera(100L)).thenReturn("direct_map");

        Map<String, Object> body = new HashMap<>();
        body.put("camera_id", 100L);
        JsonResult result = inferenceApiController.route(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(100L, ((Number) data.get("camera_id")).longValue());
        assertEquals("legacy", data.get("global_backend_type"));
        assertEquals("rk3588_rknn", data.get("backend_type"));
        assertEquals(true, data.get("override_hit"));
        assertEquals("rk3588_rknn", data.get("override_backend_type"));
        assertEquals("direct_map", data.get("override_source"));
        assertTrue(data.get("trace_id") != null && !"".equals(String.valueOf(data.get("trace_id"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void route_shouldAcceptCameraIdFromQueryParam_whenBodyMissing() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(222L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(222L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(222L)).thenReturn(null);

        JsonResult result = inferenceApiController.route(null, 222L);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(222L, ((Number) data.get("camera_id")).longValue());
        assertEquals("legacy", data.get("global_backend_type"));
        assertEquals("legacy", data.get("backend_type"));
        assertEquals(false, data.get("override_hit"));
        assertEquals(null, data.get("override_source"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void idempotentStats_shouldReturnServiceData() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("key_size", 12);
        stats.put("window_ms", 600000L);
        stats.put("cleanup_trigger_size", 2000);
        when(inferenceIdempotencyService.stats()).thenReturn(stats);

        JsonResult result = inferenceApiController.idempotentStats();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(data.get("trace_id") != null && !"".equals(String.valueOf(data.get("trace_id"))));
        Map<String, Object> idempotent = (Map<String, Object>) data.get("idempotent");
        assertEquals(12, ((Number) idempotent.get("key_size")).intValue());
        assertEquals(600000L, ((Number) idempotent.get("window_ms")).longValue());
        assertEquals(2000, ((Number) idempotent.get("cleanup_trigger_size")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void circuitStatus_shouldReturnServiceData() {
        Map<String, Object> status = new HashMap<>();
        status.put("trace_id", "trace-cs-api");
        status.put("backend", "rk3588_rknn");
        status.put("route_backend", "rk3588_rknn");
        status.put("circuit_open", false);
        when(inferenceRoutingService.circuitStatus(anyString())).thenReturn(status);
        when(inferenceRoutingService.currentBackendType()).thenReturn("rk3588_rknn");

        JsonResult result = inferenceApiController.circuitStatus();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("rk3588_rknn", data.get("backend_type"));
        Map<String, Object> circuit = (Map<String, Object>) data.get("circuit");
        assertEquals(false, circuit.get("circuit_open"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void circuitReset_shouldReturnServiceData() {
        Map<String, Object> reset = new HashMap<>();
        reset.put("trace_id", "trace-cr-api");
        reset.put("backend", "rk3588_rknn");
        reset.put("route_backend", "rk3588_rknn");
        reset.put("reset", true);
        when(inferenceRoutingService.resetCircuit(anyString())).thenReturn(reset);
        when(inferenceRoutingService.currentBackendType()).thenReturn("rk3588_rknn");

        JsonResult result = inferenceApiController.circuitReset();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("rk3588_rknn", data.get("backend_type"));
        Map<String, Object> circuit = (Map<String, Object>) data.get("circuit");
        assertEquals(true, circuit.get("reset"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldReturnResolvedBackendsForMultipleCameras() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(100L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.backendTypeForCamera(101L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(100L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.overrideBackendForCamera(101L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(100L)).thenReturn("direct_map");
        when(inferenceRoutingService.overrideSourceForCamera(101L)).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        List<Object> cameraIds = new ArrayList<>();
        cameraIds.add(100L);
        cameraIds.add(101L);
        body.put("camera_ids", cameraIds);

        JsonResult result = inferenceApiController.routeBatch(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(data.get("trace_id") != null && !"".equals(String.valueOf(data.get("trace_id"))));
        assertEquals("legacy", data.get("global_backend_type"));

        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(2, routes.size());
        assertEquals(100L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals("rk3588_rknn", routes.get(0).get("backend_type"));
        assertEquals(true, routes.get(0).get("override_hit"));
        assertEquals("direct_map", routes.get(0).get("override_source"));
        assertEquals(101L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals("legacy", routes.get(1).get("backend_type"));
        assertEquals(false, routes.get(1).get("override_hit"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldAcceptCommaQueryCameraIds_whenBodyMissing() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(201L)).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(202L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(201L)).thenReturn(null);
        when(inferenceRoutingService.overrideBackendForCamera(202L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(201L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(202L)).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "201,202");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(2, routes.size());
        assertEquals(201L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(202L, ((Number) routes.get(1).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldDeduplicateAndFallbackDefaultCamera() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(1L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(1L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(1L)).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, ",,");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(1, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(true, data.get("default_fallback_used"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldExpandRangeExpressionFromQueryAndPreserveOrder() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "100-102,101,110-108,abc, ,200");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(7, routes.size());
        assertEquals(100L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(101L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(102L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(110L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(109L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(108L, ((Number) routes.get(5).get("camera_id")).longValue());
        assertEquals(200L, ((Number) routes.get(6).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldExpandRangeExpressionFromBodyAndThenQuery() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("camera_ids", "300-302,305");
        JsonResult result = inferenceApiController.routeBatch(body, "304-303");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(6, routes.size());
        assertEquals(300L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(301L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(302L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(305L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(304L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(303L, ((Number) routes.get(5).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldCapExpandedCameraIdsToSafeLimit() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "1-600");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(500, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(500L, ((Number) routes.get(499).get("camera_id")).longValue());
        assertEquals(true, data.get("truncated"));
        assertEquals(500, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(500, ((Number) data.get("max_camera_ids_cap")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldParseRangeTokensInsideBodyListItems() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        List<Object> cameraIds = new ArrayList<>();
        cameraIds.add("10-12");
        cameraIds.add(12L);
        cameraIds.add("15,16");
        cameraIds.add("not-number");
        cameraIds.add(14L);
        body.put("camera_ids", cameraIds);

        JsonResult result = inferenceApiController.routeBatch(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(6, routes.size());
        assertEquals(10L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(11L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(12L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(15L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(16L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(14L, ((Number) routes.get(5).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldReportNotTruncatedWhenWithinLimit() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "1-3");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(false, data.get("truncated"));
        assertEquals(500, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(false, data.get("default_fallback_used"));
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(3, routes.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldAcceptCamerasAliasFromBodyList() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        List<Object> cameras = new ArrayList<>();
        cameras.add(100L);
        cameras.add("101-102");
        body.put("cameras", cameras);

        JsonResult result = inferenceApiController.routeBatch(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(3, routes.size());
        assertEquals(100L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(101L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(102L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldMergeBodyAliasesAndQueryInStableOrder() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("camera_ids", "10");
        body.put("cameras", "11-12,10");
        body.put("camera_range", "20-19");
        body.put("range", "30");

        JsonResult result = inferenceApiController.routeBatch(body, "31,12");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(7, routes.size());
        assertEquals(10L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(11L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(12L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(20L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(19L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(30L, ((Number) routes.get(5).get("camera_id")).longValue());
        assertEquals(31L, ((Number) routes.get(6).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
        List<String> hitSources = (List<String>) data.get("hit_sources");
        assertEquals(5, hitSources.size());
        assertEquals("body_camera_ids", hitSources.get(0));
        assertEquals("body_cameras", hitSources.get(1));
        assertEquals("body_camera_range", hitSources.get(2));
        assertEquals("body_range", hitSources.get(3));
        assertEquals("query_camera_ids", hitSources.get(4));
        assertEquals(null, data.get("truncated_source"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        assertEquals(5, sourceStats.size());
        Map<String, Object> bodyCamerasStats = (Map<String, Object>) sourceStats.get("body_cameras");
        assertEquals(2, ((Number) bodyCamerasStats.get("input_token_count")).intValue());
        assertEquals(3, ((Number) bodyCamerasStats.get("expanded_candidate_count")).intValue());
        assertEquals(1, ((Number) bodyCamerasStats.get("duplicate_filtered_count")).intValue());
        assertEquals(2, ((Number) bodyCamerasStats.get("unique_added_count")).intValue());
        assertEquals(false, bodyCamerasStats.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldAcceptQueryCamerasAlias() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, null, "501,503-504", null, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(3, routes.size());
        assertEquals(501L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(503L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(504L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldAcceptQueryCameraRangeAlias() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, null, null, "620-618", null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(3, routes.size());
        assertEquals(620L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(619L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(618L, ((Number) routes.get(2).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldMergeAllQueryAliasesInStableOrder() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(
                null,
                "700,701",
                "701,702",
                "703-704",
                "705,704"
        );

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(6, routes.size());
        assertEquals(700L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(701L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(702L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(703L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(704L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(705L, ((Number) routes.get(5).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldFallbackDefaultCameraWhenAllQueryAliasesInvalid() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(1L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(1L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(1L)).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, null, "abc", "x-y", " , ");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(1, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
        List<String> hitSources = (List<String>) data.get("hit_sources");
        assertEquals(0, hitSources.size());
        assertEquals(null, data.get("truncated_source"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        assertEquals(2, sourceStats.size());
        Map<String, Object> queryCamerasStats = (Map<String, Object>) sourceStats.get("query_cameras");
        assertEquals(1, ((Number) queryCamerasStats.get("input_token_count")).intValue());
        assertEquals(1, ((Number) queryCamerasStats.get("invalid_token_count")).intValue());
        assertEquals(0, ((Number) queryCamerasStats.get("unique_added_count")).intValue());
        Map<String, Object> queryCameraRangeStats = (Map<String, Object>) sourceStats.get("query_camera_range");
        assertEquals(1, ((Number) queryCameraRangeStats.get("input_token_count")).intValue());
        assertEquals(1, ((Number) queryCameraRangeStats.get("invalid_token_count")).intValue());
        assertEquals(0, ((Number) queryCameraRangeStats.get("unique_added_count")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldApplyMaxLimitAcrossMixedQueryAliases() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, null, "1-300", "301-600", null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(500, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(500L, ((Number) routes.get(499).get("camera_id")).longValue());
        assertEquals(true, data.get("truncated"));
        assertEquals(500, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(500, ((Number) data.get("max_camera_ids_cap")).intValue());
        List<String> hitSources = (List<String>) data.get("hit_sources");
        assertEquals(2, hitSources.size());
        assertEquals("query_cameras", hitSources.get(0));
        assertEquals("query_camera_range", hitSources.get(1));
        assertEquals("query_camera_range", data.get("truncated_source"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        Map<String, Object> queryRangeStats = (Map<String, Object>) sourceStats.get("query_camera_range");
        assertEquals(true, queryRangeStats.get("truncated"));
        assertEquals(1, ((Number) queryRangeStats.get("input_token_count")).intValue());
        assertEquals(200, ((Number) queryRangeStats.get("expanded_candidate_count")).intValue());
        assertEquals(200, ((Number) queryRangeStats.get("unique_added_count")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldExposeParseStatsForMixedTokens() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "100-102,101,bad, ,102,103-101");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(4, routes.size());
        assertEquals(100L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(101L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(102L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(103L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(4, ((Number) data.get("resolved_camera_count")).intValue());
        assertEquals(5, ((Number) data.get("input_token_count")).intValue());
        assertEquals(8, ((Number) data.get("expanded_candidate_count")).intValue());
        assertEquals(1, ((Number) data.get("invalid_token_count")).intValue());
        assertEquals(4, ((Number) data.get("duplicate_filtered_count")).intValue());
        assertEquals(false, data.get("truncated"));
        List<String> hitSources = (List<String>) data.get("hit_sources");
        assertEquals(1, hitSources.size());
        assertEquals("query_camera_ids", hitSources.get(0));
        assertEquals(null, data.get("truncated_source"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        Map<String, Object> queryCameraIdsStats = (Map<String, Object>) sourceStats.get("query_camera_ids");
        assertEquals(5, ((Number) queryCameraIdsStats.get("input_token_count")).intValue());
        assertEquals(8, ((Number) queryCameraIdsStats.get("expanded_candidate_count")).intValue());
        assertEquals(1, ((Number) queryCameraIdsStats.get("invalid_token_count")).intValue());
        assertEquals(4, ((Number) queryCameraIdsStats.get("duplicate_filtered_count")).intValue());
        assertEquals(4, ((Number) queryCameraIdsStats.get("unique_added_count")).intValue());
        assertEquals(false, queryCameraIdsStats.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldExposeZeroParseStatsWhenFallingBackToDefaultCamera() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(1L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(1L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(1L)).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, ",,");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(1, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(1, ((Number) data.get("resolved_camera_count")).intValue());
        assertEquals(0, ((Number) data.get("input_token_count")).intValue());
        assertEquals(0, ((Number) data.get("expanded_candidate_count")).intValue());
        assertEquals(0, ((Number) data.get("invalid_token_count")).intValue());
        assertEquals(0, ((Number) data.get("duplicate_filtered_count")).intValue());
        assertEquals(true, data.get("default_fallback_used"));
        assertEquals(false, data.get("truncated"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        assertEquals(0, sourceStats.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldRespectLowerMaxCameraIdsFromQuery() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "1-20", null, null, null, 5);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(5, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(5L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(true, data.get("truncated"));
        assertEquals("query_camera_ids", data.get("truncated_source"));
        assertEquals(5, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(500, ((Number) data.get("max_camera_ids_cap")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldClampBodyMaxCameraIdsToCap() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("camera_ids", "1-999");
        body.put("max_camera_ids", 1000);

        JsonResult result = inferenceApiController.routeBatch(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(500, routes.size());
        assertEquals(500, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(500, ((Number) data.get("max_camera_ids_cap")).intValue());
    }
}
