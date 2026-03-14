package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.ReportPushTargetService;
import com.yihecode.camera.ai.service.ReportService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.service.WareHouseService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.PageResult;
import com.yihecode.camera.ai.web.api.ReportPushService;
import com.yihecode.camera.ai.websocket.ReportWebsocket;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @Mock
    private CameraService cameraService;

    @Mock
    private AlgorithmService algorithmService;

    @Mock
    private ConfigService configService;

    @Mock
    private ReportPushService reportPushService;

    @Mock
    private ReportPushTargetService reportPushTargetService;

    @Mock
    private WareHouseService wareHouseService;

    @Mock
    private ReportWebsocket reportWebsocket;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private ReportController reportController;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canManagePushTargets(any())).thenReturn(true);
        lenient().when(roleAccessService.canWriteSystem(any())).thenReturn(true);
    }

    @Test
    void pushTargetsPageShouldReturnTemplate() {
        assertEquals("report/push_targets", reportController.pushTargetsPage());
    }

    @Test
    void getImageAsByteArray_shouldResolveRelativeReportImageFromUploadDir() throws Exception {
        Path imagePath = tempDir.resolve("relative-preview.jpg");
        Files.write(imagePath, "preview-image".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(reportController, "uploadDir", tempDir.toFile().getAbsolutePath() + File.separator);

        Report report = new Report();
        report.setId(7L);
        report.setFileName("relative-preview.jpg");
        when(reportService.getById(7L)).thenReturn(report);

        MockHttpServletResponse response = new MockHttpServletResponse();
        reportController.getImageAsByteArray(7L, response);

        assertEquals("image/jpeg", response.getContentType());
        assertArrayEquals("preview-image".getBytes(StandardCharsets.UTF_8), response.getContentAsByteArray());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listPage_shouldForwardTimeFilterAndDecorateNames() {
        Report report = new Report();
        report.setId(99L);
        report.setCameraId(10L);
        report.setAlgorithmId(20L);
        report.setType(0);
        report.setCreatedAt(new Date(1710123000000L));

        Page<Report> page = new Page<>(1, 10);
        page.setRecords(List.of(report));
        page.setTotal(1L);

        when(reportService.listByPage(any(Page.class), eq(10L), eq(20L), eq(0), any(), any())).thenReturn(page);

        Camera camera = new Camera();
        camera.setId(10L);
        camera.setName("Camera-A");
        when(cameraService.list()).thenReturn(List.of(camera));

        Algorithm algorithm = new Algorithm();
        algorithm.setId(20L);
        algorithm.setName("Algo-X");
        when(algorithmService.list()).thenReturn(List.of(algorithm));

        PageResult result = reportController.listPage(1, 10, 10L, 20L, 0, "2026-03-10T00:00", "2026-03-10T23:59");

        assertEquals(0, result.getCode());
        assertEquals(1L, result.getCount());
        List<Report> data = result.getData();
        assertEquals(1, data.size());
        assertEquals("Camera-A", data.get(0).getCameraName());
        assertEquals("Algo-X", data.get(0).getAlgorithmName());
        assertNotNull(data.get(0).getCreatedStr());

        ArgumentCaptor<Long> startCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> endCaptor = ArgumentCaptor.forClass(Long.class);
        verify(reportService).listByPage(any(Page.class), eq(10L), eq(20L), eq(0), startCaptor.capture(), endCaptor.capture());
        assertTrue(startCaptor.getValue() > 0);
        assertTrue(endCaptor.getValue() > startCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pushData_shouldPushAllTargetsAndReturnSummary() {
        Report report = new Report();
        report.setId(1L);
        report.setCameraId(10L);
        report.setAlgorithmId(20L);
        report.setFileName("alarm.jpg");
        report.setParams("[{\"type\":\"person\"}]");
        when(reportService.getById(1L)).thenReturn(report);

        Camera camera = new Camera();
        camera.setId(10L);
        camera.setName("cam-1");
        when(cameraService.getById(10L)).thenReturn(camera);

        Algorithm algorithm = new Algorithm();
        algorithm.setId(20L);
        algorithm.setName("algo-1");
        when(algorithmService.getById(20L)).thenReturn(algorithm);

        Map<String, Object> t1 = new HashMap<>();
        t1.put("id", "t1");
        t1.put("name", "Target-1");
        t1.put("url", "http://localhost:9001/push");
        t1.put("include_image", true);
        t1.put("bearer_token", "token-1");
        Map<String, Object> t2 = new HashMap<>();
        t2.put("id", "t2");
        t2.put("name", "Target-2");
        t2.put("url", "http://localhost:9002/push");
        t2.put("include_image", false);
        t2.put("bearer_token", "");
        when(reportPushTargetService.listEnabledTargets()).thenReturn(List.of(t1, t2));
        when(configService.getByValTag("webUrl")).thenReturn("http://127.0.0.1:8080");

        Map<String, Object> ok = new HashMap<>();
        ok.put("success", true);
        ok.put("status", 200);
        Map<String, Object> bad = new HashMap<>();
        bad.put("success", false);
        bad.put("status", 500);

        when(reportPushService.requestSync(eq("http://localhost:9001/push"), any(), eq(true), eq("alarm.jpg"), eq("token-1")))
                .thenReturn(ok);
        when(reportPushService.requestSync(eq("http://localhost:9002/push"), any(), eq(false), eq("alarm.jpg"), eq(null)))
                .thenReturn(bad);

        JsonResult result = reportController.pushData(1L);

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg() != null && !result.getMsg().isEmpty());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(2, data.get("target_count"));
        assertEquals(1, data.get("success_count"));
        assertEquals(1, data.get("failed_count"));
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
        assertEquals(2, results.size());

        ArgumentCaptor<com.alibaba.fastjson.JSONObject> payloadCaptor = ArgumentCaptor.forClass(com.alibaba.fastjson.JSONObject.class);
        verify(reportPushService).requestSync(eq("http://localhost:9001/push"), payloadCaptor.capture(), eq(true), eq("alarm.jpg"), eq("token-1"));
        assertEquals("cam-1", payloadCaptor.getValue().getString("camera_name"));
        assertEquals("algo-1", payloadCaptor.getValue().getString("algorithm_name"));
    }

    @Test
    void pushData_shouldFailWhenNoEnabledTargets() {
        when(reportPushTargetService.listEnabledTargets()).thenReturn(new ArrayList<>());

        JsonResult result = reportController.pushData(1L);

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg() != null && !result.getMsg().isEmpty());
    }

    @Test
    void batchRemoveShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = reportController.batchRemove("1,2");

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(reportService, never()).updateDisplay(any(), any());
    }

    @Test
    void doAuditShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = reportController.doAudit(1L, 1);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(reportService, never()).updateAudit(any(), any());
    }

    @Test
    void pushData_shouldFailWhenPermissionDenied() {
        when(roleAccessService.canManagePushTargets(any())).thenReturn(false);

        JsonResult result = reportController.pushData(1L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listPushTargets_shouldReturnConfiguredTargets() {
        when(reportPushTargetService.listAllTargets()).thenReturn(List.of(Map.of("id", "t1", "url", "http://x")));

        JsonResult result = reportController.listPushTargets();

        assertEquals(0, result.getCode());
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.getData();
        assertEquals(1, data.size());
        assertEquals("t1", data.get(0).get("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void savePushTarget_shouldSupportSnakeCaseParams() {
        when(reportPushTargetService.saveTarget(eq("a1"), eq("name"), eq("http://push"), eq("tk"), eq(true), eq(false)))
                .thenReturn(List.of(Map.of("id", "a1", "enabled", true)));

        JsonResult result = reportController.savePushTarget(
                "a1",
                "name",
                "http://push",
                null,
                "tk",
                true,
                null,
                false,
                null,
                null,
                null,
                null
        );

        assertEquals(0, result.getCode());
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.getData();
        assertEquals(1, data.size());
        verify(reportPushTargetService, times(1)).saveTarget(eq("a1"), eq("name"), eq("http://push"), eq("tk"), eq(true), eq(false));
    }

    @Test
    void savePushTarget_shouldFailWhenPermissionDenied() {
        when(roleAccessService.canManagePushTargets(any())).thenReturn(false);

        JsonResult result = reportController.savePushTarget(
                "a1",
                "name",
                "http://push",
                null,
                "tk",
                true,
                null,
                false,
                null,
                null,
                null,
                null
        );

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
    }

    @Test
    @SuppressWarnings("unchecked")
    void savePushTarget_shouldSupportAuthFileAndRetryCount() {
        when(reportPushTargetService.saveTarget(eq("a2"), eq("name2"), eq("http://push2"), eq("tk2"), eq(true), eq(true), eq("/opt/auth/token.txt"), eq(3)))
                .thenReturn(List.of(Map.of("id", "a2", "retry_count", 3)));

        JsonResult result = reportController.savePushTarget(
                "a2",
                "name2",
                "http://push2",
                "tk2",
                null,
                true,
                true,
                null,
                "/opt/auth/token.txt",
                null,
                3,
                null
        );

        assertEquals(0, result.getCode());
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.getData();
        assertEquals(1, data.size());
        verify(reportPushTargetService, times(1)).saveTarget(eq("a2"), eq("name2"), eq("http://push2"), eq("tk2"), eq(true), eq(true), eq("/opt/auth/token.txt"), eq(3));
    }

    @Test
    void pushData_shouldUseRetryAndAuthFileWhenConfigured() {
        Report report = new Report();
        report.setId(9L);
        report.setCameraId(10L);
        report.setAlgorithmId(20L);
        report.setFileName("alarm.jpg");
        report.setParams("[]");
        when(reportService.getById(9L)).thenReturn(report);

        Camera camera = new Camera();
        camera.setId(10L);
        camera.setName("cam-1");
        when(cameraService.getById(10L)).thenReturn(camera);

        Algorithm algorithm = new Algorithm();
        algorithm.setId(20L);
        algorithm.setName("algo-1");
        when(algorithmService.getById(20L)).thenReturn(algorithm);

        Map<String, Object> target = new HashMap<>();
        target.put("id", "rt1");
        target.put("name", "Retry-Target");
        target.put("url", "http://localhost:9010/push");
        target.put("include_image", false);
        target.put("bearer_token", "");
        target.put("auth_file", "/opt/auth/push.token");
        target.put("retry_count", 3);
        when(reportPushTargetService.listEnabledTargets()).thenReturn(List.of(target));
        when(configService.getByValTag("webUrl")).thenReturn("http://127.0.0.1:8080");

        Map<String, Object> ok = new HashMap<>();
        ok.put("success", true);
        ok.put("status", 200);
        when(reportPushService.requestSyncWithRetry(eq("http://localhost:9010/push"), any(), eq(false), eq("alarm.jpg"), eq(null), eq("/opt/auth/push.token"), eq(3)))
                .thenReturn(ok);

        JsonResult result = reportController.pushData(9L);

        assertEquals(0, result.getCode());
        verify(reportPushService, times(1))
                .requestSyncWithRetry(eq("http://localhost:9010/push"), any(), eq(false), eq("alarm.jpg"), eq(null), eq("/opt/auth/push.token"), eq(3));
        verify(reportPushService, never()).requestSync(eq("http://localhost:9010/push"), any(), anyBoolean(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletePushTarget_shouldReturnRemainingTargets() {
        when(reportPushTargetService.deleteTarget("t1")).thenReturn(List.of(Map.of("id", "t2")));

        JsonResult result = reportController.deletePushTarget("t1");

        assertEquals(0, result.getCode());
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.getData();
        assertEquals(1, data.size());
        assertEquals("t2", data.get(0).get("id"));
    }
}
