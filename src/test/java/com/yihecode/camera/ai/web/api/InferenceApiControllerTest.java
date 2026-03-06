package com.yihecode.camera.ai.web.api;

import com.yihecode.camera.ai.service.InferenceIdempotencyService;
import com.yihecode.camera.ai.service.InferenceDeadLetterService;
import com.yihecode.camera.ai.service.InferenceReportBridgeService;
import com.yihecode.camera.ai.service.InferenceRoutingService;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.vo.InferenceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceApiControllerTest {

    @Mock
    private InferenceRoutingService inferenceRoutingService;

    @Mock
    private InferenceReportBridgeService inferenceReportBridgeService;

    @Mock
    private InferenceIdempotencyService inferenceIdempotencyService;

    @Mock
    private InferenceDeadLetterService inferenceDeadLetterService;

    @InjectMocks
    private InferenceApiController inferenceApiController;

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_shouldShortCircuit_whenIdempotentDuplicate() {
        Map<String, Object> body = new HashMap<>();
        body.put("trace_id", "trace-1");
        body.put("camera_id", 100L);
        body.put("model_id", 200L);
        Map<String, Object> frameMeta = new HashMap<>();
        frameMeta.put("timestamp_ms", 123L);
        body.put("frame_meta", frameMeta);

        Map<String, Object> idempotentData = new HashMap<>();
        idempotentData.put("enabled", true);
        idempotentData.put("duplicate", true);
        idempotentData.put("status", "duplicate");
        idempotentData.put("trace_id", "trace-1");
        idempotentData.put("camera_id", 100L);
        idempotentData.put("timestamp_ms", 123L);

        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceIdempotencyService.checkAndMark("trace-1", 100L, 123L)).thenReturn(idempotentData);

        JsonResult result = inferenceApiController.dispatch(body, null, null, null, null, 1);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(200L, ((Number) data.get("algorithm_id")).longValue());

        Map<String, Object> report = (Map<String, Object>) data.get("report");
        assertEquals("duplicate", report.get("status"));
        assertFalse((Boolean) report.get("persisted"));

        Map<String, Object> inferResult = (Map<String, Object>) data.get("result");
        assertEquals(0, ((Number) inferResult.get("attempt")).intValue());
        assertTrue(((List<?>) inferResult.get("detections")).isEmpty());

        verify(inferenceRoutingService, never()).infer(any());
        verify(inferenceReportBridgeService, never()).persistAndBroadcast(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_shouldCallInferAndPersist_whenFreshRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("trace_id", "trace-2");
        body.put("camera_id", 101L);
        body.put("model_id", 201L);
        body.put("algorithm_id", 301L);
        Map<String, Object> frameMeta = new HashMap<>();
        frameMeta.put("timestamp_ms", 456L);
        body.put("frame_meta", frameMeta);

        Map<String, Object> idempotentData = new HashMap<>();
        idempotentData.put("enabled", true);
        idempotentData.put("duplicate", false);
        idempotentData.put("status", "fresh");
        idempotentData.put("trace_id", "trace-2");
        idempotentData.put("camera_id", 101L);
        idempotentData.put("timestamp_ms", 456L);

        InferenceResult infer = new InferenceResult();
        infer.setTraceId("trace-2");
        infer.setCameraId(101L);
        infer.setLatencyMs(9L);
        infer.setBackendType("rk3588_rknn");
        infer.setAttempt(1);
        infer.setDetections(new ArrayList<>());

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("status", "ok");
        reportData.put("persisted", true);
        reportData.put("broadcasted", true);

        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(101L)).thenReturn("rk3588_rknn");
        when(inferenceIdempotencyService.checkAndMark("trace-2", 101L, 456L)).thenReturn(idempotentData);
        when(inferenceRoutingService.infer(any())).thenReturn(infer);
        when(inferenceReportBridgeService.persistAndBroadcast(eq(101L), eq(301L), eq(infer), eq("trace-2")))
                .thenReturn(reportData);

        JsonResult result = inferenceApiController.dispatch(body, null, null, null, null, 1);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(301L, ((Number) data.get("algorithm_id")).longValue());
        assertEquals("rk3588_rknn", data.get("backend_type"));

        Map<String, Object> report = (Map<String, Object>) data.get("report");
        assertEquals("ok", report.get("status"));
        assertTrue((Boolean) report.get("persisted"));

        verify(inferenceRoutingService).infer(any());
        verify(inferenceReportBridgeService).persistAndBroadcast(eq(101L), eq(301L), eq(infer), eq("trace-2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_shouldSkipIdempotentAndPersist_whenPersistReportDisabled() {
        Map<String, Object> body = new HashMap<>();
        body.put("trace_id", "trace-3");
        body.put("camera_id", 102L);
        body.put("model_id", 202L);
        body.put("persist_report", 0);
        Map<String, Object> frameMeta = new HashMap<>();
        frameMeta.put("timestamp_ms", 789L);
        body.put("frame_meta", frameMeta);

        InferenceResult infer = new InferenceResult();
        infer.setTraceId("trace-3");
        infer.setCameraId(102L);
        infer.setLatencyMs(3L);
        infer.setBackendType("legacy");
        infer.setAttempt(1);
        infer.setDetections(new ArrayList<>());

        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.infer(any())).thenReturn(infer);

        JsonResult result = inferenceApiController.dispatch(body, null, null, null, null, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Map<String, Object> idempotent = (Map<String, Object>) data.get("idempotent");
        Map<String, Object> report = (Map<String, Object>) data.get("report");

        assertFalse((Boolean) idempotent.get("enabled"));
        assertEquals("disabled", idempotent.get("status"));
        assertEquals("skipped", report.get("status"));

        verify(inferenceRoutingService).infer(any());
        verify(inferenceIdempotencyService, never()).checkAndMark(anyString(), anyLong(), anyLong());
        verify(inferenceReportBridgeService, never()).persistAndBroadcast(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_shouldRecordDeadLetter_whenInferThrows() {
        Map<String, Object> body = new HashMap<>();
        body.put("trace_id", "trace-dead-1");
        body.put("camera_id", 103L);
        body.put("model_id", 203L);
        body.put("persist_report", 0);

        when(inferenceRoutingService.currentBackendType()).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.infer(any())).thenThrow(new IllegalStateException("mock infer error"));
        when(inferenceDeadLetterService.record(any())).thenAnswer(invocation -> invocation.getArgument(0));

        JsonResult result = inferenceApiController.dispatch(body, null, null, null, null, null);

        assertTrue(result.getCode() != 0);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("rk3588_rknn", data.get("backend_type"));
        Map<String, Object> deadLetter = (Map<String, Object>) data.get("dead_letter");
        assertEquals("dispatch_exception", deadLetter.get("status"));
        assertEquals("trace-dead-1", deadLetter.get("trace_id"));
        assertEquals(103L, ((Number) deadLetter.get("camera_id")).longValue());
        verify(inferenceDeadLetterService, atLeastOnce()).record(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void route_shouldReturnResolvedBackendForCamera() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(100L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.overrideBackendForCamera(100L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.overrideSourceForCamera(100L)).thenReturn("direct_map");

        Map<String, Object> body = new HashMap<>();
        body.put("camera_id", 100L);
        JsonResult result = inferenceApiController.route(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(100L, ((Number) data.get("camera_id")).longValue());
        assertEquals("legacy", data.get("global_backend_type"));
        assertEquals("rk3588_rknn", data.get("backend_type"));
        assertEquals(true, data.get("override_hit"));
        assertEquals("rk3588_rknn", data.get("override_backend_type"));
        assertEquals("direct_map", data.get("override_source"));
        assertTrue(data.get("trace_id") != null && !"".equals(String.valueOf(data.get("trace_id"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void route_shouldAcceptCameraIdFromQueryParam_whenBodyMissing() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(222L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(222L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(222L)).thenReturn(null);

        JsonResult result = inferenceApiController.route(null, 222L);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(222L, ((Number) data.get("camera_id")).longValue());
        assertEquals("legacy", data.get("global_backend_type"));
        assertEquals("legacy", data.get("backend_type"));
        assertEquals(false, data.get("override_hit"));
        assertEquals(null, data.get("override_source"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void idempotentStats_shouldReturnServiceData() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("key_size", 12);
        stats.put("window_ms", 600000L);
        stats.put("cleanup_trigger_size", 2000);
        when(inferenceIdempotencyService.stats()).thenReturn(stats);

        JsonResult result = inferenceApiController.idempotentStats();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(data.get("trace_id") != null && !"".equals(String.valueOf(data.get("trace_id"))));
        Map<String, Object> idempotent = (Map<String, Object>) data.get("idempotent");
        assertEquals(12, ((Number) idempotent.get("key_size")).intValue());
        assertEquals(600000L, ((Number) idempotent.get("window_ms")).longValue());
        assertEquals(2000, ((Number) idempotent.get("cleanup_trigger_size")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void circuitStatus_shouldReturnServiceData() {
        Map<String, Object> status = new HashMap<>();
        status.put("trace_id", "trace-cs-api");
        status.put("backend", "rk3588_rknn");
        status.put("route_backend", "rk3588_rknn");
        status.put("circuit_open", false);
        when(inferenceRoutingService.circuitStatus(anyString())).thenReturn(status);
        when(inferenceRoutingService.currentBackendType()).thenReturn("rk3588_rknn");

        JsonResult result = inferenceApiController.circuitStatus();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("rk3588_rknn", data.get("backend_type"));
        Map<String, Object> circuit = (Map<String, Object>) data.get("circuit");
        assertEquals(false, circuit.get("circuit_open"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void circuitReset_shouldReturnServiceData() {
        Map<String, Object> reset = new HashMap<>();
        reset.put("trace_id", "trace-cr-api");
        reset.put("backend", "rk3588_rknn");
        reset.put("route_backend", "rk3588_rknn");
        reset.put("reset", true);
        when(inferenceRoutingService.resetCircuit(anyString())).thenReturn(reset);
        when(inferenceRoutingService.currentBackendType()).thenReturn("rk3588_rknn");

        JsonResult result = inferenceApiController.circuitReset();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("rk3588_rknn", data.get("backend_type"));
        Map<String, Object> circuit = (Map<String, Object>) data.get("circuit");
        assertEquals(true, circuit.get("reset"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterStats_shouldReturnServiceData() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("queue_size", 3);
        stats.put("max_size", 200);
        stats.put("replayed_entry_count", 1);
        stats.put("replay_success_entry_count", 1);
        stats.put("replay_failed_entry_count", 0);
        stats.put("pending_replay_entry_count", 2);
        stats.put("exhausted_replay_entry_count", 0);
        stats.put("retryable_entry_count", 3);
        stats.put("non_retryable_entry_count", 0);
        stats.put("replay_in_progress_entry_count", 1);
        when(inferenceDeadLetterService.stats()).thenReturn(stats);

        JsonResult result = inferenceApiController.deadLetterStats();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Map<String, Object> deadLetter = (Map<String, Object>) data.get("dead_letter");
        assertEquals(3, ((Number) deadLetter.get("queue_size")).intValue());
        assertEquals(200, ((Number) deadLetter.get("max_size")).intValue());
        assertEquals(1, ((Number) deadLetter.get("replayed_entry_count")).intValue());
        assertEquals(2, ((Number) deadLetter.get("pending_replay_entry_count")).intValue());
        assertEquals(3, ((Number) deadLetter.get("retryable_entry_count")).intValue());
        assertEquals(0, ((Number) deadLetter.get("non_retryable_entry_count")).intValue());
        assertEquals(1, ((Number) deadLetter.get("replay_in_progress_entry_count")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterLatest_shouldReturnServiceData() {
        List<Map<String, Object>> latest = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("trace_id", "trace-dl-latest");
        item.put("replay_count", 1);
        latest.add(item);
        when(inferenceDeadLetterService.latest(5, false, false)).thenReturn(latest);
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);

        JsonResult result = inferenceApiController.deadLetterLatest(5, null, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> deadLetter = (List<Map<String, Object>>) data.get("dead_letter");
        assertEquals(1, deadLetter.size());
        assertEquals("trace-dl-latest", deadLetter.get(0).get("trace_id"));
        assertEquals(2, ((Number) deadLetter.get(0).get("remaining_replay_attempts")).intValue());
        assertEquals(false, deadLetter.get(0).get("replay_exhausted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterLatest_shouldUseOnlyRetryableFilterWhenRequested() {
        List<Map<String, Object>> latest = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("trace_id", "trace-dl-retryable");
        item.put("replay_count", 2);
        latest.add(item);
        when(inferenceDeadLetterService.latest(5, true, false)).thenReturn(latest);
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);

        JsonResult result = inferenceApiController.deadLetterLatest(5, 1, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> deadLetter = (List<Map<String, Object>>) data.get("dead_letter");
        assertEquals(1, deadLetter.size());
        assertEquals("trace-dl-retryable", deadLetter.get(0).get("trace_id"));
        assertEquals(1, ((Number) deadLetter.get(0).get("remaining_replay_attempts")).intValue());
        assertEquals(false, deadLetter.get(0).get("replay_exhausted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterLatest_shouldUseOnlyExhaustedFilterWhenRequested() {
        List<Map<String, Object>> latest = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("trace_id", "trace-dl-exhausted");
        item.put("replay_count", 3);
        latest.add(item);
        when(inferenceDeadLetterService.latest(5, false, true)).thenReturn(latest);
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);

        JsonResult result = inferenceApiController.deadLetterLatest(5, null, 1);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> deadLetter = (List<Map<String, Object>>) data.get("dead_letter");
        assertEquals(1, deadLetter.size());
        assertEquals("trace-dl-exhausted", deadLetter.get(0).get("trace_id"));
        assertEquals(0, ((Number) deadLetter.get(0).get("remaining_replay_attempts")).intValue());
        assertEquals(true, deadLetter.get(0).get("replay_exhausted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterClear_shouldReturnServiceData() {
        Map<String, Object> clear = new HashMap<>();
        clear.put("removed_count", 2);
        clear.put("queue_size", 0);
        when(inferenceDeadLetterService.clear()).thenReturn(clear);

        JsonResult result = inferenceApiController.deadLetterClear();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Map<String, Object> deadLetter = (Map<String, Object>) data.get("dead_letter");
        assertEquals(2, ((Number) deadLetter.get("removed_count")).intValue());
        assertEquals(0, ((Number) deadLetter.get("queue_size")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterGet_shouldReturnEntryWhenExists() {
        Map<String, Object> item = new HashMap<>();
        item.put("dead_letter_id", 77L);
        item.put("trace_id", "trace-get-77");
        item.put("replay_count", 2);
        when(inferenceDeadLetterService.findById(77L)).thenReturn(item);
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);

        JsonResult result = inferenceApiController.deadLetterGet(77L);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        Map<String, Object> deadLetter = (Map<String, Object>) data.get("dead_letter");
        assertEquals(77L, ((Number) deadLetter.get("dead_letter_id")).longValue());
        assertEquals("trace-get-77", deadLetter.get("trace_id"));
        assertEquals(1, ((Number) deadLetter.get("remaining_replay_attempts")).intValue());
        assertEquals(false, deadLetter.get("replay_exhausted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterGet_shouldFailWhenNotFound() {
        when(inferenceDeadLetterService.findById(78L)).thenReturn(null);

        JsonResult result = inferenceApiController.deadLetterGet(78L);

        assertTrue(result.getCode() != 0);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(78L, ((Number) data.get("dead_letter_id")).longValue());
        assertTrue(String.valueOf(result.getMsg()).contains("dead letter not found"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterRemove_shouldDeleteWhenExists() {
        when(inferenceDeadLetterService.removeById(79L)).thenReturn(true);

        JsonResult result = inferenceApiController.deadLetterRemove(79L);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(79L, ((Number) data.get("dead_letter_id")).longValue());
        assertEquals(true, data.get("removed"));
        assertTrue(data.get("trace_id") != null && !"".equals(String.valueOf(data.get("trace_id"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterRemove_shouldFailWhenNotFound() {
        when(inferenceDeadLetterService.removeById(80L)).thenReturn(false);

        JsonResult result = inferenceApiController.deadLetterRemove(80L);

        assertTrue(result.getCode() != 0);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(80L, ((Number) data.get("dead_letter_id")).longValue());
        assertEquals(false, data.get("removed"));
        assertTrue(String.valueOf(result.getMsg()).contains("dead letter not found"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterReplay_shouldReplayAndAckWhenRequested() {
        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("trace_id", "trace-old");
        requestPayload.put("camera_id", 111L);
        requestPayload.put("model_id", 211L);
        requestPayload.put("frame", new HashMap<>());
        requestPayload.put("roi", new ArrayList<>());

        Map<String, Object> deadLetter = new HashMap<>();
        deadLetter.put("dead_letter_id", 10L);
        deadLetter.put("trace_id", "trace-old");
        deadLetter.put("camera_id", 111L);
        deadLetter.put("model_id", 211L);
        deadLetter.put("algorithm_id", 311L);
        deadLetter.put("persist_report", false);
        deadLetter.put("request_payload", requestPayload);

        InferenceResult infer = new InferenceResult();
        infer.setTraceId("trace-new");
        infer.setCameraId(111L);
        infer.setLatencyMs(5L);
        infer.setBackendType("rk3588_rknn");
        infer.setAttempt(1);
        infer.setDetections(new ArrayList<>());

        Map<String, Object> replayMeta = new HashMap<>();
        replayMeta.put("replay_count", 1);
        replayMeta.put("last_replay_success", true);

        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);
        when(inferenceDeadLetterService.tryAcquireReplay(eq(10L), anyString(), eq(3)))
                .thenReturn(buildAcquireResult(true, "ok", deadLetter));
        when(inferenceRoutingService.currentBackendType()).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.backendTypeForCamera(111L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.infer(any())).thenReturn(infer);
        when(inferenceDeadLetterService.markReplay(eq(10L), eq(true), anyString(), eq("ok"))).thenReturn(replayMeta);
        when(inferenceDeadLetterService.removeById(10L)).thenReturn(true);

        JsonResult result = inferenceApiController.deadLetterReplay(10L, 0, 1);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(10L, ((Number) data.get("dead_letter_id")).longValue());
        assertEquals("rk3588_rknn", data.get("backend_type"));
        assertEquals(true, data.get("acked"));
        Map<String, Object> report = (Map<String, Object>) data.get("report");
        assertEquals("skipped", report.get("status"));
        Map<String, Object> replayMetaData = (Map<String, Object>) data.get("replay_meta");
        assertEquals(1, ((Number) replayMetaData.get("replay_count")).intValue());
        Map<String, Object> replayBudget = (Map<String, Object>) data.get("replay_budget");
        assertEquals(3, ((Number) replayBudget.get("max_replay_attempts")).intValue());
        assertEquals(1, ((Number) replayBudget.get("replay_count")).intValue());
        assertEquals(2, ((Number) replayBudget.get("remaining_replay_attempts")).intValue());
        assertEquals(false, replayBudget.get("replay_exhausted"));
        Map<String, Object> request = (Map<String, Object>) data.get("request");
        Map<String, Object> frame = (Map<String, Object>) request.get("frame");
        assertEquals("dead_letter_replay", frame.get("replay_source"));
        assertEquals(10L, ((Number) frame.get("replay_dead_letter_id")).longValue());
        assertEquals(1, ((Number) frame.get("replay_count")).intValue());
        verify(inferenceDeadLetterService).releaseReplay(eq(10L), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterReplay_shouldFailWhenDeadLetterNotFound() {
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);
        when(inferenceDeadLetterService.tryAcquireReplay(eq(999L), anyString(), eq(3)))
                .thenReturn(buildAcquireResult(false, "not_found", null));

        JsonResult result = inferenceApiController.deadLetterReplay(999L, null, null);

        assertTrue(result.getCode() != 0);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(999L, ((Number) data.get("dead_letter_id")).longValue());
        assertTrue(String.valueOf(result.getMsg()).contains("dead letter not found"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterReplay_shouldFailWhenReplayAttemptsExhausted() {
        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("trace_id", "trace-limit");
        requestPayload.put("camera_id", 112L);
        requestPayload.put("model_id", 212L);
        requestPayload.put("frame", new HashMap<>());
        requestPayload.put("roi", new ArrayList<>());

        Map<String, Object> deadLetter = new HashMap<>();
        deadLetter.put("dead_letter_id", 11L);
        deadLetter.put("replay_count", 3);
        deadLetter.put("request_payload", requestPayload);
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);
        when(inferenceDeadLetterService.tryAcquireReplay(eq(11L), anyString(), eq(3)))
                .thenReturn(buildAcquireResult(false, "replay_exhausted", deadLetter));

        JsonResult result = inferenceApiController.deadLetterReplay(11L, null, null);

        assertTrue(result.getCode() != 0);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(11L, ((Number) data.get("dead_letter_id")).longValue());
        assertEquals(3, ((Number) data.get("replay_count")).intValue());
        assertEquals(3, ((Number) data.get("max_replay_attempts")).intValue());
        assertEquals(true, data.get("replay_exhausted"));
        assertTrue(String.valueOf(result.getMsg()).contains("replay attempts exhausted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterReplay_shouldFailWhenReplayAlreadyInProgress() {
        Map<String, Object> deadLetter = new HashMap<>();
        deadLetter.put("dead_letter_id", 13L);
        deadLetter.put("replay_in_progress", true);
        deadLetter.put("replay_lock_trace_id", "trace-lock-13");
        deadLetter.put("replay_lock_at_ms", 123456789L);
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);
        when(inferenceDeadLetterService.tryAcquireReplay(eq(13L), anyString(), eq(3)))
                .thenReturn(buildAcquireResult(false, "in_progress", deadLetter));

        JsonResult result = inferenceApiController.deadLetterReplay(13L, null, null);

        assertTrue(result.getCode() != 0);
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(13L, ((Number) data.get("dead_letter_id")).longValue());
        assertEquals(true, data.get("replay_in_progress"));
        assertEquals("trace-lock-13", data.get("replay_lock_trace_id"));
        assertTrue(String.valueOf(result.getMsg()).contains("already in progress"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterReplay_shouldMarkReplayFailedWhenInferThrows() {
        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("trace_id", "trace-replay-fail");
        requestPayload.put("camera_id", 113L);
        requestPayload.put("model_id", 213L);
        requestPayload.put("frame", new HashMap<>());
        requestPayload.put("roi", new ArrayList<>());

        Map<String, Object> deadLetter = new HashMap<>();
        deadLetter.put("dead_letter_id", 12L);
        deadLetter.put("replay_count", 0);
        deadLetter.put("request_payload", requestPayload);
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);
        when(inferenceDeadLetterService.tryAcquireReplay(eq(12L), anyString(), eq(3)))
                .thenReturn(buildAcquireResult(true, "ok", deadLetter));
        when(inferenceRoutingService.backendTypeForCamera(113L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.infer(any())).thenThrow(new IllegalStateException("mock replay infer error"));

        JsonResult result = inferenceApiController.deadLetterReplay(12L, null, null);

        assertTrue(result.getCode() != 0);
        verify(inferenceDeadLetterService).markReplay(eq(12L), eq(false), anyString(), eq("mock replay infer error"));
        verify(inferenceDeadLetterService).releaseReplay(eq(12L), anyString());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(12L, ((Number) data.get("dead_letter_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterReplayBatch_shouldReplaySelectedEntriesAndSummarize() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        Map<String, Object> c1 = new HashMap<>();
        c1.put("dead_letter_id", 21L);
        Map<String, Object> c2 = new HashMap<>();
        c2.put("dead_letter_id", 22L);
        candidates.add(c1);
        candidates.add(c2);

        Map<String, Object> payload1 = new HashMap<>();
        payload1.put("camera_id", 121L);
        payload1.put("model_id", 221L);
        payload1.put("frame", new HashMap<>());
        payload1.put("roi", new ArrayList<>());
        Map<String, Object> payload2 = new HashMap<>();
        payload2.put("camera_id", 122L);
        payload2.put("model_id", 222L);
        payload2.put("frame", new HashMap<>());
        payload2.put("roi", new ArrayList<>());

        Map<String, Object> entry1 = new HashMap<>();
        entry1.put("dead_letter_id", 21L);
        entry1.put("request_payload", payload1);
        entry1.put("algorithm_id", 321L);
        entry1.put("persist_report", false);
        Map<String, Object> entry2 = new HashMap<>();
        entry2.put("dead_letter_id", 22L);
        entry2.put("request_payload", payload2);
        entry2.put("algorithm_id", 322L);
        entry2.put("persist_report", false);

        InferenceResult infer = new InferenceResult();
        infer.setTraceId("trace-batch");
        infer.setLatencyMs(6L);
        infer.setBackendType("rk3588_rknn");
        infer.setAttempt(1);
        infer.setDetections(new ArrayList<>());

        Map<String, Object> replayMeta1 = new HashMap<>();
        replayMeta1.put("replay_count", 1);
        Map<String, Object> replayMeta2 = new HashMap<>();
        replayMeta2.put("replay_count", 1);

        when(inferenceDeadLetterService.latest(5, true, false)).thenReturn(candidates);
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);
        when(inferenceDeadLetterService.tryAcquireReplay(eq(21L), anyString(), eq(3))).thenReturn(buildAcquireResult(true, "ok", entry1));
        when(inferenceDeadLetterService.tryAcquireReplay(eq(22L), anyString(), eq(3))).thenReturn(buildAcquireResult(true, "ok", entry2));
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.currentBackendType()).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.infer(any())).thenReturn(infer);
        when(inferenceDeadLetterService.markReplay(eq(21L), eq(true), anyString(), eq("ok"))).thenReturn(replayMeta1);
        when(inferenceDeadLetterService.markReplay(eq(22L), eq(true), anyString(), eq("ok"))).thenReturn(replayMeta2);
        when(inferenceDeadLetterService.removeById(anyLong())).thenReturn(true);

        JsonResult result = inferenceApiController.deadLetterReplayBatch(5, 0, 1, 1, null, null, null, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(2, ((Number) data.get("selected_count")).intValue());
        assertEquals(2, ((Number) data.get("processed_count")).intValue());
        assertEquals(2, ((Number) data.get("success_count")).intValue());
        assertEquals(0, ((Number) data.get("failed_count")).intValue());
        assertEquals(0, ((Number) data.get("failed_replay_in_progress_count")).intValue());
        assertEquals(0, ((Number) data.get("failed_replay_exhausted_count")).intValue());
        assertEquals(0, ((Number) data.get("failed_other_count")).intValue());
        assertEquals(0, ((Number) data.get("dry_run_count")).intValue());
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
        assertEquals(2, results.size());
        assertEquals(0, ((Number) results.get(0).get("code")).intValue());
        assertEquals(0, ((Number) results.get(1).get("code")).intValue());
        verify(inferenceDeadLetterService, times(2)).releaseReplay(anyLong(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterReplayBatch_shouldIncludeFailureWhenReplayInProgress() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        Map<String, Object> c1 = new HashMap<>();
        c1.put("dead_letter_id", 31L);
        Map<String, Object> c2 = new HashMap<>();
        c2.put("dead_letter_id", 32L);
        candidates.add(c1);
        candidates.add(c2);

        Map<String, Object> payload1 = new HashMap<>();
        payload1.put("camera_id", 131L);
        payload1.put("model_id", 231L);
        payload1.put("frame", new HashMap<>());
        payload1.put("roi", new ArrayList<>());
        Map<String, Object> entry1 = new HashMap<>();
        entry1.put("dead_letter_id", 31L);
        entry1.put("request_payload", payload1);
        entry1.put("algorithm_id", 331L);
        entry1.put("persist_report", false);

        Map<String, Object> lockEntry = new HashMap<>();
        lockEntry.put("dead_letter_id", 32L);
        lockEntry.put("replay_in_progress", true);
        lockEntry.put("replay_lock_trace_id", "trace-lock-32");
        lockEntry.put("replay_lock_at_ms", 123L);

        InferenceResult infer = new InferenceResult();
        infer.setTraceId("trace-batch-2");
        infer.setLatencyMs(4L);
        infer.setBackendType("rk3588_rknn");
        infer.setAttempt(1);
        infer.setDetections(new ArrayList<>());

        Map<String, Object> replayMeta = new HashMap<>();
        replayMeta.put("replay_count", 1);

        when(inferenceDeadLetterService.latest(5, true, false)).thenReturn(candidates);
        when(inferenceDeadLetterService.maxReplayAttempts()).thenReturn(3);
        when(inferenceDeadLetterService.tryAcquireReplay(eq(31L), anyString(), eq(3))).thenReturn(buildAcquireResult(true, "ok", entry1));
        when(inferenceDeadLetterService.tryAcquireReplay(eq(32L), anyString(), eq(3))).thenReturn(buildAcquireResult(false, "in_progress", lockEntry));
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.currentBackendType()).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.infer(any())).thenReturn(infer);
        when(inferenceDeadLetterService.markReplay(eq(31L), eq(true), anyString(), eq("ok"))).thenReturn(replayMeta);

        JsonResult result = inferenceApiController.deadLetterReplayBatch(5, 0, null, 1, null, null, null, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(2, ((Number) data.get("selected_count")).intValue());
        assertEquals(1, ((Number) data.get("success_count")).intValue());
        assertEquals(1, ((Number) data.get("failed_count")).intValue());
        assertEquals(1, ((Number) data.get("failed_replay_in_progress_count")).intValue());
        assertEquals(0, ((Number) data.get("failed_replay_exhausted_count")).intValue());
        assertEquals(0, ((Number) data.get("failed_other_count")).intValue());
        assertEquals(0, ((Number) data.get("dry_run_count")).intValue());
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
        assertEquals(2, results.size());
        assertEquals(true, results.get(1).get("replay_in_progress"));
        verify(inferenceDeadLetterService, times(1)).releaseReplay(anyLong(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterReplayBatch_shouldSupportDryRunWithoutExecutingReplay() {
        List<Map<String, Object>> candidates = new ArrayList<>();
        Map<String, Object> c1 = new HashMap<>();
        c1.put("dead_letter_id", 41L);
        Map<String, Object> c2 = new HashMap<>();
        c2.put("dead_letter_id", 42L);
        candidates.add(c1);
        candidates.add(c2);
        when(inferenceDeadLetterService.latest(5, true, false)).thenReturn(candidates);

        JsonResult result = inferenceApiController.deadLetterReplayBatch(5, 0, 1, 1, null, 1, null, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(true, data.get("dry_run"));
        assertEquals(2, ((Number) data.get("processed_count")).intValue());
        assertEquals(2, ((Number) data.get("dry_run_count")).intValue());
        assertEquals(0, ((Number) data.get("success_count")).intValue());
        assertEquals(0, ((Number) data.get("failed_count")).intValue());
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
        assertEquals(2, results.size());
        assertEquals("dry_run", results.get(0).get("msg"));
        assertEquals("dry_run", results.get(1).get("msg"));

        verify(inferenceDeadLetterService, never()).tryAcquireReplay(anyLong(), anyString(), anyInt());
        verify(inferenceRoutingService, never()).infer(any());
        verify(inferenceDeadLetterService, never()).markReplay(anyLong(), anyBoolean(), anyString(), anyString());
        verify(inferenceDeadLetterService, never()).releaseReplay(anyLong(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLetterReplayBatch_shouldUseExplicitDeadLetterIdsWhenProvided() {
        JsonResult result = inferenceApiController.deadLetterReplayBatch(5, 0, 1, 1, null, 1, "52,51,52", null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("explicit_ids", data.get("selection_source"));
        assertEquals(2, ((Number) data.get("selected_count")).intValue());
        assertEquals(2, ((Number) data.get("processed_count")).intValue());
        assertEquals(2, ((Number) data.get("dry_run_count")).intValue());
        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
        assertEquals(2, results.size());
        assertEquals(52L, ((Number) results.get(0).get("dead_letter_id")).longValue());
        assertEquals(51L, ((Number) results.get(1).get("dead_letter_id")).longValue());

        verify(inferenceDeadLetterService, never()).latest(anyInt(), anyBoolean(), anyBoolean());
        verify(inferenceRoutingService, never()).infer(any());
    }

    private Map<String, Object> buildAcquireResult(boolean acquired, String reason, Map<String, Object> entry) {
        Map<String, Object> result = new HashMap<>();
        result.put("exists", entry != null);
        result.put("acquired", acquired);
        result.put("reason", reason);
        if (entry != null) {
            result.put("entry", entry);
        }
        return result;
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldReturnResolvedBackendsForMultipleCameras() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(100L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.backendTypeForCamera(101L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(100L)).thenReturn("rk3588_rknn");
        when(inferenceRoutingService.overrideBackendForCamera(101L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(100L)).thenReturn("direct_map");
        when(inferenceRoutingService.overrideSourceForCamera(101L)).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        List<Object> cameraIds = new ArrayList<>();
        cameraIds.add(100L);
        cameraIds.add(101L);
        body.put("camera_ids", cameraIds);

        JsonResult result = inferenceApiController.routeBatch(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(data.get("trace_id") != null && !"".equals(String.valueOf(data.get("trace_id"))));
        assertEquals("legacy", data.get("global_backend_type"));

        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(2, routes.size());
        assertEquals(100L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals("rk3588_rknn", routes.get(0).get("backend_type"));
        assertEquals(true, routes.get(0).get("override_hit"));
        assertEquals("direct_map", routes.get(0).get("override_source"));
        assertEquals(101L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals("legacy", routes.get(1).get("backend_type"));
        assertEquals(false, routes.get(1).get("override_hit"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldAcceptCommaQueryCameraIds_whenBodyMissing() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(201L)).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(202L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(201L)).thenReturn(null);
        when(inferenceRoutingService.overrideBackendForCamera(202L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(201L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(202L)).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "201,202");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(2, routes.size());
        assertEquals(201L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(202L, ((Number) routes.get(1).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldDeduplicateAndFallbackDefaultCamera() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(1L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(1L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(1L)).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, ",,");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(1, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(true, data.get("default_fallback_used"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldExpandRangeExpressionFromQueryAndPreserveOrder() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "100-102,101,110-108,abc, ,200");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(7, routes.size());
        assertEquals(100L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(101L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(102L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(110L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(109L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(108L, ((Number) routes.get(5).get("camera_id")).longValue());
        assertEquals(200L, ((Number) routes.get(6).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldExpandRangeExpressionFromBodyAndThenQuery() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("camera_ids", "300-302,305");
        JsonResult result = inferenceApiController.routeBatch(body, "304-303");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(6, routes.size());
        assertEquals(300L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(301L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(302L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(305L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(304L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(303L, ((Number) routes.get(5).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldCapExpandedCameraIdsToSafeLimit() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "1-600");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(500, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(500L, ((Number) routes.get(499).get("camera_id")).longValue());
        assertEquals(true, data.get("truncated"));
        assertEquals(500, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(500, ((Number) data.get("max_camera_ids_cap")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldParseRangeTokensInsideBodyListItems() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        List<Object> cameraIds = new ArrayList<>();
        cameraIds.add("10-12");
        cameraIds.add(12L);
        cameraIds.add("15,16");
        cameraIds.add("not-number");
        cameraIds.add(14L);
        body.put("camera_ids", cameraIds);

        JsonResult result = inferenceApiController.routeBatch(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(6, routes.size());
        assertEquals(10L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(11L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(12L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(15L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(16L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(14L, ((Number) routes.get(5).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldReportNotTruncatedWhenWithinLimit() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "1-3");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(false, data.get("truncated"));
        assertEquals(500, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(false, data.get("default_fallback_used"));
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(3, routes.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldAcceptCamerasAliasFromBodyList() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        List<Object> cameras = new ArrayList<>();
        cameras.add(100L);
        cameras.add("101-102");
        body.put("cameras", cameras);

        JsonResult result = inferenceApiController.routeBatch(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(3, routes.size());
        assertEquals(100L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(101L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(102L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldMergeBodyAliasesAndQueryInStableOrder() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("camera_ids", "10");
        body.put("cameras", "11-12,10");
        body.put("camera_range", "20-19");
        body.put("range", "30");

        JsonResult result = inferenceApiController.routeBatch(body, "31,12");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(7, routes.size());
        assertEquals(10L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(11L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(12L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(20L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(19L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(30L, ((Number) routes.get(5).get("camera_id")).longValue());
        assertEquals(31L, ((Number) routes.get(6).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
        List<String> hitSources = (List<String>) data.get("hit_sources");
        assertEquals(5, hitSources.size());
        assertEquals("body_camera_ids", hitSources.get(0));
        assertEquals("body_cameras", hitSources.get(1));
        assertEquals("body_camera_range", hitSources.get(2));
        assertEquals("body_range", hitSources.get(3));
        assertEquals("query_camera_ids", hitSources.get(4));
        assertEquals(null, data.get("truncated_source"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        assertEquals(5, sourceStats.size());
        Map<String, Object> bodyCamerasStats = (Map<String, Object>) sourceStats.get("body_cameras");
        assertEquals(2, ((Number) bodyCamerasStats.get("input_token_count")).intValue());
        assertEquals(3, ((Number) bodyCamerasStats.get("expanded_candidate_count")).intValue());
        assertEquals(1, ((Number) bodyCamerasStats.get("duplicate_filtered_count")).intValue());
        assertEquals(2, ((Number) bodyCamerasStats.get("unique_added_count")).intValue());
        assertEquals(false, bodyCamerasStats.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldAcceptQueryCamerasAlias() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, null, "501,503-504", null, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(3, routes.size());
        assertEquals(501L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(503L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(504L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldAcceptQueryCameraRangeAlias() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, null, null, "620-618", null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(3, routes.size());
        assertEquals(620L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(619L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(618L, ((Number) routes.get(2).get("camera_id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldMergeAllQueryAliasesInStableOrder() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(
                null,
                "700,701",
                "701,702",
                "703-704",
                "705,704"
        );

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(6, routes.size());
        assertEquals(700L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(701L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(702L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(703L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(704L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(705L, ((Number) routes.get(5).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldFallbackDefaultCameraWhenAllQueryAliasesInvalid() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(1L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(1L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(1L)).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, null, "abc", "x-y", " , ");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(1, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(false, data.get("truncated"));
        List<String> hitSources = (List<String>) data.get("hit_sources");
        assertEquals(0, hitSources.size());
        assertEquals(null, data.get("truncated_source"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        assertEquals(2, sourceStats.size());
        Map<String, Object> queryCamerasStats = (Map<String, Object>) sourceStats.get("query_cameras");
        assertEquals(1, ((Number) queryCamerasStats.get("input_token_count")).intValue());
        assertEquals(1, ((Number) queryCamerasStats.get("invalid_token_count")).intValue());
        assertEquals(0, ((Number) queryCamerasStats.get("unique_added_count")).intValue());
        Map<String, Object> queryCameraRangeStats = (Map<String, Object>) sourceStats.get("query_camera_range");
        assertEquals(1, ((Number) queryCameraRangeStats.get("input_token_count")).intValue());
        assertEquals(1, ((Number) queryCameraRangeStats.get("invalid_token_count")).intValue());
        assertEquals(0, ((Number) queryCameraRangeStats.get("unique_added_count")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldApplyMaxLimitAcrossMixedQueryAliases() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, null, "1-300", "301-600", null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(500, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(500L, ((Number) routes.get(499).get("camera_id")).longValue());
        assertEquals(true, data.get("truncated"));
        assertEquals(500, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(500, ((Number) data.get("max_camera_ids_cap")).intValue());
        List<String> hitSources = (List<String>) data.get("hit_sources");
        assertEquals(2, hitSources.size());
        assertEquals("query_cameras", hitSources.get(0));
        assertEquals("query_camera_range", hitSources.get(1));
        assertEquals("query_camera_range", data.get("truncated_source"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        Map<String, Object> queryRangeStats = (Map<String, Object>) sourceStats.get("query_camera_range");
        assertEquals(true, queryRangeStats.get("truncated"));
        assertEquals(1, ((Number) queryRangeStats.get("input_token_count")).intValue());
        assertEquals(200, ((Number) queryRangeStats.get("expanded_candidate_count")).intValue());
        assertEquals(200, ((Number) queryRangeStats.get("unique_added_count")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldExposeParseStatsForMixedTokens() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "100-102,101,bad, ,102,103-101");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(4, routes.size());
        assertEquals(100L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(101L, ((Number) routes.get(1).get("camera_id")).longValue());
        assertEquals(102L, ((Number) routes.get(2).get("camera_id")).longValue());
        assertEquals(103L, ((Number) routes.get(3).get("camera_id")).longValue());
        assertEquals(4, ((Number) data.get("resolved_camera_count")).intValue());
        assertEquals(5, ((Number) data.get("input_token_count")).intValue());
        assertEquals(8, ((Number) data.get("expanded_candidate_count")).intValue());
        assertEquals(1, ((Number) data.get("invalid_token_count")).intValue());
        assertEquals(4, ((Number) data.get("duplicate_filtered_count")).intValue());
        assertEquals(false, data.get("truncated"));
        List<String> hitSources = (List<String>) data.get("hit_sources");
        assertEquals(1, hitSources.size());
        assertEquals("query_camera_ids", hitSources.get(0));
        assertEquals(null, data.get("truncated_source"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        Map<String, Object> queryCameraIdsStats = (Map<String, Object>) sourceStats.get("query_camera_ids");
        assertEquals(5, ((Number) queryCameraIdsStats.get("input_token_count")).intValue());
        assertEquals(8, ((Number) queryCameraIdsStats.get("expanded_candidate_count")).intValue());
        assertEquals(1, ((Number) queryCameraIdsStats.get("invalid_token_count")).intValue());
        assertEquals(4, ((Number) queryCameraIdsStats.get("duplicate_filtered_count")).intValue());
        assertEquals(4, ((Number) queryCameraIdsStats.get("unique_added_count")).intValue());
        assertEquals(false, queryCameraIdsStats.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldExposeZeroParseStatsWhenFallingBackToDefaultCamera() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(1L)).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(1L)).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(1L)).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, ",,");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(1, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(1, ((Number) data.get("resolved_camera_count")).intValue());
        assertEquals(0, ((Number) data.get("input_token_count")).intValue());
        assertEquals(0, ((Number) data.get("expanded_candidate_count")).intValue());
        assertEquals(0, ((Number) data.get("invalid_token_count")).intValue());
        assertEquals(0, ((Number) data.get("duplicate_filtered_count")).intValue());
        assertEquals(true, data.get("default_fallback_used"));
        assertEquals(false, data.get("truncated"));
        Map<String, Object> sourceStats = (Map<String, Object>) data.get("source_stats");
        assertEquals(0, sourceStats.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldRespectLowerMaxCameraIdsFromQuery() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        JsonResult result = inferenceApiController.routeBatch(null, "1-20", null, null, null, 5);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(5, routes.size());
        assertEquals(1L, ((Number) routes.get(0).get("camera_id")).longValue());
        assertEquals(5L, ((Number) routes.get(4).get("camera_id")).longValue());
        assertEquals(true, data.get("truncated"));
        assertEquals("query_camera_ids", data.get("truncated_source"));
        assertEquals(5, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(500, ((Number) data.get("max_camera_ids_cap")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routeBatch_shouldClampBodyMaxCameraIdsToCap() {
        when(inferenceRoutingService.currentBackendType()).thenReturn("legacy");
        when(inferenceRoutingService.backendTypeForCamera(anyLong())).thenReturn("legacy");
        when(inferenceRoutingService.overrideBackendForCamera(anyLong())).thenReturn(null);
        when(inferenceRoutingService.overrideSourceForCamera(anyLong())).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("camera_ids", "1-999");
        body.put("max_camera_ids", 1000);

        JsonResult result = inferenceApiController.routeBatch(body, null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> routes = (List<Map<String, Object>>) data.get("route_list");
        assertEquals(500, routes.size());
        assertEquals(500, ((Number) data.get("max_camera_ids")).intValue());
        assertEquals(500, ((Number) data.get("max_camera_ids_cap")).intValue());
    }
}
