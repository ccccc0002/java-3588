package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.service.AlgorithmPackageLifecycleService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlgorithmPackageLifecycleControllerTest {

    @Mock
    private AlgorithmPackageLifecycleService algorithmPackageLifecycleService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private AlgorithmPackageLifecycleController algorithmPackageLifecycleController;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canWriteSystem(any())).thenReturn(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void importPackage_shouldReturnServiceDataWhenSuccess() throws Exception {
        when(algorithmPackageLifecycleService.importPackage(any()))
                .thenReturn(Map.of("plugin_id", "yolov8n", "algorithm_id", 9L));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "yolov8n.zip",
                "application/zip",
                "demo".getBytes(StandardCharsets.UTF_8)
        );

        JsonResult result = algorithmPackageLifecycleController.importPackage(file);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("yolov8n", data.get("plugin_id"));
        assertEquals(9L, data.get("algorithm_id"));
    }

    @Test
    void importPackage_shouldReturnFailureWhenServiceThrows() throws Exception {
        when(algorithmPackageLifecycleService.importPackage(any()))
                .thenThrow(new IllegalArgumentException("manifest validation failed"));

        JsonResult result = algorithmPackageLifecycleController.importPackage(null);

        assertEquals(500, result.getCode());
        assertEquals("manifest validation failed", result.getMsg());
    }

    @Test
    @SuppressWarnings("unchecked")
    void forceDelete_shouldReturnServiceDataWhenSuccess() {
        when(algorithmPackageLifecycleService.forceDelete(5L))
                .thenReturn(Map.of("algorithm_id", 5L, "unbind_count", 1, "removed", true));

        JsonResult result = algorithmPackageLifecycleController.forceDelete(5L);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(5L, data.get("algorithm_id"));
        assertEquals(1, data.get("unbind_count"));
        assertEquals(true, data.get("removed"));
    }

    @Test
    void forceDelete_shouldReturnFailureWhenServiceThrows() {
        when(algorithmPackageLifecycleService.forceDelete(eq(6L)))
                .thenThrow(new IllegalArgumentException("algorithm not found"));

        JsonResult result = algorithmPackageLifecycleController.forceDelete(6L);

        assertEquals(500, result.getCode());
        assertEquals("algorithm not found", result.getMsg());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateMetadata_shouldReturnServiceDataWhenSuccess() {
        when(algorithmPackageLifecycleService.updateMetadata(eq(9L), eq("name"), eq("desc"), eq("{\"person\":\"人员\"}")))
                .thenReturn(Map.of("algorithm_id", 9L, "algorithm_name", "name"));

        JsonResult result = algorithmPackageLifecycleController.updateMetadata(9L, "name", "desc", "{\"person\":\"人员\"}");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(9L, data.get("algorithm_id"));
        assertEquals("name", data.get("algorithm_name"));
    }

    @Test
    void updateMetadata_shouldReturnFailureWhenServiceThrows() {
        when(algorithmPackageLifecycleService.updateMetadata(eq(10L), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("label aliases must be a valid json object"));

        JsonResult result = algorithmPackageLifecycleController.updateMetadata(10L, "name", "desc", "[]");

        assertEquals(500, result.getCode());
        assertEquals("label aliases must be a valid json object", result.getMsg());
    }

    @Test
    void importPackage_shouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = algorithmPackageLifecycleController.importPackage(null);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
    }
}
