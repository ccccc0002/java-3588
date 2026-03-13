package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraAlgorithmService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlgorithmControllerTest {

    @Mock
    private AlgorithmService algorithmService;

    @Mock
    private CameraAlgorithmService cameraAlgorithmService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private AlgorithmController algorithmController;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canWriteSystem(any())).thenReturn(true);
    }

    @Test
    void saveShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = algorithmController.save(new Algorithm());

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(algorithmService, never()).saveOrUpdate(any());
        verify(operationLogService).record(eq("algorithm:save"), eq("algorithmId=null"), eq(false), eq("permission denied"), eq(""));
    }

    @Test
    void deleteShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = algorithmController.delete(1L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(algorithmService, never()).removeById(anyLong());
        verify(operationLogService).record(eq("algorithm:delete"), eq("algorithmId=1"), eq(false), eq("permission denied"), eq(""));
    }
}
