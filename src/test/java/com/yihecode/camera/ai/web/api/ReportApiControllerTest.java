package com.yihecode.camera.ai.web.api;

import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.Report;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.ReportPeriodService;
import com.yihecode.camera.ai.service.ReportPushTargetService;
import com.yihecode.camera.ai.service.ReportService;
import com.yihecode.camera.ai.service.SmsPhoneService;
import com.yihecode.camera.ai.service.WareHouseService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.websocket.MessageWebsocket;
import com.yihecode.camera.ai.websocket.ReportWebsocket;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ReportApiControllerTest {

    @Mock
    private CameraService cameraService;

    @Mock
    private AlgorithmService algorithmService;

    @Mock
    private ReportPeriodService reportPeriodService;

    @Mock
    private ConfigService configService;

    @Mock
    private ReportPushService reportPushService;

    @Mock
    private ReportPushTargetService reportPushTargetService;

    @Mock
    private ReportService reportService;

    @Mock
    private MessageWebsocket websocket;

    @Mock
    private ReportWebsocket reportWebsocket;

    @Mock
    private WareHouseService wareHouseService;

    @Mock
    private SmsPhoneService smsPhoneService;

    @InjectMocks
    private ReportApiController reportApiController;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(configService.getByValTag(anyString())).thenReturn("");
    }

    @Test
    void report_shouldDispatchToAllEnabledHttpTargets() {
        Camera camera = new Camera();
        camera.setId(1L);
        camera.setName("cam-1");
        when(cameraService.getById(1L)).thenReturn(camera);

        Algorithm algorithm = new Algorithm();
        algorithm.setId(2L);
        algorithm.setName("algo-2");
        when(algorithmService.getById(2L)).thenReturn(algorithm);

        when(reportPeriodService.listData(1L, 2L)).thenReturn(new ArrayList<>());
        when(reportService.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(100L);
            return true;
        });
        when(configService.getByValTag("webUrl")).thenReturn("http://127.0.0.1:8080");

        Map<String, Object> t1 = new HashMap<>();
        t1.put("url", "http://localhost:9001/push");
        t1.put("include_image", true);
        t1.put("bearer_token", "token-a");
        Map<String, Object> t2 = new HashMap<>();
        t2.put("url", "http://localhost:9002/push");
        t2.put("include_image", false);
        t2.put("bearer_token", "");
        when(reportPushTargetService.listEnabledTargets()).thenReturn(List.of(t1, t2));

        JsonResult result = reportApiController.report(1L, 2L, "alarm.jpg", "[]", null);

        assertEquals(0, result.getCode());
        verify(reportPushService, times(1))
                .request(eq("http://localhost:9001/push"), any(), eq(true), eq("alarm.jpg"), eq("token-a"));
        verify(reportPushService, times(1))
                .request(eq("http://localhost:9002/push"), any(), eq(false), eq("alarm.jpg"), eq(null));
    }

    @Test
    void report_shouldSkipHttpPushWhenNoEnabledTarget() {
        Camera camera = new Camera();
        camera.setId(11L);
        camera.setName("cam-11");
        when(cameraService.getById(11L)).thenReturn(camera);

        Algorithm algorithm = new Algorithm();
        algorithm.setId(22L);
        algorithm.setName("algo-22");
        when(algorithmService.getById(22L)).thenReturn(algorithm);

        when(reportPeriodService.listData(11L, 22L)).thenReturn(new ArrayList<>());
        when(reportService.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(101L);
            return true;
        });
        when(reportPushTargetService.listEnabledTargets()).thenReturn(new ArrayList<>());

        JsonResult result = reportApiController.report(11L, 22L, "x.jpg", "[]", null);

        assertEquals(0, result.getCode());
        verify(reportPushService, never()).request(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void report_shouldDispatchVoicePushWhenEnabled() {
        Camera camera = new Camera();
        camera.setId(31L);
        camera.setName("cam-31");
        when(cameraService.getById(31L)).thenReturn(camera);

        Algorithm algorithm = new Algorithm();
        algorithm.setId(41L);
        algorithm.setName("algo-41");
        when(algorithmService.getById(41L)).thenReturn(algorithm);

        when(reportPeriodService.listData(31L, 41L)).thenReturn(new ArrayList<>());
        when(reportService.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(300L);
            return true;
        });
        when(reportPushTargetService.listEnabledTargets()).thenReturn(new ArrayList<>());
        when(configService.getByValTag("voice_push_enabled")).thenReturn("1");
        when(configService.getByValTag("voice_push_url")).thenReturn("http://localhost:9100/voice");
        when(configService.getByValTag("voice_push_provider")).thenReturn("mock-provider");
        when(configService.getByValTag("voice_push_numbers")).thenReturn("13800000000");
        when(configService.getByValTag("voice_push_bearer")).thenReturn("voice-token");

        JsonResult result = reportApiController.report(31L, 41L, "voice.jpg", "[]", null);

        assertEquals(0, result.getCode());
        verify(reportPushService, times(1))
                .request(eq("http://localhost:9100/voice"), any(), eq(false), eq("voice.jpg"), eq("voice-token"));
    }
}
