package com.yihecode.camera.ai.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeAccessTokenServiceTest {

    @Mock
    private ConfigService configService;

    @AfterEach
    void tearDown() {
        System.clearProperty("runtime.bootstrap.token");
    }

    @Test
    void resolveBootstrapToken_shouldPreferSystemPropertyOverConfiguredValue() {
        lenient().when(configService.getByValTag(anyString())).thenReturn("db-bootstrap-token");
        System.setProperty("runtime.bootstrap.token", "system-bootstrap-token");
        RuntimeAccessTokenService service = new RuntimeAccessTokenService(configService, () -> 1000L);

        String token = service.resolveBootstrapToken();

        assertEquals("system-bootstrap-token", token);
    }

    @Test
    void issueToken_shouldAuthorizeBeforeExpiry() {
        AtomicLong now = new AtomicLong(1_000L);
        when(configService.getByValTag("runtime_token_ttl_sec")).thenReturn("60");
        RuntimeAccessTokenService service = new RuntimeAccessTokenService(configService, now::get);

        Map<String, Object> issued = service.issueToken("edge-user", "admin");

        String token = String.valueOf(issued.get("token"));
        assertNotNull(token);
        assertTrue(service.isAuthorized("Bearer " + token));
    }

    @Test
    void isAuthorized_shouldRejectExpiredToken() {
        AtomicLong now = new AtomicLong(2_000L);
        when(configService.getByValTag("runtime_token_ttl_sec")).thenReturn("1");
        RuntimeAccessTokenService service = new RuntimeAccessTokenService(configService, now::get);

        Map<String, Object> issued = service.issueToken("edge-user", "admin");
        String token = String.valueOf(issued.get("token"));
        now.set(5_000L);

        assertFalse(service.isAuthorized("Bearer " + token));
    }

    @Test
    void isBootstrapTokenValid_shouldRequireExactMatch() {
        when(configService.getByValTag("runtime_bootstrap_token")).thenReturn("edge-demo-bootstrap");
        RuntimeAccessTokenService service = new RuntimeAccessTokenService(configService, () -> 1000L);

        assertTrue(service.isBootstrapTokenValid("edge-demo-bootstrap"));
        assertFalse(service.isBootstrapTokenValid("wrong-token"));
    }
}
