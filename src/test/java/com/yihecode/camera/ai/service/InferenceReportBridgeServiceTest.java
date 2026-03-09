package com.yihecode.camera.ai.service;

import com.alibaba.fastjson.JSON;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.vo.InferenceResult;
import com.yihecode.camera.ai.vo.Message;
import com.yihecode.camera.ai.vo.ReportMessage;
import com.yihecode.camera.ai.websocket.MessageWebsocket;
import com.yihecode.camera.ai.websocket.ReportWebsocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceReportBridgeServiceTest {

    @Mock
    private CameraService cameraService;

    @Mock
    private AlgorithmService algorithmService;

    @Mock
    private ReportService reportService;

    @Mock
    private WareHouseService wareHouseService;

    @Mock
    private ConfigService configService;

    @Mock
    private ReportWebsocket reportWebsocket;

    @Mock
    private MessageWebsocket messageWebsocket;

    @InjectMocks
    private InferenceReportBridgeService inferenceReportBridgeService;

    @Test
    void persistAndBroadcast_shouldSkipWhenAlertsExplicitlyEmpty() {
        InferenceResult result = new InferenceResult();
        result.setDetections(List.of(mapOf("label", "person")));
        result.setAlerts(Collections.emptyList());

        Map<String, Object> response = inferenceReportBridgeService.persistAndBroadcast(1L, 2L, result, "trace-empty-alerts");

        assertEquals("skipped", response.get("status"));
        assertEquals("empty alerts", response.get("reason"));
        verify(reportService, never()).save(any());
        verify(reportWebsocket, never()).sendToAll(any());
        verify(messageWebsocket, never()).sendToAll(any());
    }

    @Test
    void persistAndBroadcast_shouldPersistAlertPayloadInsteadOfAllDetections() throws Exception {
        Camera camera = new Camera();
        camera.setId(11L);
        camera.setName("Camera-A");
        camera.setIntervalTime(0F);
        camera.setWareHouseId(0L);
        Algorithm algorithm = new Algorithm();
        algorithm.setId(22L);
        algorithm.setName("YoloV8n");
        when(cameraService.getById(11L)).thenReturn(camera);
        when(algorithmService.getById(22L)).thenReturn(algorithm);
        when(configService.getByValTag("webUrl")).thenReturn("http://demo-web");
        doAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(1001L);
            return true;
        }).when(reportService).save(any(Report.class));

        InferenceResult result = new InferenceResult();
        result.setTraceId("trace-alert-persist");
        result.setCameraId(11L);
        result.setDetections(List.of(
                mapOf("label", "person", "label_zh", "人员"),
                mapOf("label", "bus", "label_zh", "公交车")
        ));
        result.setAlerts(List.of(
                mapOf("label", "bus", "label_zh", "公交车", "score", 0.91)
        ));
        result.setEvents(List.of(
                mapOf("event_type", "vision.alert", "label", "bus", "label_zh", "公交车")
        ));

        Map<String, Object> response = inferenceReportBridgeService.persistAndBroadcast(11L, 22L, result, "trace-alert-persist");

        assertEquals("ok", response.get("status"));
        assertEquals(Boolean.TRUE, response.get("persisted"));
        assertEquals(1, ((Number) response.get("alert_count")).intValue());
        assertEquals(1, ((Number) response.get("event_count")).intValue());

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportService).save(reportCaptor.capture());
        List<?> persistedParams = JSON.parseArray(reportCaptor.getValue().getParams());
        assertEquals(1, persistedParams.size());
        assertTrue(reportCaptor.getValue().getParams().contains("bus"));
        assertFalse(reportCaptor.getValue().getParams().contains("person"));

        ArgumentCaptor<String> reportSocketCaptor = ArgumentCaptor.forClass(String.class);
        verify(reportWebsocket).sendToAll(reportSocketCaptor.capture());
        ReportMessage reportMessage = JSON.parseObject(reportSocketCaptor.getValue(), ReportMessage.class);
        assertTrue(reportMessage.getParams().contains("bus"));
        assertFalse(reportMessage.getParams().contains("person"));
        assertEquals("trace-alert-persist", reportMessage.getTraceId());
        assertEquals(1, reportMessage.getAlertCount());
        assertFalse(reportMessage.getAlertLabelsZh().isEmpty());
        assertFalse(String.valueOf(reportMessage.getAlertLabelsZh().get(0)).trim().isEmpty());

        ArgumentCaptor<String> messageSocketCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageWebsocket).sendToAll(messageSocketCaptor.capture());
        Message message = JSON.parseObject(messageSocketCaptor.getValue(), Message.class);
        assertTrue(message.getContent().contains("YoloV8n"));
        Map<?, ?> data = JSON.parseObject(JSON.toJSONString(message.getData()), Map.class);
        assertEquals(1, ((Number) data.get("alertCount")).intValue());
        assertEquals("公交车", ((List<?>) data.get("alertLabelsZh")).get(0));
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            data.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return data;
    }
}
