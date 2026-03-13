package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.CameraAlgorithm;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.web.api.InferenceApiController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveCameraInferenceSchedulerServiceTest {

    @Mock
    private CameraService cameraService;

    @Mock
    private CameraAlgorithmService cameraAlgorithmService;

    @Mock
    private AlgorithmService algorithmService;

    @Mock
    private ConfigService configService;

    @Mock
    private InferenceApiController inferenceApiController;

    @InjectMocks
    private ActiveCameraInferenceSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchActiveCameras_shouldDispatchBoundAlgorithmsUsingDefaultPluginId() {
        Camera camera = camera(11L, "rtsp://demo/stream1", 0F);
        CameraAlgorithm binding = binding(11L, 22L);
        Algorithm algorithm = algorithm(22L, null, 0);
        JsonResult dispatchResult = JsonResultUtils.success(Map.of("trace_id", "trace-1"));

        when(configService.getByValTag("infer_scheduler_enabled")).thenReturn("1");
        when(configService.getByValTag("infer_default_plugin_id")).thenReturn("yolov8n");
        when(cameraService.listActives()).thenReturn(List.of(camera));
        when(cameraAlgorithmService.listByCamera(11L)).thenReturn(List.of(binding));
        when(algorithmService.getById(22L)).thenReturn(algorithm);
        when(inferenceApiController.dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt())).thenReturn(dispatchResult);

        Map<String, Object> summary = schedulerService.dispatchActiveCameras();

        assertEquals(Boolean.TRUE, summary.get("enabled"));
        assertEquals(1, ((Number) summary.get("camera_count")).intValue());
        assertEquals(1, ((Number) summary.get("binding_count")).intValue());
        assertEquals(1, ((Number) summary.get("dispatch_count")).intValue());
        assertEquals(0, ((Number) summary.get("skip_count")).intValue());
        assertEquals(0, ((Number) summary.get("error_count")).intValue());

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(inferenceApiController).dispatch(bodyCaptor.capture(), isNull(), isNull(), isNull(), isNull(), anyInt());
        Map<String, Object> body = bodyCaptor.getValue();
        assertEquals(11L, ((Number) body.get("camera_id")).longValue());
        assertEquals(22L, ((Number) body.get("model_id")).longValue());
        assertEquals(22L, ((Number) body.get("algorithm_id")).longValue());
        assertEquals("yolov8n", body.get("plugin_id"));
        assertEquals(1, ((Number) body.get("persist_report")).intValue());
        Map<String, Object> frame = (Map<String, Object>) body.get("frame");
        assertEquals("rtsp://demo/stream1", frame.get("source"));
        assertNotNull(frame.get("timestamp_ms"));
    }

    @Test
    void dispatchActiveCameras_shouldHonorPluginIdFromAlgorithmParams() {
        Camera camera = camera(12L, "rtsp://demo/stream2", 0F);
        CameraAlgorithm binding = binding(12L, 23L);
        Algorithm algorithm = algorithm(23L, "{\"plugin_id\":\"helmet-detector\"}", 0);

        when(configService.getByValTag("infer_scheduler_enabled")).thenReturn("1");
        when(cameraService.listActives()).thenReturn(List.of(camera));
        when(cameraAlgorithmService.listByCamera(12L)).thenReturn(List.of(binding));
        when(algorithmService.getById(23L)).thenReturn(algorithm);
        when(inferenceApiController.dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(JsonResultUtils.success(Map.of("trace_id", "trace-2")));

        schedulerService.dispatchActiveCameras();

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(inferenceApiController).dispatch(bodyCaptor.capture(), isNull(), isNull(), isNull(), isNull(), anyInt());
        Map<String, Object> body = bodyCaptor.getValue();
        assertEquals("helmet-detector", body.get("plugin_id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchActiveCameras_shouldThrottleRepeatedDispatchesPerCameraAlgorithm() {
        Camera camera = camera(13L, "rtsp://demo/stream3", 60F);
        CameraAlgorithm binding = binding(13L, 24L);
        Algorithm algorithm = algorithm(24L, null, 0);

        when(configService.getByValTag("infer_scheduler_enabled")).thenReturn("1");
        when(configService.getByValTag("infer_default_plugin_id")).thenReturn("yolov8n");
        when(cameraService.listActives()).thenReturn(List.of(camera));
        when(cameraAlgorithmService.listByCamera(13L)).thenReturn(List.of(binding));
        when(algorithmService.getById(24L)).thenReturn(algorithm);
        when(inferenceApiController.dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(JsonResultUtils.success(Map.of("trace_id", "trace-3")));

        Map<String, Object> first = schedulerService.dispatchActiveCameras();
        Map<String, Object> second = schedulerService.dispatchActiveCameras();

        assertEquals(1, ((Number) first.get("dispatch_count")).intValue());
        assertEquals(0, ((Number) second.get("dispatch_count")).intValue());
        assertEquals(1, ((Number) second.get("skip_count")).intValue());
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) second.get("skipped");
        assertEquals("cooldown", skipped.get(0).get("reason"));
        assertEquals("base", skipped.get(0).get("cooldown_source"));
        assertEquals("camera_interval", skipped.get(0).get("base_cooldown_source"));
        assertEquals(60000L, ((Number) skipped.get(0).get("base_cooldown_ms")).longValue());
        assertEquals(0L, ((Number) skipped.get(0).get("latency_cooldown_ms")).longValue());
        assertEquals(60000L, ((Number) skipped.get(0).get("effective_cooldown_ms")).longValue());
        verify(inferenceApiController, times(1)).dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt());
    }

    @Test
    void dispatchActiveCameras_shouldSkipWhenDisabled() {
        when(configService.getByValTag("infer_scheduler_enabled")).thenReturn("0");

        Map<String, Object> summary = schedulerService.dispatchActiveCameras();

        assertEquals(Boolean.FALSE, summary.get("enabled"));
        assertEquals(0, ((Number) summary.get("dispatch_count")).intValue());
        verify(cameraService, never()).listActives();
        verify(inferenceApiController, never()).dispatch(any(), anyLong(), anyLong(), anyLong(), any(), anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchActiveCameras_shouldThrottleByDeclaredInferenceTimeWhenCooldownIsZero() {
        Camera camera = camera(21L, "rtsp://demo/stream21", 0F);
        CameraAlgorithm binding = binding(21L, 31L);
        Algorithm algorithm = algorithm(31L, "{\"plugin_id\":\"yolov8n\",\"inference_time_ms\":2000}", 0);

        when(configService.getByValTag("infer_scheduler_enabled")).thenReturn("1");
        when(configService.getByValTag("infer_scheduler_cooldown_ms")).thenReturn("0");
        when(configService.getByValTag("infer_scheduler_latency_factor")).thenReturn("2");
        when(cameraService.listActives()).thenReturn(List.of(camera));
        when(cameraAlgorithmService.listByCamera(21L)).thenReturn(List.of(binding));
        when(algorithmService.getById(31L)).thenReturn(algorithm);
        when(inferenceApiController.dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(JsonResultUtils.success(Map.of("trace_id", "trace-21")));

        Map<String, Object> first = schedulerService.dispatchActiveCameras();
        Map<String, Object> second = schedulerService.dispatchActiveCameras();

        assertEquals(1, ((Number) first.get("dispatch_count")).intValue());
        assertEquals(0, ((Number) second.get("dispatch_count")).intValue());
        assertEquals(1, ((Number) second.get("skip_count")).intValue());
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) second.get("skipped");
        assertEquals("cooldown", skipped.get(0).get("reason"));
        assertEquals("latency", skipped.get(0).get("cooldown_source"));
        assertEquals("config_default", skipped.get(0).get("base_cooldown_source"));
        assertEquals(0L, ((Number) skipped.get(0).get("base_cooldown_ms")).longValue());
        assertEquals(4000L, ((Number) skipped.get(0).get("latency_cooldown_ms")).longValue());
        assertEquals(4000L, ((Number) skipped.get(0).get("effective_cooldown_ms")).longValue());
        assertEquals(2000L, ((Number) skipped.get(0).get("declared_inference_ms")).longValue());
        assertEquals(0L, ((Number) skipped.get(0).get("observed_latency_ms")).longValue());
        assertEquals(4000L, ((Number) second.get("max_effective_cooldown_ms")).longValue());
        assertEquals(2000L, ((Number) second.get("max_declared_inference_ms")).longValue());
        verify(inferenceApiController, times(1)).dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchActiveCameras_shouldThrottleByObservedLatencyWhenCooldownIsZero() {
        Camera camera = camera(22L, "rtsp://demo/stream22", 0F);
        CameraAlgorithm binding = binding(22L, 32L);
        Algorithm algorithm = algorithm(32L, "{\"plugin_id\":\"yolov8n\"}", 0);

        when(configService.getByValTag("infer_scheduler_enabled")).thenReturn("1");
        when(configService.getByValTag("infer_scheduler_cooldown_ms")).thenReturn("0");
        when(configService.getByValTag("infer_scheduler_latency_factor")).thenReturn("1");
        when(cameraService.listActives()).thenReturn(List.of(camera));
        when(cameraAlgorithmService.listByCamera(22L)).thenReturn(List.of(binding));
        when(algorithmService.getById(32L)).thenReturn(algorithm);
        when(inferenceApiController.dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(JsonResultUtils.success(Map.of("trace_id", "trace-22", "result", Map.of("latency_ms", 3000))));

        Map<String, Object> first = schedulerService.dispatchActiveCameras();
        Map<String, Object> second = schedulerService.dispatchActiveCameras();

        assertEquals(1, ((Number) first.get("dispatch_count")).intValue());
        assertEquals(0, ((Number) second.get("dispatch_count")).intValue());
        assertEquals(1, ((Number) second.get("skip_count")).intValue());
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) second.get("skipped");
        assertEquals("cooldown", skipped.get(0).get("reason"));
        assertEquals("latency", skipped.get(0).get("cooldown_source"));
        assertEquals("config_default", skipped.get(0).get("base_cooldown_source"));
        assertEquals(0L, ((Number) skipped.get(0).get("base_cooldown_ms")).longValue());
        assertEquals(3000L, ((Number) skipped.get(0).get("latency_cooldown_ms")).longValue());
        assertEquals(3000L, ((Number) skipped.get(0).get("effective_cooldown_ms")).longValue());
        assertEquals(0L, ((Number) skipped.get(0).get("declared_inference_ms")).longValue());
        assertEquals(3000L, ((Number) skipped.get(0).get("observed_latency_ms")).longValue());
        assertEquals(1, ((Number) first.get("latency_update_count")).intValue());
        assertEquals(3000L, ((Number) second.get("max_observed_latency_ms")).longValue());
        assertEquals(3000L, ((Number) second.get("max_effective_cooldown_ms")).longValue());
        verify(inferenceApiController, times(1)).dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchActiveCameras_shouldScaleLatencyCooldownByConcurrencyPressure() {
        Camera camera1 = camera(31L, "rtsp://demo/stream31", 0F);
        Camera camera2 = camera(32L, "rtsp://demo/stream32", 0F);
        CameraAlgorithm binding1 = binding(31L, 41L);
        CameraAlgorithm binding2 = binding(32L, 42L);
        Algorithm algorithm1 = algorithm(41L, "{\"plugin_id\":\"yolov8n\",\"inference_time_ms\":1000}", 0);
        Algorithm algorithm2 = algorithm(42L, "{\"plugin_id\":\"yolov8n\",\"inference_time_ms\":1000}", 0);

        when(configService.getByValTag("infer_scheduler_enabled")).thenReturn("1");
        when(configService.getByValTag("infer_scheduler_cooldown_ms")).thenReturn("0");
        when(configService.getByValTag("infer_scheduler_latency_factor")).thenReturn("1");
        when(configService.getByValTag("infer_scheduler_concurrency_baseline")).thenReturn("1");
        when(cameraService.listActives()).thenReturn(List.of(camera1, camera2));
        when(cameraAlgorithmService.listByCamera(31L)).thenReturn(List.of(binding1));
        when(cameraAlgorithmService.listByCamera(32L)).thenReturn(List.of(binding2));
        when(algorithmService.getById(41L)).thenReturn(algorithm1);
        when(algorithmService.getById(42L)).thenReturn(algorithm2);
        when(inferenceApiController.dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt()))
                .thenReturn(JsonResultUtils.success(Map.of("trace_id", "trace-pressure")));

        Map<String, Object> first = schedulerService.dispatchActiveCameras();
        Map<String, Object> second = schedulerService.dispatchActiveCameras();

        assertEquals(2, ((Number) first.get("dispatch_count")).intValue());
        assertEquals(0, ((Number) second.get("dispatch_count")).intValue());
        assertEquals(2, ((Number) second.get("skip_count")).intValue());
        assertEquals(2, ((Number) second.get("concurrency_level")).intValue());
        assertEquals(2.0D, ((Number) second.get("concurrency_pressure")).doubleValue(), 0.0001D);

        List<Map<String, Object>> skipped = (List<Map<String, Object>>) second.get("skipped");
        assertEquals("cooldown", skipped.get(0).get("reason"));
        assertEquals(2000L, ((Number) skipped.get(0).get("latency_cooldown_ms")).longValue());
        assertEquals(2000L, ((Number) skipped.get(0).get("effective_cooldown_ms")).longValue());
        assertEquals(2, ((Number) skipped.get(0).get("concurrency_level")).intValue());
        assertEquals(2.0D, ((Number) skipped.get(0).get("concurrency_pressure")).doubleValue(), 0.0001D);
        verify(inferenceApiController, times(2)).dispatch(any(), isNull(), isNull(), isNull(), isNull(), anyInt());
    }

    private Camera camera(Long id, String rtspUrl, Float intervalTime) {
        Camera camera = new Camera();
        camera.setId(id);
        camera.setName("camera-" + id);
        camera.setRtspUrl(rtspUrl);
        camera.setIntervalTime(intervalTime);
        return camera;
    }

    private CameraAlgorithm binding(Long cameraId, Long algorithmId) {
        CameraAlgorithm binding = new CameraAlgorithm();
        binding.setCameraId(cameraId);
        binding.setAlgorithmId(algorithmId);
        binding.setConfidence(0.55F);
        return binding;
    }

    private Algorithm algorithm(Long id, String params, Integer intervalSeconds) {
        Algorithm algorithm = new Algorithm();
        algorithm.setId(id);
        algorithm.setName("algo-" + id);
        algorithm.setParams(params);
        algorithm.setIntervalTime(intervalSeconds);
        return algorithm;
    }
}
