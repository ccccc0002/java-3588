package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Model;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraAlgorithmService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.ModelDependService;
import com.yihecode.camera.ai.service.ModelService;
import com.yihecode.camera.ai.service.ModelTestCaptureService;
import com.yihecode.camera.ai.service.ModelTestResultService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelControllerTest {

    @Mock
    private AlgorithmService algorithmService;

    @Mock
    private CameraAlgorithmService cameraAlgorithmService;

    @Mock
    private ModelService modelService;

    @Mock
    private ModelDependService modelDependService;

    @Mock
    private ConfigService configService;

    @Mock
    private ModelTestResultService modelTestResultService;

    @Mock
    private ModelTestCaptureService modelTestCaptureService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private ModelController modelController;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canWriteSystem(any())).thenReturn(true);
    }

    @Test
    void saveShouldFailWhenPermissionDenied() throws Exception {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = modelController.save(new Model());

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(modelService, never()).saveModel(any());
    }

    @Test
    void startShouldFailWhenPermissionDenied() throws Exception {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = modelController.startModel(10L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(modelService, never()).updateModelEnable(any());
    }

    @Test
    void deleteShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = modelController.delete(10L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(modelService, never()).removeById(anyLong());
    }

    @Test
    void mergeShouldFailWhenPermissionDenied() throws Exception {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = modelController.merge(1, "abc", "demo.onnx");

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
    }
}
