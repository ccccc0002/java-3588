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
        when(runtimeApiService.buildRuntimeSnapshot()).thenReturn(Collections.singletonMap("device_count", 2));

        ResponseEntity<Map<String, Object>> response = runtimeApiController.runtimeSnapshot("Bearer token-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(2, ((Map<String, Object>) response.getBody().get("data")).get("device_count"));
        verify(runtimeApiService).buildRuntimeSnapshot();
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
}
