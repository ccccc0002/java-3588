package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
