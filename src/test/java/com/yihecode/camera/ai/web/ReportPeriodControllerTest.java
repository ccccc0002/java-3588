package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.ReportPeriod;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraAlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.ReportPeriodService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportPeriodControllerTest {

    @Mock
    private CameraService cameraService;

    @Mock
    private AlgorithmService algorithmService;

    @Mock
    private ReportPeriodService reportPeriodService;

    @Mock
    private CameraAlgorithmService cameraAlgorithmService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private ReportPeriodController reportPeriodController;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canWriteSystem(any())).thenReturn(true);
    }

    @Test
    void saveShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);
        ReportPeriod reportPeriod = new ReportPeriod();
        reportPeriod.setCameraId(1L);
        reportPeriod.setAlgorithmId(2L);
        reportPeriod.setStartText("09:00");
        reportPeriod.setEndText("10:00");

        JsonResult result = reportPeriodController.save(reportPeriod);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(reportPeriodService, never()).saveOrUpdate(any());
    }

    @Test
    void deleteShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = reportPeriodController.delete(10L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(reportPeriodService, never()).removeById(10L);
    }
}
