package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InferenceIdempotencyService {

    private static final long DEFAULT_WINDOW_MS = 10 * 60 * 1000L;
    private static final int CLEANUP_TRIGGER_SIZE = 2000;

    private final ConcurrentHashMap<String, Long> keySeenAt = new ConcurrentHashMap<>();

    @Autowired
    private ConfigService configService;

    public synchronized Map<String, Object> checkAndMark(String traceId, Long cameraId, Long timestampMs) {
        Map<String, Object> data = new HashMap<>();
        data.put("trace_id", traceId);
        data.put("camera_id", cameraId);
        data.put("timestamp_ms", timestampMs);
        data.put("enabled", true);

        if (StrUtil.isBlank(traceId) || cameraId == null || timestampMs == null) {
            data.put("duplicate", false);
            data.put("status", "invalid_args");
            data.put("reason", "trace_id or camera_id or timestamp_ms missing");
            return data;
        }

        long now = System.currentTimeMillis();
        long windowMs = getWindowMs();
        cleanupIfNeeded(now, windowMs);

        String key = buildKey(traceId, cameraId, timestampMs);
        data.put("key", key);
        data.put("window_ms", windowMs);

        Long seenAt = keySeenAt.get(key);
        if (seenAt != null && (now - seenAt) <= windowMs) {
            data.put("duplicate", true);
            data.put("status", "duplicate");
            data.put("reason", "idempotent key already seen");
            return data;
        }

        keySeenAt.put(key, now);
        data.put("duplicate", false);
        data.put("status", "fresh");
        return data;
    }

    private String buildKey(String traceId, Long cameraId, Long timestampMs) {
        return traceId + "|" + cameraId + "|" + timestampMs;
    }

    private long getWindowMs() {
        String val = configService.getByValTag("infer_idempotent_window_ms");
        if (StrUtil.isBlank(val)) {
            return DEFAULT_WINDOW_MS;
        }
        try {
            long num = Long.parseLong(val.trim());
            return num > 0 ? num : DEFAULT_WINDOW_MS;
        } catch (Exception e) {
            return DEFAULT_WINDOW_MS;
        }
    }

    private void cleanupIfNeeded(long now, long windowMs) {
        if (keySeenAt.size() < CLEANUP_TRIGGER_SIZE) {
            return;
        }
        Iterator<Map.Entry<String, Long>> iterator = keySeenAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if ((now - entry.getValue()) > windowMs) {
                iterator.remove();
            }
        }
    }
}
