package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceIdempotencyServiceTest {

    @Mock
    private ConfigService configService;

    @InjectMocks
    private InferenceIdempotencyService inferenceIdempotencyService;

    @Test
    void checkAndMark_shouldReturnInvalidArgs_whenRequiredFieldsMissing() {
        Map<String, Object> data = inferenceIdempotencyService.checkAndMark("", 1L, 2L);

        assertEquals("invalid_args", data.get("status"));
        assertFalse((Boolean) data.get("duplicate"));
    }

    @Test
    void checkAndMark_shouldMarkDuplicate_onSecondCallWithinWindow() {
        when(configService.getByValTag("infer_idempotent_window_ms")).thenReturn("600000");

        Map<String, Object> first = inferenceIdempotencyService.checkAndMark("trace-a", 100L, 12345L);
        Map<String, Object> second = inferenceIdempotencyService.checkAndMark("trace-a", 100L, 12345L);

        assertEquals("fresh", first.get("status"));
        assertFalse((Boolean) first.get("duplicate"));

        assertEquals("duplicate", second.get("status"));
        assertTrue((Boolean) second.get("duplicate"));
        assertEquals(first.get("key"), second.get("key"));
    }

    @Test
    void checkAndMark_shouldUseDefaultWindow_whenConfigInvalid() {
        when(configService.getByValTag("infer_idempotent_window_ms")).thenReturn("-1");

        Map<String, Object> data = inferenceIdempotencyService.checkAndMark("trace-b", 101L, 98765L);

        assertEquals("fresh", data.get("status"));
        assertEquals(600000L, ((Number) data.get("window_ms")).longValue());
    }

    @Test
    void stats_shouldExposeCurrentSizeAndWindow() {
        when(configService.getByValTag("infer_idempotent_window_ms")).thenReturn("300000");

        inferenceIdempotencyService.checkAndMark("trace-c", 102L, 33333L);
        Map<String, Object> stats = inferenceIdempotencyService.stats();

        assertEquals(1, ((Number) stats.get("key_size")).intValue());
        assertEquals(300000L, ((Number) stats.get("window_ms")).longValue());
        assertEquals(2000, ((Number) stats.get("cleanup_trigger_size")).intValue());
    }
}
