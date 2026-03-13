package com.yihecode.camera.ai.web.api;

import com.yihecode.camera.ai.service.ActiveCameraInferenceSchedulerService;
import com.yihecode.camera.ai.service.RuntimeAccessTokenService;
import com.yihecode.camera.ai.service.RuntimeApiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeApiControllerTest {

    @Mock
    private RuntimeAccessTokenService runtimeAccessTokenService;

    @Mock
    private RuntimeApiService runtimeApiService;

    @Mock
    private ActiveCameraInferenceSchedulerService activeCameraInferenceSchedulerService;

    @InjectMocks
    private RuntimeApiController runtimeApiController;

    @Test
    void issueToken_shouldRejectInvalidBootstrapToken() {
        when(runtimeAccessTokenService.isBootstrapTokenValid("wrong-token")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = runtimeApiController.issueToken("wrong-token", Collections.singletonMap("user_id", "edge-user"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }


    @Test
    void runtimeHealth_shouldReturnOkWithoutAuthorization() {
        ResponseEntity<Map<String, Object>> response = runtimeApiController.runtimeHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals("ok", ((Map<String, Object>) response.getBody().get("data")).get("status"));
    }

    @Test
    void runtimeSnapshot_shouldReturnDataWhenAuthorized() {
        when(runtimeAccessTokenService.isAuthorized("Bearer token-1")).thenReturn(true);
        when(runtimeApiService.buildRuntimeSnapshot()).thenReturn(Map.of(
                "device_count", 2,
                "telemetry_status", "degraded",
                "telemetry_error", "scheduler_summary_failed",
                "throttle_hint", Map.of(
                        "recommended_frame_stride", 2,
                        "suggested_min_dispatch_ms", 2400
                )
        ));

        ResponseEntity<Map<String, Object>> response = runtimeApiController.runtimeSnapshot("Bearer token-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(2, data.get("device_count"));
        assertEquals("degraded", data.get("telemetry_status"));
        assertEquals("scheduler_summary_failed", data.get("telemetry_error"));
        Map<String, Object> throttleHint = (Map<String, Object>) data.get("throttle_hint");
        assertEquals(2, ((Number) throttleHint.get("recommended_frame_stride")).intValue());
        assertEquals(2400, ((Number) throttleHint.get("suggested_min_dispatch_ms")).intValue());
        verify(runtimeApiService).buildRuntimeSnapshot();
    }

    @Test
    void inferencePlan_shouldReturnThrottleHintWhenAuthorized() {
        when(runtimeAccessTokenService.isAuthorized("Bearer token-plan")).thenReturn(true);
        when(runtimeApiService.buildInferencePlan(12.0D)).thenReturn(Map.of(
                "budget", 12.0D,
                "telemetry_status", "ok",
                "telemetry_error", "",
                "throttle_hint", Map.of(
                        "recommended_frame_stride", 3,
                        "suggested_min_dispatch_ms", 5200,
                        "strategy_source", "scheduler_feedback"
                )
        ));

        ResponseEntity<Map<String, Object>> response = runtimeApiController.inferencePlan(
                "Bearer token-plan",
                Collections.singletonMap("budget", 12.0D)
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(12.0D, ((Number) data.get("budget")).doubleValue());
        assertEquals("ok", data.get("telemetry_status"));
        assertEquals("", data.get("telemetry_error"));
        Map<String, Object> throttleHint = (Map<String, Object>) data.get("throttle_hint");
        assertEquals(3, ((Number) throttleHint.get("recommended_frame_stride")).intValue());
        assertEquals(5200, ((Number) throttleHint.get("suggested_min_dispatch_ms")).intValue());
        assertEquals("scheduler_feedback", throttleHint.get("strategy_source"));
        verify(runtimeApiService).buildInferencePlan(12.0D);
    }

    @Test
    void inferencePlan_shouldRejectUnauthorizedRequest() {
        when(runtimeAccessTokenService.isAuthorized("Bearer token-2")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = runtimeApiController.inferencePlan("Bearer token-2", Collections.singletonMap("budget", 10));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }

    @Test
    void schedulerSummary_shouldReturnDataWhenAuthorized() {
        when(runtimeAccessTokenService.isAuthorized("Bearer token-3")).thenReturn(true);
        when(activeCameraInferenceSchedulerService.getLastSummary()).thenReturn(Collections.singletonMap("concurrency_level", 2));

        ResponseEntity<Map<String, Object>> response = runtimeApiController.schedulerSummary("Bearer token-3");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(2, ((Map<String, Object>) response.getBody().get("data")).get("concurrency_level"));
        verify(activeCameraInferenceSchedulerService).getLastSummary();
    }

    @Test
    void schedulerDispatch_shouldRejectUnauthorizedRequest() {
        when(runtimeAccessTokenService.isAuthorized("Bearer token-4")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = runtimeApiController.schedulerDispatch("Bearer token-4");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }

    @Test
    void schedulerSummary_shouldReturnServiceUnavailableWhenSchedulerFails() {
        when(runtimeAccessTokenService.isAuthorized("Bearer token-5")).thenReturn(true);
        doThrow(new RuntimeException("scheduler boom"))
                .when(activeCameraInferenceSchedulerService)
                .getLastSummary();

        ResponseEntity<Map<String, Object>> response = runtimeApiController.schedulerSummary("Bearer token-5");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("scheduler_summary_failed", error.get("code"));
    }

    @Test
    void schedulerDispatch_shouldReturnServiceUnavailableWhenSchedulerFails() {
        when(runtimeAccessTokenService.isAuthorized("Bearer token-6")).thenReturn(true);
        doThrow(new RuntimeException("dispatch boom"))
                .when(activeCameraInferenceSchedulerService)
                .dispatchActiveCameras();

        ResponseEntity<Map<String, Object>> response = runtimeApiController.schedulerDispatch("Bearer token-6");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("scheduler_dispatch_failed", error.get("code"));
    }
}
