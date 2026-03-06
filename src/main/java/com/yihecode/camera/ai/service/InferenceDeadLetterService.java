package com.yihecode.camera.ai.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InferenceDeadLetterService {

    private static final int DEFAULT_MAX_SIZE = 200;
    private static final int MAX_MAX_SIZE = 2000;
    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 200;
    private static final int DEFAULT_REPLAY_MAX_ATTEMPTS = 3;
    private static final int MAX_REPLAY_MAX_ATTEMPTS = 20;

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
        return latest(limit, false);
    }

    public synchronized List<Map<String, Object>> latest(Integer limit, Boolean onlyRetryable) {
        int effectiveLimit = normalizeListLimit(limit);
        boolean retryableOnly = onlyRetryable != null && onlyRetryable;
        int maxReplayAttempts = retryableOnly ? maxReplayAttempts() : 0;
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> snapshot = new ArrayList<>(deadLetters);
        for (int i = snapshot.size() - 1; i >= 0 && result.size() < effectiveLimit; i--) {
            Map<String, Object> entry = snapshot.get(i);
            if (retryableOnly && !isRetryable(entry, maxReplayAttempts)) {
                continue;
            }
            result.add(new LinkedHashMap<>(entry));
        }
        return result;
    }

    public synchronized Map<String, Object> stats() {
        Map<String, Object> data = new HashMap<>();
        int replayedEntryCount = 0;
        int replaySuccessEntryCount = 0;
        int replayFailedEntryCount = 0;
        int pendingReplayEntryCount = 0;
        int exhaustedReplayEntryCount = 0;
        int retryableEntryCount = 0;
        int nonRetryableEntryCount = 0;
        int maxReplayAttempts = maxReplayAttempts();
        for (Map<String, Object> entry : deadLetters) {
            int replayCount = toInt(entry.get("replay_count"), 0);
            boolean exhausted = replayCount >= maxReplayAttempts;
            if (exhausted) {
                nonRetryableEntryCount++;
            } else {
                retryableEntryCount++;
            }
            boolean replayed = replayCount > 0;
            if (!replayed) {
                pendingReplayEntryCount++;
                continue;
            }
            replayedEntryCount++;
            boolean success = toBoolean(entry.get("last_replay_success"), false);
            if (success) {
                replaySuccessEntryCount++;
            } else {
                replayFailedEntryCount++;
            }
            if (exhausted) {
                exhaustedReplayEntryCount++;
            }
        }

        data.put("queue_size", deadLetters.size());
        data.put("max_size", getMaxSize());
        data.put("default_list_limit", DEFAULT_LIST_LIMIT);
        data.put("max_list_limit", MAX_LIST_LIMIT);
        data.put("max_replay_attempts", maxReplayAttempts);
        data.put("replayed_entry_count", replayedEntryCount);
        data.put("replay_success_entry_count", replaySuccessEntryCount);
        data.put("replay_failed_entry_count", replayFailedEntryCount);
        data.put("pending_replay_entry_count", pendingReplayEntryCount);
        data.put("exhausted_replay_entry_count", exhaustedReplayEntryCount);
        data.put("retryable_entry_count", retryableEntryCount);
        data.put("non_retryable_entry_count", nonRetryableEntryCount);
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

    public int maxReplayAttempts() {
        return toPositiveIntWithinRange(
                configService.getByValTag("infer_dead_letter_replay_max_attempts"),
                DEFAULT_REPLAY_MAX_ATTEMPTS,
                MAX_REPLAY_MAX_ATTEMPTS
        );
    }

    public synchronized Map<String, Object> clear() {
        int removed = deadLetters.size();
        deadLetters.clear();
        Map<String, Object> data = new HashMap<>();
        data.put("removed_count", removed);
        data.put("queue_size", deadLetters.size());
        return data;
    }

    public synchronized Map<String, Object> findById(Long deadLetterId) {
        if (deadLetterId == null) {
            return null;
        }
        for (Map<String, Object> entry : deadLetters) {
            Long id = toLong(entry.get("dead_letter_id"));
            if (id != null && deadLetterId.equals(id)) {
                return new LinkedHashMap<>(entry);
            }
        }
        return null;
    }

    public synchronized boolean removeById(Long deadLetterId) {
        if (deadLetterId == null) {
            return false;
        }
        Iterator<Map<String, Object>> iterator = deadLetters.iterator();
        while (iterator.hasNext()) {
            Map<String, Object> entry = iterator.next();
            Long id = toLong(entry.get("dead_letter_id"));
            if (id != null && deadLetterId.equals(id)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public synchronized Map<String, Object> markReplay(Long deadLetterId,
                                                       boolean success,
                                                       String replayTraceId,
                                                       String replayMessage) {
        if (deadLetterId == null) {
            return null;
        }
        for (Map<String, Object> entry : deadLetters) {
            Long id = toLong(entry.get("dead_letter_id"));
            if (id == null || !deadLetterId.equals(id)) {
                continue;
            }
            int replayCount = toInt(entry.get("replay_count"), 0) + 1;
            entry.put("replay_count", replayCount);
            entry.put("last_replay_success", success);
            entry.put("last_replay_trace_id", replayTraceId);
            entry.put("last_replay_message", replayMessage);
            entry.put("last_replay_at_ms", System.currentTimeMillis());
            return new LinkedHashMap<>(entry);
        }
        return null;
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

    private int toPositiveIntWithinRange(String value, int defaultValue, int maxValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                return defaultValue;
            }
            return Math.min(parsed, maxValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean isRetryable(Map<String, Object> entry, int maxReplayAttempts) {
        int replayCount = toInt(entry == null ? null : entry.get("replay_count"), 0);
        return replayCount < maxReplayAttempts;
    }

    private boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        return defaultValue;
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
