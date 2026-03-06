package com.yihecode.camera.ai.service;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginRouteResolverServiceTest {

    @Mock
    private PluginRegistryService pluginRegistryService;

    @InjectMocks
    private PluginRouteResolverService pluginRouteResolverService;

    @Test
    void hasSelector_shouldReturnTrue_whenPluginSelectorPresent() {
        Map<String, Object> body = new HashMap<>();
        body.put("plugin_id", "face-detector");

        assertTrue(pluginRouteResolverService.hasSelector(body));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolve_shouldReturnNoSelector_whenBodyMissingPluginFields() {
        Map<String, Object> data = pluginRouteResolverService.resolve(new HashMap<>());

        assertFalse((Boolean) data.get("requested"));
        assertFalse((Boolean) data.get("matched"));
        assertEquals("no_plugin_selector", data.get("reason"));
        assertTrue(((Map<String, Object>) data.get("selector")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolve_shouldReturnRequestedRegistration_whenRegistrationIdMatched() {
        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");
        record.setPluginId("face-detector");
        record.setVersion("1.0.0");
        record.setRuntime("rk3588_rknn");
        record.setCapabilities(Arrays.asList("inference", "alert"));
        record.setHealthy(true);
        record.setStatus("healthy");
        when(pluginRegistryService.findByRegistrationId("face-detector:1.0.0")).thenReturn(Optional.of(record));

        Map<String, Object> body = new HashMap<>();
        body.put("plugin_registration_id", "face-detector:1.0.0");
        Map<String, Object> data = pluginRouteResolverService.resolve(body);

        assertTrue((Boolean) data.get("requested"));
        assertTrue((Boolean) data.get("matched"));
        assertTrue((Boolean) data.get("available"));
        assertEquals("registration_id", data.get("source"));
        assertEquals("rk3588_rknn", data.get("backend_hint"));
        Map<String, Object> plugin = (Map<String, Object>) data.get("plugin");
        assertEquals("face-detector:1.0.0", plugin.get("registration_id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolve_shouldPreferHealthyLatestRegistration_whenFilteringByPluginAndCapability() {
        PluginRegistryRecord older = new PluginRegistryRecord();
        older.setRegistrationId("face-detector:1.0.0");
        older.setPluginId("face-detector");
        older.setVersion("1.0.0");
        older.setRuntime("rk3588_rknn");
        older.setCapabilities(Arrays.asList("inference"));
        older.setHealthy(false);
        older.setStatus("unreachable");
        older.setUpdatedAtMs(100L);

        PluginRegistryRecord newer = new PluginRegistryRecord();
        newer.setRegistrationId("face-detector:1.1.0");
        newer.setPluginId("face-detector");
        newer.setVersion("1.1.0");
        newer.setRuntime("rk3588_rknn");
        newer.setCapabilities(Arrays.asList("inference", "alert"));
        newer.setHealthy(true);
        newer.setInferUrl("http://plugin-b:19090/v1/infer");
        newer.setStatus("healthy");
        newer.setUpdatedAtMs(200L);

        when(pluginRegistryService.list()).thenReturn(Arrays.asList(older, newer));

        Map<String, Object> body = new HashMap<>();
        body.put("plugin_id", "face-detector");
        body.put("plugin_capabilities", List.of("inference"));
        Map<String, Object> data = pluginRouteResolverService.resolve(body);

        assertTrue((Boolean) data.get("matched"));
        assertTrue((Boolean) data.get("available"));
        assertEquals("registry_search", data.get("source"));
        Map<String, Object> plugin = (Map<String, Object>) data.get("plugin");
        assertEquals("face-detector:1.1.0", plugin.get("registration_id"));
        assertEquals("http://plugin-b:19090/v1/infer", plugin.get("infer_url"));
        Map<String, Object> selector = (Map<String, Object>) data.get("selector");
        assertEquals(Arrays.asList("inference"), selector.get("capabilities"));
    }

    @Test
    void resolve_shouldMarkUnavailable_whenOnlyDisabledPluginMatched() {
        PluginRegistryRecord record = new PluginRegistryRecord();
        record.setRegistrationId("face-detector:1.0.0");
        record.setPluginId("face-detector");
        record.setVersion("1.0.0");
        record.setRuntime("rk3588_rknn");
        record.setCapabilities(Arrays.asList("inference"));
        record.setHealthy(false);
        record.setStatus("disabled");
        when(pluginRegistryService.list()).thenReturn(Arrays.asList(record));

        Map<String, Object> body = new HashMap<>();
        body.put("plugin_id", "face-detector");
        Map<String, Object> data = pluginRouteResolverService.resolve(body);

        assertTrue((Boolean) data.get("matched"));
        assertFalse((Boolean) data.get("available"));
        assertEquals("plugin_unavailable", data.get("reason"));
    }
}
