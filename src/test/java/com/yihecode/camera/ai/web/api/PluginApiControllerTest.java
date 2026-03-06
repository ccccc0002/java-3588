package com.yihecode.camera.ai.web.api;

import com.yihecode.camera.ai.service.PluginHealthProbeService;
import com.yihecode.camera.ai.service.PluginRegistrationService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginApiControllerTest {

    @Mock
    private PluginRegistrationService pluginRegistrationService;

    @Mock
    private PluginHealthProbeService pluginHealthProbeService;

    @InjectMocks
    private PluginApiController pluginApiController;

    @Test
    @SuppressWarnings("unchecked")
    void validateManifest_shouldReturnNormalizedManifestWhenValid() {
        Map<String, Object> body = new HashMap<>();
        body.put("plugin_id", " face-detector ");
        body.put("version", "1.0.0");
        body.put("runtime", " rk3588_rknn ");
        body.put("capabilities", Arrays.asList(" inference ", "alert", "inference", " "));

        JsonResult result = pluginApiController.validateManifest(body);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("plugin-manifest.v1", data.get("schema_version"));
        assertEquals(Boolean.TRUE, data.get("valid"));
        assertNotNull(data.get("trace_id"));
        assertTrue(((List<String>) data.get("errors")).isEmpty());

        Map<String, Object> normalized = (Map<String, Object>) data.get("normalized_manifest");
        assertEquals("face-detector", normalized.get("plugin_id"));
        assertEquals("1.0.0", normalized.get("version"));
        assertEquals("rk3588_rknn", normalized.get("runtime"));
        assertEquals(Arrays.asList("inference", "alert"), normalized.get("capabilities"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateManifest_shouldReturnErrorsWhenInvalid() {
        Map<String, Object> body = new HashMap<>();
        body.put("plugin_id", " ");
        body.put("version", "1.0");
        body.put("runtime", "");
        body.put("capabilities", Arrays.asList(" "));

        JsonResult result = pluginApiController.validateManifest(body);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(Boolean.FALSE, data.get("valid"));
        List<String> errors = (List<String>) data.get("errors");
        String joined = String.join(";", errors).toLowerCase();
        assertTrue(joined.contains("plugin_id"));
        assertTrue(joined.contains("version"));
        assertTrue(joined.contains("runtime"));
        assertTrue(joined.contains("capabilities"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateManifest_shouldHandleNullBody() {
        JsonResult result = pluginApiController.validateManifest(null);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(Boolean.FALSE, data.get("valid"));
        assertFalse(((List<String>) data.get("errors")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthProbe_shouldReturnProbeData() {
        Map<String, Object> probe = new HashMap<>();
        probe.put("trace_id", "trace-plugin-probe-1");
        probe.put("healthy", true);
        probe.put("status", "ok");
        when(pluginHealthProbeService.probe(anyString(), anyString())).thenReturn(probe);

        JsonResult result = pluginApiController.healthProbe(Map.of("health_url", "http://plugin-a:19090/health"));

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(Boolean.TRUE, data.get("healthy"));
        assertEquals("ok", data.get("status"));
        verify(pluginHealthProbeService).probe(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void register_shouldReturnRegistrationData() {
        Map<String, Object> registration = new HashMap<>();
        registration.put("trace_id", "trace-plugin-register-4");
        registration.put("accepted", true);
        registration.put("registration_id", "face-detector:1.0.0");
        when(pluginRegistrationService.register(anyString(), any(), anyString())).thenReturn(registration);

        Map<String, Object> body = new HashMap<>();
        body.put("plugin_id", "face-detector");
        body.put("version", "1.0.0");
        body.put("runtime", "rk3588_rknn");
        body.put("capabilities", Arrays.asList("inference"));
        body.put("health_url", "http://plugin-a:19090/health");

        JsonResult result = pluginApiController.register(body);

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(Boolean.TRUE, data.get("accepted"));
        assertEquals("face-detector:1.0.0", data.get("registration_id"));
        verify(pluginRegistrationService).register(anyString(), any(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_shouldReturnPluginRegistrations() {
        Map<String, Object> listing = new HashMap<>();
        listing.put("trace_id", "trace-plugin-list-2");
        listing.put("plugins", Arrays.asList(Map.of("registration_id", "face-detector:1.0.0")));
        when(pluginRegistrationService.listRegistrations(anyString())).thenReturn(listing);

        JsonResult result = pluginApiController.list();

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) data.get("plugins");
        assertEquals(1, plugins.size());
        assertEquals("face-detector:1.0.0", plugins.get(0).get("registration_id"));
        verify(pluginRegistrationService).listRegistrations(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void detail_shouldReturnPluginRegistration() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("trace_id", "trace-plugin-detail-1");
        detail.put("plugin", Map.of("registration_id", "face-detector:1.0.0"));
        when(pluginRegistrationService.getRegistration(anyString(), anyString())).thenReturn(detail);

        JsonResult result = pluginApiController.detail("face-detector:1.0.0");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("face-detector:1.0.0", ((Map<String, Object>) data.get("plugin")).get("registration_id"));
        verify(pluginRegistrationService).getRegistration(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void refresh_shouldReturnUpdatedPluginRegistration() {
        Map<String, Object> refreshed = new HashMap<>();
        refreshed.put("trace_id", "trace-plugin-refresh-2");
        refreshed.put("found", true);
        refreshed.put("refreshed", true);
        refreshed.put("plugin", Map.of("status", "ok"));
        when(pluginRegistrationService.refreshRegistration(anyString(), anyString())).thenReturn(refreshed);

        JsonResult result = pluginApiController.refresh("face-detector:1.0.0");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(Boolean.TRUE, data.get("refreshed"));
        assertEquals("ok", ((Map<String, Object>) data.get("plugin")).get("status"));
        verify(pluginRegistrationService).refreshRegistration(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unregister_shouldReturnRemovedState() {
        Map<String, Object> removed = new HashMap<>();
        removed.put("trace_id", "trace-plugin-delete-2");
        removed.put("removed", true);
        when(pluginRegistrationService.unregisterRegistration(anyString(), anyString())).thenReturn(removed);

        JsonResult result = pluginApiController.unregister("face-detector:1.0.0");

        assertEquals(0, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(Boolean.TRUE, data.get("removed"));
        verify(pluginRegistrationService).unregisterRegistration(anyString(), anyString());
    }
}
