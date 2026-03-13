package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ReportService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticControllerTest {

    @Mock
    private CameraService cameraService;

    @Mock
    private AlgorithmService algorithmService;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private StatisticController statisticController;

    @Test
    @SuppressWarnings("unchecked")
    void listAlgorithmRatio_shouldSupportCombinedFilters() {
        Map<Long, String> algorithmMap = new HashMap<>();
        algorithmMap.put(9L, "helmet");
        when(algorithmService.toMap()).thenReturn(algorithmMap);
        when(reportService.getCount(any(), any(), eq(2L), eq(9L), eq(0))).thenReturn(6);

        JsonResult result = statisticController.listAlgorithmRatio("2026-03-01", "2026-03-07", 2L, 9L, 0);

        assertEquals(0, result.getCode());
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.getData();
        assertEquals(1, data.size());
        assertEquals("helmet", data.get(0).get("name"));
        assertEquals(6, data.get(0).get("value"));

        ArgumentCaptor<Long> startCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> endCaptor = ArgumentCaptor.forClass(Long.class);
        verify(reportService).getCount(startCaptor.capture(), endCaptor.capture(), eq(2L), eq(9L), eq(0));
        assertTrue(startCaptor.getValue() > 0);
        assertTrue(endCaptor.getValue() > startCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCamera_shouldSupportCombinedFilters() {
        Map<Long, String> cameraMap = new HashMap<>();
        cameraMap.put(11L, "cam-11");
        when(cameraService.toMap()).thenReturn(cameraMap);
        when(reportService.getCount(any(), any(), eq(11L), eq(7L), eq(1))).thenReturn(3);

        JsonResult result = statisticController.listCamera("2026-03-01", "2026-03-07", 11L, 7L, 1);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertNotNull(data.get("xAxiss"));
        assertNotNull(data.get("values"));
        List<Object> names = (List<Object>) data.get("xAxiss");
        List<Object> values = (List<Object>) data.get("values");
        assertEquals(1, names.size());
        assertEquals("cam-11", names.get(0));
        assertEquals(3, values.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void alarmTrend_shouldReturnDailyCountsForCombinedFilters() {
        when(reportService.getCount(any(), any(), eq(11L), eq(7L), eq(1))).thenReturn(2, 3, 4);

        JsonResult result = statisticController.alarmTrend("2026-03-01", "2026-03-03", 11L, 7L, 1);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Object> xAxiss = (List<Object>) data.get("xAxiss");
        List<Object> values = (List<Object>) data.get("values");
        assertEquals(3, xAxiss.size());
        assertEquals(3, values.size());
        assertEquals(2, values.get(0));
        assertEquals(3, values.get(1));
        assertEquals(4, values.get(2));
        verify(reportService, atLeast(3)).getCount(any(), any(), eq(11L), eq(7L), eq(1));
    }
}
