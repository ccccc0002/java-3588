package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.MediaStreamUrlService;
import com.yihecode.camera.ai.service.ReportService;
import com.yihecode.camera.ai.service.VideoPlayService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamControllerTest {

    @Mock private CameraService cameraService;
    @Mock private ConfigService configService;
    @Mock private AlgorithmService algorithmService;
    @Mock private ReportService reportService;
    @Mock private VideoPlayService videoPlayService;
    @Mock private MediaStreamUrlService mediaStreamUrlService;

    @InjectMocks
    private StreamController streamController;

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
}