package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.entity.Camera;
import com.yihecode.camera.ai.entity.VideoPlay;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuntimeApiService {

    private final CameraService cameraService;
    private final VideoPlayService videoPlayService;
    private final PluginRegistryService pluginRegistryService;
    private final InferenceDeadLetterService inferenceDeadLetterService;
    private final ConfigService configService;
    private final MediaStreamUrlService mediaStreamUrlService;
    private final ActiveCameraInferenceSchedulerService activeCameraInferenceSchedulerService;

    public RuntimeApiService(CameraService cameraService,
                             VideoPlayService videoPlayService,
                             PluginRegistryService pluginRegistryService,
                             InferenceDeadLetterService inferenceDeadLetterService,
                             ConfigService configService,
                             MediaStreamUrlService mediaStreamUrlService,
                             ActiveCameraInferenceSchedulerService activeCameraInferenceSchedulerService) {
        this.cameraService = cameraService;
        this.videoPlayService = videoPlayService;
        this.pluginRegistryService = pluginRegistryService;
        this.inferenceDeadLetterService = inferenceDeadLetterService;
        this.configService = configService;
        this.mediaStreamUrlService = mediaStreamUrlService;
        this.activeCameraInferenceSchedulerService = activeCameraInferenceSchedulerService;
    }

    public Map<String, Object> buildRuntimeSnapshot() {
        List<Camera> activeCameras = safeCameras(cameraService.listActives());
        List<VideoPlay> activeStreams = safeVideoPlays(videoPlayService.list());
        int deadLetterSize = toInt(inferenceDeadLetterService.stats().get("queue_size"));
        Map<Long, Camera> cameraMap = toCameraMap(activeCameras);
        List<Map<String, Object>> streamItems = buildStreamItems(activeStreams, cameraMap);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessions", Collections.emptyMap());
        data.put("streams", streamItems);
        data.put("media", buildMediaConfig());
        data.put("session_count", activeStreams.size());
        data.put("stream_count", activeCameras.size());
        data.put("ready_stream_count", streamItems.size());
        data.put("device_count", activeCameras.size());
        data.put("algorithm_count", pluginRegistryService.list().size());
        data.put("base_library_count", 0);
        data.put("base_library_mapping_count", 0);
        data.put("offline_executor_count", 0);
        data.put("offline_job_count", 0);
        data.put("event_dedupe_size", 0);
        data.put("push_queue_size", 0);
        data.put("dead_letter_size", deadLetterSize);
        data.put("gray_batch_plan_cache_entries", 0);
        data.put("gray_batch_plan_cache_hits", 0);
        data.put("gray_batch_plan_cache_misses", 0);
        data.put("gray_batch_plan_cache_conflicts", 0);
        data.put("gray_batch_plan_cache_evicted_expired", 0);
        data.put("gray_batch_plan_cache_evicted_overflow", 0);
        data.put("gray_batch_plan_cache_last_minute_requests", 0);
        data.put("gray_batch_plan_cache_last_minute_hits", 0);
        data.put("gray_batch_plan_cache_last_minute_misses", 0);
        data.put("gray_batch_plan_cache_last_minute_conflicts", 0);
        data.put("gray_batch_plan_cache_last_minute_hit_rate_percent", 0);
        data.put("gray_batch_cache_policy_default_max_clear_entries", null);
        data.put("gray_batch_cache_policy_enabled", false);
        return data;
    }

    public Map<String, Object> buildInferencePlan(Double budget) {
        double normalizedBudget = budget == null || budget <= 0 ? 10.0D : budget;
        List<Camera> activeCameras = safeCameras(cameraService.listActives());
        List<VideoPlay> activeStreams = safeVideoPlays(videoPlayService.list());
        Map<Long, VideoPlay> videoPlayMap = toVideoPlayMap(activeStreams);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Camera camera : activeCameras) {
            VideoPlay videoPlay = videoPlayMap.get(camera.getId());
            Integer videoPort = videoPlay == null ? null : videoPlay.getVideoPort();
            Map<String, Object> item = new HashMap<>();
            item.put("camera_id", camera.getId());
            item.put("camera_name", camera.getName());
            item.put("rtsp_url", camera.getRtspUrl());
            item.put("ready", videoPlay != null);
            item.put("video_port", videoPort == null ? 0 : videoPort);
            item.put("play_url", videoPlay == null ? "" : trimToEmpty(mediaStreamUrlService.buildPlayUrl(camera, videoPort)));
            item.put("push_url", videoPlay == null ? "" : trimToEmpty(mediaStreamUrlService.buildPushRtmpUrl(camera.getId(), videoPort)));
            items.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> scheduler = activeCameraInferenceSchedulerService == null
                ? Collections.emptyMap()
                : activeCameraInferenceSchedulerService.getLastSummary();
        Map<String, Object> throttleHint = buildThrottleHint(scheduler, activeCameras.size(), normalizedBudget);

        data.put("budget", normalizedBudget);
        data.put("stream_count", activeCameras.size());
        data.put("ready_stream_count", activeStreams.size());
        data.put("media", buildMediaConfig());
        data.put("scheduler", scheduler);
        data.put("throttle_hint", throttleHint);
        data.put("items", items);
        return data;
    }

    private Map<String, Object> buildThrottleHint(Map<String, Object> scheduler, int streamCount, double budget) {
        double pressure = toPositiveDouble(scheduler == null ? null : scheduler.get("concurrency_pressure"), 1.0D);
        int concurrencyLevel = toInt(scheduler == null ? null : scheduler.get("concurrency_level"));
        int maxEffectiveCooldown = toInt(scheduler == null ? null : scheduler.get("max_effective_cooldown_ms"));

        int recommendedFrameStride;
        if (pressure >= 2.0D || maxEffectiveCooldown >= 5000) {
            recommendedFrameStride = 3;
        } else if (pressure >= 1.2D || maxEffectiveCooldown >= 2500) {
            recommendedFrameStride = 2;
        } else {
            recommendedFrameStride = 1;
        }

        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("concurrency_pressure", pressure);
        hint.put("concurrency_level", concurrencyLevel);
        hint.put("recommended_frame_stride", recommendedFrameStride);
        hint.put("estimated_budget_per_stream", streamCount <= 0 ? budget : budget / streamCount);
        hint.put("strategy_source", "scheduler_feedback");
        return hint;
    }

    private List<Map<String, Object>> buildStreamItems(List<VideoPlay> activeStreams, Map<Long, Camera> cameraMap) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (VideoPlay videoPlay : activeStreams) {
            if (videoPlay == null || videoPlay.getCameraId() == null) {
                continue;
            }
            Camera camera = cameraMap.get(videoPlay.getCameraId());
            if (camera == null) {
                camera = cameraService.getById(videoPlay.getCameraId());
            }
            String rtspUrl = camera == null ? "" : trimToEmpty(camera.getRtspUrl());
            if (StrUtil.isBlank(rtspUrl)) {
                continue;
            }
            Integer videoPort = videoPlay.getVideoPort();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("camera_id", videoPlay.getCameraId());
            item.put("camera_name", camera == null ? "" : trimToEmpty(camera.getName()));
            item.put("rtsp_url", rtspUrl);
            item.put("video_port", videoPort == null ? 0 : videoPort);
            item.put("play_url", trimToEmpty(mediaStreamUrlService.buildPlayUrl(camera, videoPort)));
            item.put("push_url", trimToEmpty(mediaStreamUrlService.buildPushRtmpUrl(videoPlay.getCameraId(), videoPort)));
            item.put("enabled", true);
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> buildMediaConfig() {
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("server_type", defaultIfBlank(configService.getByValTag("media_server_type"), mediaStreamUrlService.isZlmMode() ? "zlm" : "legacy"));
        media.put("play_mode", defaultIfBlank(configService.getByValTag("zlm_play_mode"), "stream"));
        media.put("ffmpeg_bin", defaultIfBlank(configService.getByValTag("media_ffmpeg_bin"), "ffmpeg"));
        media.put("decode_backend", defaultIfBlank(configService.getByValTag("media_decode_backend"), "mpp"));
        media.put("decode_hwaccel", defaultIfBlank(configService.getByValTag("media_decode_hwaccel"), "rga"));
        media.put("rtsp_transport", defaultIfBlank(configService.getByValTag("media_rtsp_transport"), "tcp"));
        media.put("osd_enabled", toBoolean(configService.getByValTag("media_osd_enabled")));
        media.put("video_codec", defaultIfBlank(configService.getByValTag("media_video_codec"), "h264_rkmpp"));
        media.put("sync_interval_sec", toPositiveInt(configService.getByValTag("media_sync_interval_sec"), 2));
        return media;
    }

    private Map<Long, Camera> toCameraMap(List<Camera> cameras) {
        Map<Long, Camera> data = new HashMap<>();
        for (Camera camera : cameras) {
            if (camera != null && camera.getId() != null) {
                data.put(camera.getId(), camera);
            }
        }
        return data;
    }

    private Map<Long, VideoPlay> toVideoPlayMap(List<VideoPlay> videoPlays) {
        Map<Long, VideoPlay> data = new HashMap<>();
        for (VideoPlay item : videoPlays) {
            if (item != null && item.getCameraId() != null) {
                data.put(item.getCameraId(), item);
            }
        }
        return data;
    }

    private List<Camera> safeCameras(List<Camera> cameras) {
        return cameras == null ? Collections.emptyList() : cameras;
    }

    private List<VideoPlay> safeVideoPlays(List<VideoPlay> videoPlays) {
        return videoPlays == null ? Collections.emptyList() : videoPlays;
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private double toPositiveDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            return number > 0D ? number : defaultValue;
        }
        if (value != null) {
            try {
                double number = Double.parseDouble(String.valueOf(value).trim());
                return number > 0D ? number : defaultValue;
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int toPositiveInt(String value, int defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            int number = Integer.parseInt(value.trim());
            return number > 0 ? number : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean toBoolean(String value) {
        String normalized = trimToEmpty(value).toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StrUtil.isBlank(value) ? defaultValue : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
