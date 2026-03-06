package com.yihecode.camera.ai.service;

import com.yihecode.camera.ai.plugin.PluginManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginRegistrationServiceImplTest {

    @Mock
    private PluginHealthProbeService pluginHealthProbeService;

    @Mock
    private PluginRegistryService pluginRegistryService;

    @InjectMocks
    private PluginRegistrationServiceImpl pluginRegistrationService;

    @Test
    @SuppressWarnings("unchecked")
    void register_shouldRejectInvalidManifestWithoutCallingHealthProbe() {
        PluginManifest manifest = new PluginManifest();
        manifest.setVersion("1.0.0");
        manifest.setRuntime("rk3588_rknn");
        manifest.setCapabilities(Arrays.asList("inference"));

        Map<String, Object> data = pluginRegistrationService.register("trace-plugin-register-1", manifest, "http://plugin-a:19090/health");

        assertEquals(Boolean.FALSE, data.get("accepted"));
        assertEquals("rejected", data.get("registration_status"));
        List<String> errors = (List<String>) data.get("errors");
        assertTrue(String.join(";", errors).toLowerCase().contains("plugin_id"));
        verify(pluginHealthProbeService, never()).probe(any(), any());
        verify(pluginRegistryService, never()).save(any());
    }

    @Test
    void register_shouldAcceptValidManifestWithoutHealthProbe() {
        PluginManifest manifest = new PluginManifest();
        manifest.setPluginId("face-detector");
        manifest.setVersion("1.0.0");
        manifest.setRuntime("rk3588_rknn");
        manifest.setCapabilities(Arrays.asList("inference", "alert"));

        Map<String, Object> data = pluginRegistrationService.register("trace-plugin-register-2", manifest, null);

        assertEquals(Boolean.TRUE, data.get("accepted"));
        assertEquals("accepted", data.get("registration_status"));
        assertEquals("face-detector:1.0.0", data.get("registration_id"));
        assertEquals("memory", data.get("storage_mode"));
        assertEquals("registered", ((Map<String, Object>) data.get("plugin")).get("status"));
        verify(pluginRegistryService).save(any());
    }

    @Test
    void register_shouldRejectWhenHealthProbeFails() {
        PluginManifest manifest = new PluginManifest();
        manifest.setPluginId("face-detector");
        manifest.setVersion("1.0.0");
        manifest.setRuntime("rk3588_rknn");
        manifest.setCapabilities(Arrays.asList("inference"));

        Map<String, Object> health = new HashMap<>();
        health.put("healthy", false);
        health.put("status", "down");
        health.put("error", "non-200 response: 503");
        when(pluginHealthProbeService.probe("trace-plugin-register-3", "http://plugin-a:19090/health")).thenReturn(health);

        Map<String, Object> data = pluginRegistrationService.register("trace-plugin-register-3", manifest, "http://plugin-a:19090/health");

        assertEquals(Boolean.FALSE, data.get("accepted"));
        assertEquals("health_check_failed", data.get("registration_status"));
        verify(pluginHealthProbeService).probe("trace-plugin-register-3", "http://plugin-a:19090/health");
        verify(pluginRegistryService, never()).save(any());
    }

    @Test
    void getRegistration_shouldReturnStoredRecordWhenFound() {
        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");
        record.setPluginId("face-detector");
        record.setVersion("1.0.0");
        when(pluginRegistryService.findByRegistrationId("face-detector:1.0.0")).thenReturn(Optional.of(record));

        Map<String, Object> data = pluginRegistrationService.getRegistration("trace-plugin-get-1", "face-detector:1.0.0");

        assertEquals("trace-plugin-get-1", data.get("trace_id"));
        assertNotNull(data.get("plugin"));
        verify(pluginRegistryService).findByRegistrationId("face-detector:1.0.0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRegistrations_shouldReturnStoredRecords() {
        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");
        record.setPluginId("face-detector");
        record.setVersion("1.0.0");
        when(pluginRegistryService.list()).thenReturn(Arrays.asList(record));

        Map<String, Object> data = pluginRegistrationService.listRegistrations("trace-plugin-list-1", null, null, null, null, null, null, 0, 100);

        assertEquals("trace-plugin-list-1", data.get("trace_id"));
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) data.get("plugins");
        assertEquals(1, plugins.size());
        assertEquals("face-detector:1.0.0", plugins.get(0).get("registration_id"));
        assertEquals(1, ((Number) data.get("total")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRegistrations_shouldFilterAndPaginateRecords() {
        PluginRegistryRecord first = new PluginRegistryRecord();
        first.setRegistrationId("face-detector:1.0.0");
        first.setPluginId("face-detector");
        first.setRuntime("rk3588_rknn");
        first.setHealthy(true);
        first.setStatus("healthy");

        PluginRegistryRecord second = new PluginRegistryRecord();
        second.setRegistrationId("helmet-detector:1.0.0");
        second.setPluginId("helmet-detector");
        second.setRuntime("rk3588_rknn");
        second.setHealthy(false);
        second.setStatus("unreachable");

        when(pluginRegistryService.list()).thenReturn(Arrays.asList(first, second));

        Map<String, Object> data = pluginRegistrationService.listRegistrations("trace-plugin-list-2", "detector", "rk3588", "healthy", true, null, null, 0, 10);

        List<Map<String, Object>> plugins = (List<Map<String, Object>>) data.get("plugins");
        assertEquals(1, plugins.size());
        assertEquals("face-detector:1.0.0", plugins.get(0).get("registration_id"));
        assertEquals(1, ((Number) data.get("total")).intValue());
        assertEquals(Boolean.FALSE, data.get("has_more"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshRegistration_shouldUpdateStoredHealthStatus() {
        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");
        record.setHealthUrl("http://plugin-a:19090/health");
        record.setStatus("registered");
        when(pluginRegistryService.findByRegistrationId("face-detector:1.0.0")).thenReturn(Optional.of(record));
        Map<String, Object> health = new HashMap<>();
        health.put("healthy", true);
        health.put("status", "ok");
        when(pluginHealthProbeService.probe("trace-plugin-refresh-1", "http://plugin-a:19090/health")).thenReturn(health);

        Map<String, Object> data = pluginRegistrationService.refreshRegistration("trace-plugin-refresh-1", "face-detector:1.0.0");

        assertEquals(Boolean.TRUE, data.get("found"));
        assertEquals(Boolean.TRUE, data.get("refreshed"));
        Map<String, Object> plugin = (Map<String, Object>) data.get("plugin");
        assertEquals("healthy", plugin.get("status"));
        verify(pluginRegistryService).save(any());
    }

    @Test
    void unregisterRegistration_shouldDeleteStoredRecord() {
        when(pluginRegistryService.delete("face-detector:1.0.0")).thenReturn(true);

        Map<String, Object> data = pluginRegistrationService.unregisterRegistration("trace-plugin-delete-1", "face-detector:1.0.0");

        assertEquals(Boolean.TRUE, data.get("removed"));
        assertEquals("trace-plugin-delete-1", data.get("trace_id"));
        verify(pluginRegistryService).delete("face-detector:1.0.0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void stats_shouldAggregateRegistryState() {
        PluginRegistryRecord first = new PluginRegistryRecord();
        first.setRegistrationId("face-detector:1.0.0");
        first.setPluginId("face-detector");
        first.setRuntime("rk3588_rknn");
        first.setCapabilities(Arrays.asList("inference", "alert"));
        first.setInferUrl("http://plugin-a:19090/v1/infer");
        first.setHealthUrl("http://plugin-a:19090/health");
        first.setHealthy(true);
        first.setStatus("healthy");
        first.setUpdatedAtMs(1000L);

        PluginRegistryRecord second = new PluginRegistryRecord();
        second.setRegistrationId("helmet-detector:1.1.0");
        second.setPluginId("helmet-detector");
        second.setRuntime("rk3588_rknn");
        second.setCapabilities(Arrays.asList("inference"));
        second.setHealthUrl("http://plugin-b:19090/health");
        second.setHealthy(false);
        second.setStatus("unreachable");
        second.setUpdatedAtMs(2000L);

        PluginRegistryRecord third = new PluginRegistryRecord();
        third.setRegistrationId("stream-proxy:2.0.0");
        third.setPluginId("stream-proxy");
        third.setRuntime("http_proxy");
        third.setCapabilities(Arrays.asList("stream"));
        third.setStatus("registered");
        third.setUpdatedAtMs(500L);

        when(pluginRegistryService.list()).thenReturn(Arrays.asList(first, second, third));

        Map<String, Object> data = pluginRegistrationService.stats("trace-plugin-stats-1");

        assertEquals("trace-plugin-stats-1", data.get("trace_id"));
        assertEquals(3, ((Number) data.get("total")).intValue());
        assertEquals(1, ((Number) data.get("healthy_count")).intValue());
        assertEquals(1, ((Number) data.get("unhealthy_count")).intValue());
        assertEquals(1, ((Number) data.get("unknown_health_count")).intValue());
        assertEquals(1, ((Number) data.get("dispatch_ready_count")).intValue());
        assertEquals(2000L, ((Number) data.get("latest_updated_at_ms")).longValue());
        assertEquals(500L, ((Number) data.get("oldest_updated_at_ms")).longValue());

        Map<String, Object> statusCounts = (Map<String, Object>) data.get("status_counts");
        assertEquals(1, ((Number) statusCounts.get("healthy")).intValue());
        assertEquals(1, ((Number) statusCounts.get("unreachable")).intValue());
        assertEquals(1, ((Number) statusCounts.get("registered")).intValue());

        Map<String, Object> runtimeCounts = (Map<String, Object>) data.get("runtime_counts");
        assertEquals(2, ((Number) runtimeCounts.get("rk3588_rknn")).intValue());
        assertEquals(1, ((Number) runtimeCounts.get("http_proxy")).intValue());

        Map<String, Object> capabilityCounts = (Map<String, Object>) data.get("capability_counts");
        assertEquals(2, ((Number) capabilityCounts.get("inference")).intValue());
        assertEquals(1, ((Number) capabilityCounts.get("alert")).intValue());
        assertEquals(1, ((Number) capabilityCounts.get("stream")).intValue());
    }


    @Test
    @SuppressWarnings("unchecked")
    void listRegistrations_shouldFilterByCapabilityAndDispatchReady() {
        PluginRegistryRecord first = new PluginRegistryRecord();
        first.setRegistrationId("face-detector:1.0.0");
        first.setPluginId("face-detector");
        first.setRuntime("rk3588_rknn");
        first.setCapabilities(Arrays.asList("inference", "alert"));
        first.setInferUrl("http://plugin-a:19090/v1/infer");
        first.setHealthy(true);
        first.setStatus("healthy");

        PluginRegistryRecord second = new PluginRegistryRecord();
        second.setRegistrationId("stream-proxy:1.0.0");
        second.setPluginId("stream-proxy");
        second.setRuntime("http_proxy");
        second.setCapabilities(Arrays.asList("stream"));
        second.setHealthy(true);
        second.setStatus("healthy");

        PluginRegistryRecord third = new PluginRegistryRecord();
        third.setRegistrationId("helmet-detector:1.0.0");
        third.setPluginId("helmet-detector");
        third.setRuntime("rk3588_rknn");
        third.setCapabilities(Arrays.asList("inference"));
        third.setInferUrl("http://plugin-b:19090/v1/infer");
        third.setHealthy(false);
        third.setStatus("unreachable");

        when(pluginRegistryService.list()).thenReturn(Arrays.asList(first, second, third));

        Map<String, Object> data = pluginRegistrationService.listRegistrations("trace-plugin-list-3", null, null, null, null, true, "inference", 0, 10);

        List<Map<String, Object>> plugins = (List<Map<String, Object>>) data.get("plugins");
        assertEquals(1, plugins.size());
        assertEquals("face-detector:1.0.0", plugins.get(0).get("registration_id"));
        assertEquals(Boolean.TRUE, data.get("dispatch_ready_filter"));
        assertEquals("inference", data.get("capability_filter"));
    }

}
