package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.entity.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportPushTargetServiceTest {

    @Mock
    private ConfigService configService;

    @InjectMocks
    private ReportPushTargetService reportPushTargetService;

    @Test
    void listAllTargets_shouldFallbackToLegacyConfigWhenTargetListMissing() {
        when(configService.getByValTag("reportPushTargets")).thenReturn(null);
        when(configService.getByValTag("reportPushImage")).thenReturn("true");
        when(configService.getByValTag("reportPushUrl")).thenReturn("http://legacy.local/push");

        List<Map<String, Object>> targets = reportPushTargetService.listAllTargets();

        assertEquals(1, targets.size());
        assertEquals("legacy-default", targets.get(0).get("id"));
        assertEquals("http://legacy.local/push", targets.get(0).get("url"));
        assertEquals(true, targets.get(0).get("include_image"));
        assertEquals(true, targets.get(0).get("enabled"));
    }

    @Test
    void saveTarget_shouldCreateAndUpdateTargetWithPersistence() {
        AtomicReference<String> targetStore = new AtomicReference<>("[]");
        when(configService.getByValTag("reportPushTargets")).thenAnswer(invocation -> targetStore.get());
        when(configService.getByValTag("reportPushImage")).thenReturn("false");
        when(configService.getByValTag("reportPushUrl")).thenReturn(null);
        when(configService.getOne(any(), eq(false))).thenReturn(null);
        when(configService.saveOrUpdate(any(Config.class))).thenAnswer(invocation -> {
            Config config = invocation.getArgument(0);
            if ("reportPushTargets".equals(config.getTag())) {
                targetStore.set(config.getVal());
            }
            return true;
        });

        List<Map<String, Object>> created = reportPushTargetService.saveTarget(
                null,
                "Target-A",
                "http://localhost:9001/push",
                "token-a",
                true,
                true
        );
        assertEquals(1, created.size());
        String targetId = (String) created.get(0).get("id");
        assertNotNull(targetId);
        assertEquals("Target-A", created.get(0).get("name"));
        assertEquals("token-a", created.get(0).get("bearer_token"));
        assertEquals(true, created.get(0).get("include_image"));

        List<Map<String, Object>> updated = reportPushTargetService.saveTarget(
                targetId,
                "Target-B",
                "http://localhost:9002/push",
                "",
                false,
                false
        );
        assertEquals(1, updated.size());
        assertEquals(targetId, updated.get(0).get("id"));
        assertEquals("Target-B", updated.get(0).get("name"));
        assertEquals("http://localhost:9002/push", updated.get(0).get("url"));
        assertEquals(false, updated.get(0).get("enabled"));
        assertEquals(false, updated.get(0).get("include_image"));

        verify(configService, atLeastOnce()).saveOrUpdate(any(Config.class));
        verify(configService, atLeastOnce()).evictByTag("reportPushTargets");
    }

    @Test
    void deleteTarget_shouldRemoveSpecifiedTarget() {
        AtomicReference<String> targetStore = new AtomicReference<>(
                "[{\"id\":\"t1\",\"name\":\"A\",\"url\":\"http://a\",\"enabled\":true,\"include_image\":false}," +
                        "{\"id\":\"t2\",\"name\":\"B\",\"url\":\"http://b\",\"enabled\":true,\"include_image\":true}]"
        );
        when(configService.getByValTag("reportPushTargets")).thenAnswer(invocation -> targetStore.get());
        when(configService.getByValTag("reportPushImage")).thenReturn("false");
        when(configService.getOne(any(), eq(false))).thenReturn(null);
        when(configService.saveOrUpdate(any(Config.class))).thenAnswer(invocation -> {
            Config config = invocation.getArgument(0);
            if ("reportPushTargets".equals(config.getTag())) {
                targetStore.set(config.getVal());
            }
            return true;
        });

        List<Map<String, Object>> retained = reportPushTargetService.deleteTarget("t1");

        assertEquals(1, retained.size());
        assertEquals("t2", retained.get(0).get("id"));
        assertFalse(String.valueOf(retained.get(0).get("url")).contains("http://a"));
        assertTrue(String.valueOf(retained.get(0).get("url")).contains("http://b"));
    }

    @Test
    void saveTarget_shouldPersistAuthFileAndRetryCount() {
        AtomicReference<String> targetStore = new AtomicReference<>("[]");
        when(configService.getByValTag("reportPushTargets")).thenAnswer(invocation -> targetStore.get());
        when(configService.getByValTag("reportPushImage")).thenReturn("false");
        when(configService.getByValTag("reportPushUrl")).thenReturn(null);
        when(configService.getOne(any(), eq(false))).thenReturn(null);
        when(configService.saveOrUpdate(any(Config.class))).thenAnswer(invocation -> {
            Config config = invocation.getArgument(0);
            if ("reportPushTargets".equals(config.getTag())) {
                targetStore.set(config.getVal());
            }
            return true;
        });

        List<Map<String, Object>> created = reportPushTargetService.saveTarget(
                null,
                "Target-Retry",
                "http://localhost:9010/push",
                "",
                true,
                false,
                "/opt/auth/push.token",
                3
        );

        assertEquals(1, created.size());
        assertEquals("/opt/auth/push.token", created.get(0).get("auth_file"));
        assertEquals(3, created.get(0).get("retry_count"));
    }
}
