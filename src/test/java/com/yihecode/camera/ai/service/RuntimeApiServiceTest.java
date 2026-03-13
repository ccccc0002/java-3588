package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.VideoPlay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeApiServiceTest {

    @Mock
    private CameraService cameraService;

    @Mock
    private VideoPlayService videoPlayService;

    @Mock
    private PluginRegistryService pluginRegistryService;

    @Mock
    private InferenceDeadLetterService inferenceDeadLetterService;

    @Mock
    private ConfigService configService;

    @Mock
    private MediaStreamUrlService mediaStreamUrlService;

    @Mock
    private ActiveCameraInferenceSchedulerService activeCameraInferenceSchedulerService;

    @InjectMocks
    private RuntimeApiService runtimeApiService;

    @BeforeEach
    void setupDefaults() {
        lenient().when(activeCameraInferenceSchedulerService.getLastSummary()).thenReturn(Collections.emptyMap());
    }

    @Test
    void buildRuntimeSnapshot_shouldExposeCountsAndQueueStats() {
        when(cameraService.listActives()).thenReturn(List.of(camera(1L, "cam-1", "rtsp://cam-1"), camera(2L, "cam-2", "rtsp://cam-2")));
        when(videoPlayService.list()).thenReturn(List.of(videoPlay(1L, 18082)));
        when(pluginRegistryService.list()).thenReturn(List.of(new PluginRegistryRecord(), new PluginRegistryRecord(), new PluginRegistryRecord()));
        when(inferenceDeadLetterService.stats()).thenReturn(Collections.singletonMap("queue_size", 4));
        when(mediaStreamUrlService.isZlmMode()).thenReturn(true);
        stubMediaConfig();
        when(mediaStreamUrlService.buildPlayUrl(any(Camera.class), eq(18082))).thenReturn("http://zlm.example/live/1.live.flv");
        when(mediaStreamUrlService.buildPushRtmpUrl(1L, 18082)).thenReturn("rtmp://127.0.0.1:1935/live/1");

        Map<String, Object> snapshot = runtimeApiService.buildRuntimeSnapshot();

        assertEquals(2, ((Number) snapshot.get("device_count")).intValue());
        assertEquals(1, ((Number) snapshot.get("session_count")).intValue());
        assertEquals(3, ((Number) snapshot.get("algorithm_count")).intValue());
        assertEquals(4, ((Number) snapshot.get("dead_letter_size")).intValue());
        assertEquals(0, ((Number) snapshot.get("push_queue_size")).intValue());
        assertEquals(1, ((Number) snapshot.get("ready_stream_count")).intValue());
        assertTrue(snapshot.containsKey("sessions"));

        Map<String, Object> media = (Map<String, Object>) snapshot.get("media");
        assertEquals("zlm", media.get("server_type"));
        assertEquals("stream", media.get("play_mode"));
        assertEquals("ffmpeg", media.get("ffmpeg_bin"));
        assertEquals("mpp", media.get("decode_backend"));
        assertEquals("rga", media.get("decode_hwaccel"));

        List<Map<String, Object>> streams = (List<Map<String, Object>>) snapshot.get("streams");
        assertEquals(1, streams.size());
        assertEquals(1L, streams.get(0).get("camera_id"));
        assertEquals("rtsp://cam-1", streams.get(0).get("rtsp_url"));
        assertEquals("http://zlm.example/live/1.live.flv", streams.get(0).get("play_url"));
        assertEquals("rtmp://127.0.0.1:1935/live/1", streams.get(0).get("push_url"));
    }

    @Test
    void buildInferencePlan_shouldReturnBudgetAndActiveItems() {
        when(cameraService.listActives()).thenReturn(List.of(camera(1L, "cam-1", "rtsp://cam-1"), camera(2L, "cam-2", "rtsp://cam-2")));
        when(videoPlayService.list()).thenReturn(List.of(videoPlay(1L, 18082)));
        when(mediaStreamUrlService.buildPlayUrl(any(Camera.class), eq(18082))).thenReturn("http://zlm.example/live/1.live.flv");
        when(mediaStreamUrlService.buildPushRtmpUrl(1L, 18082)).thenReturn("rtmp://127.0.0.1:1935/live/1");
        when(activeCameraInferenceSchedulerService.getLastSummary()).thenReturn(Map.of(
                "concurrency_level", 4,
                "concurrency_pressure", 2.2D,
                "max_effective_cooldown_ms", 5200
        ));
        stubMediaConfig();

        Map<String, Object> plan = runtimeApiService.buildInferencePlan(12.5D);

        assertEquals(12.5D, ((Number) plan.get("budget")).doubleValue());
        assertEquals(2, ((Number) plan.get("stream_count")).intValue());
        assertEquals(1, ((Number) plan.get("ready_stream_count")).intValue());
        List<Map<String, Object>> items = (List<Map<String, Object>>) plan.get("items");
        assertEquals(2, items.size());
        assertEquals(1L, items.get(0).get("camera_id"));
        assertEquals(Boolean.TRUE, items.get(0).get("ready"));
        assertEquals("http://zlm.example/live/1.live.flv", items.get(0).get("play_url"));
        assertEquals("rtmp://127.0.0.1:1935/live/1", items.get(0).get("push_url"));
        assertEquals(3, ((Number) items.get(0).get("suggested_frame_stride")).intValue());
        assertEquals(5200, ((Number) items.get(0).get("suggested_min_dispatch_ms")).intValue());
        assertEquals("scheduler_feedback", items.get(0).get("suggestion_source"));
        assertEquals(Boolean.FALSE, items.get(1).get("ready"));
        assertEquals("", items.get(1).get("play_url"));
        assertEquals("", items.get(1).get("push_url"));
        assertEquals(3, ((Number) items.get(1).get("suggested_frame_stride")).intValue());
        assertEquals(5200, ((Number) items.get(1).get("suggested_min_dispatch_ms")).intValue());
        assertEquals("scheduler_feedback", items.get(1).get("suggestion_source"));

        Map<String, Object> scheduler = (Map<String, Object>) plan.get("scheduler");
        assertEquals(4, scheduler.get("concurrency_level"));
        assertEquals(2.2D, ((Number) scheduler.get("concurrency_pressure")).doubleValue());

        Map<String, Object> throttleHint = (Map<String, Object>) plan.get("throttle_hint");
        assertEquals(3, ((Number) throttleHint.get("recommended_frame_stride")).intValue());
        assertEquals("scheduler_feedback", throttleHint.get("strategy_source"));
        assertEquals(6.25D, ((Number) throttleHint.get("estimated_budget_per_stream")).doubleValue(), 0.0001D);
    }
    @Test
    void buildRuntimeSnapshot_shouldFallbackToCameraLookupForRequestedStreams() {
        when(cameraService.listActives()).thenReturn(Collections.emptyList());
        when(cameraService.getById(1L)).thenReturn(camera(1L, "cam-1", "rtsp://cam-1"));
        when(videoPlayService.list()).thenReturn(List.of(videoPlay(1L, 18082)));
        when(pluginRegistryService.list()).thenReturn(Collections.emptyList());
        when(inferenceDeadLetterService.stats()).thenReturn(Collections.singletonMap("queue_size", 0));
        when(mediaStreamUrlService.isZlmMode()).thenReturn(true);
        stubMediaConfig();
        when(mediaStreamUrlService.buildPlayUrl(any(Camera.class), eq(18082))).thenReturn("http://zlm.example/live/1.live.flv");
        when(mediaStreamUrlService.buildPushRtmpUrl(1L, 18082)).thenReturn("rtmp://127.0.0.1:1935/live/1");

        Map<String, Object> snapshot = runtimeApiService.buildRuntimeSnapshot();

        assertEquals(1, ((Number) snapshot.get("ready_stream_count")).intValue());
        List<Map<String, Object>> streams = (List<Map<String, Object>>) snapshot.get("streams");
        assertEquals(1, streams.size());
        assertEquals(1L, streams.get(0).get("camera_id"));
        assertEquals("rtsp://cam-1", streams.get(0).get("rtsp_url"));
    }
    private void stubMediaConfig() {
        when(configService.getByValTag("media_server_type")).thenReturn("zlm");
        when(configService.getByValTag("zlm_play_mode")).thenReturn("stream");
        when(configService.getByValTag("media_ffmpeg_bin")).thenReturn("ffmpeg");
        when(configService.getByValTag("media_decode_backend")).thenReturn("mpp");
        when(configService.getByValTag("media_decode_hwaccel")).thenReturn("rga");
        when(configService.getByValTag("media_rtsp_transport")).thenReturn("tcp");
        when(configService.getByValTag("media_osd_enabled")).thenReturn("0");
        when(configService.getByValTag("media_video_codec")).thenReturn("h264_rkmpp");
        when(configService.getByValTag("media_sync_interval_sec")).thenReturn("2");
    }

    private Camera camera(Long id, String name, String rtspUrl) {
        Camera camera = new Camera();
        camera.setId(id);
        camera.setName(name);
        camera.setRtspUrl(rtspUrl);
        return camera;
    }

    private VideoPlay videoPlay(Long cameraId, Integer videoPort) {
        VideoPlay item = new VideoPlay();
        item.setCameraId(cameraId);
        item.setVideoPort(videoPort);
        return item;
    }
}
