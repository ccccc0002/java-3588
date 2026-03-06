package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceDeadLetterServiceTest {

    @Mock
    private ConfigService configService;

    @InjectMocks
    private InferenceDeadLetterService inferenceDeadLetterService;

    @Test
    @SuppressWarnings("unchecked")
    void record_shouldAssignIdAndKeepLatestWithinConfiguredMaxSize() {
        when(configService.getByValTag("infer_dead_letter_max_size")).thenReturn("2");

        Map<String, Object> first = new HashMap<>();
        first.put("trace_id", "trace-1");
        Map<String, Object> second = new HashMap<>();
        second.put("trace_id", "trace-2");
        Map<String, Object> third = new HashMap<>();
        third.put("trace_id", "trace-3");

        inferenceDeadLetterService.record(first);
        inferenceDeadLetterService.record(second);
        Map<String, Object> latestEntry = inferenceDeadLetterService.record(third);

        assertNotNull(latestEntry.get("dead_letter_id"));
        assertEquals("trace-3", latestEntry.get("trace_id"));

        Map<String, Object> stats = inferenceDeadLetterService.stats();
        assertEquals(2, ((Number) stats.get("queue_size")).intValue());
        assertEquals(20, ((Number) stats.get("default_list_limit")).intValue());
        assertEquals(200, ((Number) stats.get("max_list_limit")).intValue());
        List<Map<String, Object>> latest = inferenceDeadLetterService.latest(10);
        assertEquals(2, latest.size());
        assertEquals("trace-3", latest.get(0).get("trace_id"));
        assertEquals("trace-2", latest.get(1).get("trace_id"));
    }

    @Test
    void latest_shouldApplyDefaultLimitWhenInvalidLimitProvided() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
        for (int i = 0; i < 25; i++) {
            Map<String, Object> event = new HashMap<>();
            event.put("trace_id", "trace-" + i);
            inferenceDeadLetterService.record(event);
        }

        List<Map<String, Object>> latest = inferenceDeadLetterService.latest(0);

        assertEquals(20, latest.size());
        assertEquals("trace-24", latest.get(0).get("trace_id"));
    }

    @Test
    void latest_shouldFilterOnlyRetryableWhenRequested() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
        when(configService.getByValTag("infer_dead_letter_replay_max_attempts")).thenReturn("3");

        Map<String, Object> first = new HashMap<>();
        first.put("trace_id", "trace-a");
        first.put("replay_count", 3);
        Map<String, Object> second = new HashMap<>();
        second.put("trace_id", "trace-b");
        second.put("replay_count", 2);
        Map<String, Object> third = new HashMap<>();
        third.put("trace_id", "trace-c");
        third.put("replay_count", 0);

        inferenceDeadLetterService.record(first);
        inferenceDeadLetterService.record(second);
        inferenceDeadLetterService.record(third);

        List<Map<String, Object>> latestRetryable = inferenceDeadLetterService.latest(10, true, false);

        assertEquals(2, latestRetryable.size());
        assertEquals("trace-c", latestRetryable.get(0).get("trace_id"));
        assertEquals("trace-b", latestRetryable.get(1).get("trace_id"));
    }

    @Test
    void latest_shouldFilterOnlyExhaustedWhenRequested() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
        when(configService.getByValTag("infer_dead_letter_replay_max_attempts")).thenReturn("3");

        Map<String, Object> first = new HashMap<>();
        first.put("trace_id", "trace-a");
        first.put("replay_count", 3);
        Map<String, Object> second = new HashMap<>();
        second.put("trace_id", "trace-b");
        second.put("replay_count", 2);
        Map<String, Object> third = new HashMap<>();
        third.put("trace_id", "trace-c");
        third.put("replay_count", 4);

        inferenceDeadLetterService.record(first);
        inferenceDeadLetterService.record(second);
        inferenceDeadLetterService.record(third);

        List<Map<String, Object>> latestExhausted = inferenceDeadLetterService.latest(10, false, true);

        assertEquals(2, latestExhausted.size());
        assertEquals("trace-c", latestExhausted.get(0).get("trace_id"));
        assertEquals("trace-a", latestExhausted.get(1).get("trace_id"));
    }

    @Test
    void clear_shouldRemoveAllEntries() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
        Map<String, Object> event = new HashMap<>();
        event.put("trace_id", "trace-clear");
        inferenceDeadLetterService.record(event);

        Map<String, Object> clearData = inferenceDeadLetterService.clear();
        Map<String, Object> stats = inferenceDeadLetterService.stats();

        assertEquals(1, ((Number) clearData.get("removed_count")).intValue());
        assertEquals(0, ((Number) clearData.get("queue_size")).intValue());
        assertEquals(0, ((Number) stats.get("queue_size")).intValue());
    }

    @Test
    void findMarkReplayAndRemove_shouldUpdateAndDeleteEntryById() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
        Map<String, Object> event = new HashMap<>();
        event.put("trace_id", "trace-replay");
        Map<String, Object> recorded = inferenceDeadLetterService.record(event);
        Long id = ((Number) recorded.get("dead_letter_id")).longValue();

        Map<String, Object> found = inferenceDeadLetterService.findById(id);
        assertEquals("trace-replay", found.get("trace_id"));

        Map<String, Object> marked = inferenceDeadLetterService.markReplay(id, true, "trace-replay-1", "ok");
        assertEquals(1, ((Number) marked.get("replay_count")).intValue());
        assertEquals(true, marked.get("last_replay_success"));
        assertEquals("trace-replay-1", marked.get("last_replay_trace_id"));

        boolean removed = inferenceDeadLetterService.removeById(id);
        assertEquals(true, removed);
        assertEquals(null, inferenceDeadLetterService.findById(id));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryAcquireAndReleaseReplay_shouldLockAndUnlockEntry() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
        Map<String, Object> event = new HashMap<>();
        event.put("trace_id", "trace-lock");
        event.put("replay_count", 1);
        Map<String, Object> recorded = inferenceDeadLetterService.record(event);
        Long id = ((Number) recorded.get("dead_letter_id")).longValue();

        Map<String, Object> firstAcquire = inferenceDeadLetterService.tryAcquireReplay(id, "trace-acquire-1", 3);
        assertTrue((Boolean) firstAcquire.get("exists"));
        assertTrue((Boolean) firstAcquire.get("acquired"));
        assertEquals("ok", firstAcquire.get("reason"));
        Map<String, Object> statsWhenLocked = inferenceDeadLetterService.stats();
        assertEquals(1, ((Number) statsWhenLocked.get("replay_in_progress_entry_count")).intValue());

        Map<String, Object> secondAcquire = inferenceDeadLetterService.tryAcquireReplay(id, "trace-acquire-2", 3);
        assertTrue((Boolean) secondAcquire.get("exists"));
        assertFalse((Boolean) secondAcquire.get("acquired"));
        assertEquals("in_progress", secondAcquire.get("reason"));

        inferenceDeadLetterService.releaseReplay(id, "trace-acquire-1");
        Map<String, Object> statsAfterRelease = inferenceDeadLetterService.stats();
        assertEquals(0, ((Number) statsAfterRelease.get("replay_in_progress_entry_count")).intValue());
        Map<String, Object> thirdAcquire = inferenceDeadLetterService.tryAcquireReplay(id, "trace-acquire-3", 3);
        assertTrue((Boolean) thirdAcquire.get("acquired"));
        assertEquals("ok", thirdAcquire.get("reason"));
    }

    @Test
    void maxReplayAttempts_shouldUseConfigAndClampInvalidValues() {
        when(configService.getByValTag("infer_dead_letter_replay_max_attempts")).thenReturn("5");
        assertEquals(5, inferenceDeadLetterService.maxReplayAttempts());

        when(configService.getByValTag("infer_dead_letter_replay_max_attempts")).thenReturn("-1");
        assertEquals(3, inferenceDeadLetterService.maxReplayAttempts());

        when(configService.getByValTag("infer_dead_letter_replay_max_attempts")).thenReturn("100");
        assertEquals(20, inferenceDeadLetterService.maxReplayAttempts());
    }

    @Test
    void stats_shouldExposeReplayClassificationCounters() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
        when(configService.getByValTag("infer_dead_letter_replay_max_attempts")).thenReturn("2");
        Map<String, Object> e1 = inferenceDeadLetterService.record(new HashMap<>());
        Map<String, Object> e2 = inferenceDeadLetterService.record(new HashMap<>());
        inferenceDeadLetterService.record(new HashMap<>());
        Long id1 = ((Number) e1.get("dead_letter_id")).longValue();
        Long id2 = ((Number) e2.get("dead_letter_id")).longValue();

        inferenceDeadLetterService.markReplay(id1, true, "trace-r1", "ok");
        inferenceDeadLetterService.markReplay(id2, false, "trace-r2", "timeout");
        inferenceDeadLetterService.markReplay(id2, false, "trace-r3", "timeout");

        Map<String, Object> stats = inferenceDeadLetterService.stats();

        assertEquals(3, ((Number) stats.get("queue_size")).intValue());
        assertEquals(2, ((Number) stats.get("max_replay_attempts")).intValue());
        assertEquals(2, ((Number) stats.get("replayed_entry_count")).intValue());
        assertEquals(1, ((Number) stats.get("replay_success_entry_count")).intValue());
        assertEquals(1, ((Number) stats.get("replay_failed_entry_count")).intValue());
        assertEquals(1, ((Number) stats.get("pending_replay_entry_count")).intValue());
        assertEquals(1, ((Number) stats.get("exhausted_replay_entry_count")).intValue());
        assertEquals(2, ((Number) stats.get("retryable_entry_count")).intValue());
        assertEquals(1, ((Number) stats.get("non_retryable_entry_count")).intValue());
        assertEquals(0, ((Number) stats.get("replay_in_progress_entry_count")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void stats_shouldAggregateBackendAndPluginDimensions() {
        lenient().when(configService.getByValTag(anyString())).thenReturn(null);
        when(configService.getByValTag("infer_dead_letter_replay_max_attempts")).thenReturn("3");

        Map<String, Object> first = new HashMap<>();
        first.put("trace_id", "trace-plugin-1");
        first.put("backend_type", "rk3588_rknn");
        first.put("error_type", "IllegalStateException");
        first.put("plugin_route", Map.of("plugin", Map.of(
                "registration_id", "face-detector:1.0.0",
                "plugin_id", "face-detector"
        )));

        Map<String, Object> second = new HashMap<>();
        second.put("trace_id", "trace-plugin-2");
        second.put("backend_type", "rk3588_rknn");
        second.put("error_type", "TimeoutException");
        second.put("plugin_dispatch", Map.of(
                "registration_id", "face-detector:1.0.0",
                "plugin_id", "face-detector"
        ));

        Map<String, Object> third = new HashMap<>();
        third.put("trace_id", "trace-legacy-1");
        third.put("backend_type", "legacy");
        third.put("error_type", "IllegalArgumentException");

        inferenceDeadLetterService.record(first);
        inferenceDeadLetterService.record(second);
        inferenceDeadLetterService.record(third);

        Map<String, Object> stats = inferenceDeadLetterService.stats();
        Map<String, Object> backendCounts = (Map<String, Object>) stats.get("backend_type_counts");
        Map<String, Object> errorTypeCounts = (Map<String, Object>) stats.get("error_type_counts");
        Map<String, Object> pluginIdCounts = (Map<String, Object>) stats.get("plugin_id_counts");
        Map<String, Object> pluginRegistrationCounts = (Map<String, Object>) stats.get("plugin_registration_id_counts");

        assertEquals(2, ((Number) backendCounts.get("rk3588_rknn")).intValue());
        assertEquals(1, ((Number) backendCounts.get("legacy")).intValue());
        assertEquals(1, ((Number) errorTypeCounts.get("TimeoutException")).intValue());
        assertEquals(1, ((Number) errorTypeCounts.get("IllegalArgumentException")).intValue());
        assertEquals(2, ((Number) pluginIdCounts.get("face-detector")).intValue());
        assertEquals(2, ((Number) pluginRegistrationCounts.get("face-detector:1.0.0")).intValue());
    }

}
