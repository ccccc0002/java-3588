package com.yihecode.camera.ai.web.api;

import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.InferenceDeadLetterService;
import com.yihecode.camera.ai.service.InferenceIdempotencyService;
import com.yihecode.camera.ai.service.InferenceReportBridgeService;
import com.yihecode.camera.ai.service.InferenceRoutingService;
import com.yihecode.camera.ai.service.PluginInferenceDispatchService;
import com.yihecode.camera.ai.service.PluginRouteResolverService;
import com.yihecode.camera.ai.vo.InferenceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceApiControllerSourceResolutionTest {

    @Mock
    private InferenceRoutingService inferenceRoutingService;

    @Mock
    private InferenceReportBridgeService inferenceReportBridgeService;

    @Mock
    private InferenceIdempotencyService inferenceIdempotencyService;

    @Mock
    private InferenceDeadLetterService inferenceDeadLetterService;

    @Mock
    private ConfigService configService;

    @Mock
    private PluginRouteResolverService pluginRouteResolverService;

    @Mock
    private PluginInferenceDispatchService pluginInferenceDispatchService;

    @Mock
    private CameraService cameraService;

    @InjectMocks
    private InferenceApiController inferenceApiController;

    @Test
    @SuppressWarnings("unchecked")
    void buildTestRequest_shouldUseCameraRtspWhenFrameSourceMissing() {
        Camera camera = new Camera();
        camera.setId(501L);
        camera.setRtspUrl("rtsp://camera-501/main");
        when(cameraService.getById(501L)).thenReturn(camera);

        Map<String, Object> body = new HashMap<>();
        body.put("camera_id", 501L);
        body.put("frame", new HashMap<>());

        InferenceRequest request = ReflectionTestUtils.invokeMethod(
                inferenceApiController,
                "buildTestRequest",
                "trace-source-fill",
                body,
                null,
                null,
                null
        );

        Map<String, Object> payload = request.toPayload();
        Map<String, Object> frame = (Map<String, Object>) payload.get("frame");
        assertEquals("rtsp://camera-501/main", frame.get("source"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildDispatchRequest_shouldNotFallbackToSyntheticTestSourceWhenCameraRtspMissing() {
        Camera camera = new Camera();
        camera.setId(502L);
        camera.setRtspUrl("");
        when(cameraService.getById(502L)).thenReturn(camera);

        Map<String, Object> body = new HashMap<>();
        body.put("camera_id", 502L);
        body.put("frame", new HashMap<>());

        InferenceRequest request = ReflectionTestUtils.invokeMethod(
                inferenceApiController,
                "buildDispatchRequest",
                "trace-dispatch-source",
                body,
                null,
                null,
                null
        );

        Map<String, Object> payload = request.toPayload();
        Map<String, Object> frame = (Map<String, Object>) payload.get("frame");
        assertFalse(frame.containsKey("source"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildTestRequest_shouldFallbackToSyntheticTestSourceWhenCameraRtspMissing() {
        Camera camera = new Camera();
        camera.setId(503L);
        camera.setRtspUrl("");
        when(cameraService.getById(503L)).thenReturn(camera);

        Map<String, Object> body = new HashMap<>();
        body.put("camera_id", 503L);
        body.put("frame", new HashMap<>());

        InferenceRequest request = ReflectionTestUtils.invokeMethod(
                inferenceApiController,
                "buildTestRequest",
                "trace-test-source",
                body,
                null,
                null,
                null
        );

        Map<String, Object> payload = request.toPayload();
        Map<String, Object> frame = (Map<String, Object>) payload.get("frame");
        assertEquals("test://frame", frame.get("source"));
    }
}
