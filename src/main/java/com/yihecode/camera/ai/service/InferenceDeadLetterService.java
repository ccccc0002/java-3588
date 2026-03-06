package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InferenceDeadLetterService {

    private static final int DEFAULT_MAX_SIZE = 200;
    private static final int MAX_MAX_SIZE = 2000;
    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 200;

    private final Deque<Map<String, Object>> deadLetters = new ArrayDeque<>();
    private long sequence = 0L;

    @Autowired
    private ConfigService configService;

    public synchronized Map<String, Object> record(Map<String, Object> event) {
        Map<String, Object> entry = new LinkedHashMap<>();
        if (event != null) {
            entry.putAll(event);
        }
        long id = ++sequence;
        entry.put("dead_letter_id", id);
        if (!entry.containsKey("created_at_ms")) {
            entry.put("created_at_ms", System.currentTimeMillis());
        }

        deadLetters.addLast(entry);
        int maxSize = getMaxSize();
        while (deadLetters.size() > maxSize) {
            deadLetters.removeFirst();
        }
        return new LinkedHashMap<>(entry);
    }

    public synchronized List<Map<String, Object>> latest(Integer limit) {
        int effectiveLimit = normalizeListLimit(limit);
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> snapshot = new ArrayList<>(deadLetters);
        for (int i = snapshot.size() - 1; i >= 0 && result.size() < effectiveLimit; i--) {
            result.add(new LinkedHashMap<>(snapshot.get(i)));
        }
        return result;
    }

    public synchronized Map<String, Object> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("queue_size", deadLetters.size());
        data.put("max_size", getMaxSize());
        data.put("next_dead_letter_id", sequence + 1);

        Long oldestId = null;
        Long newestId = null;
        if (!deadLetters.isEmpty()) {
            oldestId = toLong(deadLetters.peekFirst().get("dead_letter_id"));
            newestId = toLong(deadLetters.peekLast().get("dead_letter_id"));
        }
        data.put("oldest_dead_letter_id", oldestId);
        data.put("newest_dead_letter_id", newestId);
        return data;
    }

    public synchronized Map<String, Object> clear() {
        int removed = deadLetters.size();
        deadLetters.clear();
        Map<String, Object> data = new HashMap<>();
        data.put("removed_count", removed);
        data.put("queue_size", deadLetters.size());
        return data;
    }

    private int getMaxSize() {
        String value = configService.getByValTag("infer_dead_letter_max_size");
        if (StrUtil.isBlank(value)) {
            return DEFAULT_MAX_SIZE;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                return DEFAULT_MAX_SIZE;
            }
            return Math.min(parsed, MAX_MAX_SIZE);
        } catch (Exception ignored) {
            return DEFAULT_MAX_SIZE;
        }
    }

    private int normalizeListLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }
}
