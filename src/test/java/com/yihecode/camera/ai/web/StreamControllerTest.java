package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.MediaStreamUrlService;
import com.yihecode.camera.ai.service.ModelService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.ReportService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.service.RuntimeAccessTokenService;
import com.yihecode.camera.ai.service.RuntimeApiService;
import com.yihecode.camera.ai.service.VideoPlayService;
import com.yihecode.camera.ai.entity.Model;
import org.junit.jupiter.api.BeforeEach;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamControllerTest {

    @Mock private CameraService cameraService;
    @Mock private ConfigService configService;
    @Mock private AlgorithmService algorithmService;
    @Mock private ReportService reportService;
    @Mock private VideoPlayService videoPlayService;
    @Mock private MediaStreamUrlService mediaStreamUrlService;
    @Mock private ModelService modelService;
    @Mock private RoleAccessService roleAccessService;
    @Mock private RuntimeAccessTokenService runtimeAccessTokenService;
    @Mock private OperationLogService operationLogService;
    @Mock private RuntimeApiService runtimeApiService;

    @InjectMocks
    private StreamController streamController;

    @BeforeEach
    void setupPermissionDefaults() {
        lenient().when(roleAccessService.canManageStream(any())).thenReturn(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staticsCounterShouldReturnTodayCounterPayload() {
        when(reportService.getCounter(anyLong(), anyLong())).thenReturn(7);

        JsonResult result = streamController.staticsCounter();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data.get("today"));
        assertEquals(7, ((Number) data.get("counter")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardSummaryShouldReturnOverviewAndChartPayload() {
        when(cameraService.getCountByRunState(-1)).thenReturn(12);
        when(cameraService.getCountByRunState(1)).thenReturn(9);

        Map<Long, String> algorithmMap = new HashMap<>();
        algorithmMap.put(1L, "person");
        algorithmMap.put(2L, "helmet");
        when(algorithmService.toMap()).thenReturn(algorithmMap);

        List<Model> models = new ArrayList<>();
        models.add(new Model());
        models.add(new Model());
        when(modelService.listData()).thenReturn(models);

        when(reportService.getCounter(anyLong(), anyLong())).thenReturn(5);

        List<Map<String, Object>> ratioRows = new ArrayList<>();
        Map<String, Object> ratio = new HashMap<>();
        ratio.put("algorithm_id", 1L);
        ratio.put("cnt", 4);
        ratioRows.add(ratio);
        when(reportService.findAlgorithmRatio(any(), any())).thenReturn(ratioRows);

        Map<Long, String> cameraMap = new HashMap<>();
        cameraMap.put(10L, "cam-a");
        when(cameraService.toMap()).thenReturn(cameraMap);

        List<Map<String, Object>> cameraRows = new ArrayList<>();
        Map<String, Object> camera = new HashMap<>();
        camera.put("camera_id", 10L);
        camera.put("cnt", 6);
        cameraRows.add(camera);
        when(reportService.findCamera(any(), any())).thenReturn(cameraRows);
        Map<String, Object> scheduler = new HashMap<>();
        scheduler.put("concurrency_level", 4);
        scheduler.put("concurrency_pressure", 1.6D);
        scheduler.put("max_effective_cooldown_ms", 2400);
        Map<String, Object> throttleHint = new HashMap<>();
        throttleHint.put("recommended_frame_stride", 2);
        throttleHint.put("suggested_min_dispatch_ms", 2400);
        throttleHint.put("concurrency_pressure", 1.6D);
        throttleHint.put("strategy_source", "scheduler_feedback");
        Map<String, Object> runtimeSnapshot = new HashMap<>();
        runtimeSnapshot.put("scheduler", scheduler);
        runtimeSnapshot.put("throttle_hint", throttleHint);
        when(runtimeApiService.buildRuntimeSnapshot()).thenReturn(runtimeSnapshot);

        JsonResult result = streamController.dashboardSummary();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data);

        Map<String, Object> overview = (Map<String, Object>) data.get("overview");
        assertEquals(5, ((Number) overview.get("todayAlerts")).intValue());
        assertEquals(9, ((Number) overview.get("onlineCameras")).intValue());
        assertEquals(12, ((Number) overview.get("totalCameras")).intValue());
        assertEquals(2, ((Number) overview.get("algorithmCount")).intValue());
        assertEquals(2, ((Number) overview.get("modelCount")).intValue());

        Map<String, Object> trend = (Map<String, Object>) data.get("trend");
        List<Object> trendLabels = (List<Object>) trend.get("labels");
        List<Object> trendValues = (List<Object>) trend.get("values");
        assertEquals(7, trendLabels.size());
        assertEquals(7, trendValues.size());

        List<Map<String, Object>> pie = (List<Map<String, Object>>) data.get("pie");
        assertEquals(1, pie.size());
        assertEquals("person", pie.get(0).get("name"));
        assertEquals(4, ((Number) pie.get(0).get("value")).intValue());

        Map<String, Object> ranking = (Map<String, Object>) data.get("ranking");
        List<Object> rankingLabels = (List<Object>) ranking.get("labels");
        List<Object> rankingValues = (List<Object>) ranking.get("values");
        assertTrue(rankingLabels.contains("cam-a"));
        assertTrue(rankingValues.contains(6));

        Map<String, Object> schedulerPayload = (Map<String, Object>) data.get("scheduler");
        assertEquals(4, ((Number) schedulerPayload.get("concurrency_level")).intValue());
        assertEquals(2400, ((Number) schedulerPayload.get("max_effective_cooldown_ms")).intValue());

        Map<String, Object> throttlePayload = (Map<String, Object>) data.get("throttle_hint");
        assertEquals(2, ((Number) throttlePayload.get("recommended_frame_stride")).intValue());
        assertEquals(2400, ((Number) throttlePayload.get("suggested_min_dispatch_ms")).intValue());
        assertEquals("scheduler_feedback", throttlePayload.get("strategy_source"));
        assertEquals("ok", data.get("telemetry_status"));
        assertEquals("", data.get("telemetry_error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardSummaryShouldFallbackWhenRuntimeSnapshotFails() {
        when(cameraService.getCountByRunState(-1)).thenReturn(1);
        when(cameraService.getCountByRunState(1)).thenReturn(1);
        when(algorithmService.toMap()).thenReturn(new HashMap<>());
        when(modelService.listData()).thenReturn(new ArrayList<>());
        when(reportService.getCounter(anyLong(), anyLong())).thenReturn(0);
        when(reportService.findAlgorithmRatio(any(), any())).thenReturn(new ArrayList<>());
        when(cameraService.toMap()).thenReturn(new HashMap<>());
        when(reportService.findCamera(any(), any())).thenReturn(new ArrayList<>());
        doThrow(new RuntimeException("runtime unavailable")).when(runtimeApiService).buildRuntimeSnapshot();

        JsonResult result = streamController.dashboardSummary();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data);
        assertTrue(data.get("scheduler") instanceof Map);
        assertTrue(data.get("throttle_hint") instanceof Map);
        assertTrue(((Map<String, Object>) data.get("scheduler")).isEmpty());
        assertTrue(((Map<String, Object>) data.get("throttle_hint")).isEmpty());
        assertEquals("degraded", data.get("telemetry_status"));
        assertEquals("runtime_snapshot_failed", data.get("telemetry_error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardSummaryShouldUseTelemetryStatusFromRuntimeSnapshot() {
        when(cameraService.getCountByRunState(-1)).thenReturn(1);
        when(cameraService.getCountByRunState(1)).thenReturn(1);
        when(algorithmService.toMap()).thenReturn(new HashMap<>());
        when(modelService.listData()).thenReturn(new ArrayList<>());
        when(reportService.getCounter(anyLong(), anyLong())).thenReturn(0);
        when(reportService.findAlgorithmRatio(any(), any())).thenReturn(new ArrayList<>());
        when(cameraService.toMap()).thenReturn(new HashMap<>());
        when(reportService.findCamera(any(), any())).thenReturn(new ArrayList<>());
        when(runtimeApiService.buildRuntimeSnapshot()).thenReturn(Map.of(
                "scheduler", Collections.emptyMap(),
                "throttle_hint", Collections.emptyMap(),
                "telemetry_status", "degraded",
                "telemetry_error", "scheduler_summary_failed"
        ));

        JsonResult result = streamController.dashboardSummary();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data);
        assertEquals("degraded", data.get("telemetry_status"));
        assertEquals("scheduler_summary_failed", data.get("telemetry_error"));
    }

    @Test
    void formConfigShouldDenyWhenNoManagePermission() {
        when(roleAccessService.canManageStream(any())).thenReturn(false);

        JsonResult result = streamController.formConfig("[]");

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(operationLogService).record(eq("stream:form_config"), eq("stream"), eq(false), eq("permission denied"), eq(""));
    }

    @Test
    void stopStreamShouldDenyWhenNoManagePermission() {
        JsonResult result = streamController.stopStream(1L, null);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(operationLogService).record(eq("stream:stop"), eq("cameraId=1"), eq(false), eq("permission denied"), eq(""));
    }

    @Test
    void startStreamShouldDenyWhenNoManagePermission() {
        JsonResult result = streamController.startStream(2L, 8080, null);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(operationLogService).record(eq("stream:start"), eq("cameraId=2"), eq(false), eq("permission denied"), eq(""));
    }

    @Test
    void stopStreamShouldAllowWithBootstrapTokenWhenRoleDenied() {
        when(runtimeAccessTokenService.isBootstrapTokenValid("edge-demo-bootstrap")).thenReturn(true);
        when(mediaStreamUrlService.isZlmMode()).thenReturn(true);

        JsonResult result = streamController.stopStream(1L, "edge-demo-bootstrap");

        assertEquals(0, result.getCode());
        verify(cameraService).updatePlay(1L, 0);
    }

    @Test
    void startStreamShouldAllowWithBootstrapTokenWhenRoleDenied() {
        when(runtimeAccessTokenService.isBootstrapTokenValid("edge-demo-bootstrap")).thenReturn(true);
        when(mediaStreamUrlService.isZlmMode()).thenReturn(true);
        when(cameraService.updatePlay(2L, 1)).thenReturn(true);
        when(mediaStreamUrlService.buildPlayUrl(any(), any())).thenReturn("http://127.0.0.1/live/2.live.flv");

        JsonResult result = streamController.startStream(2L, null, "edge-demo-bootstrap");

        assertEquals(0, result.getCode());
    }
}
