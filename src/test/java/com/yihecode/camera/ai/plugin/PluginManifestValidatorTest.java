package com.yihecode.camera.ai.plugin;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManifestValidatorTest {

    @Test
    void validate_shouldPassForValidManifest() {
        PluginManifest manifest = new PluginManifest();
        manifest.setPluginId("face-detector");
        manifest.setVersion("1.0.0");
        manifest.setRuntime("rk3588_rknn");
        manifest.setCapabilities(Arrays.asList("inference", "alert"));

        List<String> errors = PluginManifestValidator.validate(manifest);

        assertTrue(errors.isEmpty(), "expected no validation errors for valid manifest");
    }

    @Test
    void validate_shouldFailWhenRequiredFieldsMissing() {
        PluginManifest manifest = new PluginManifest();
        manifest.setPluginId("   ");
        manifest.setVersion(null);
        manifest.setRuntime("");

        List<String> errors = PluginManifestValidator.validate(manifest);

        assertTrue(errors.size() >= 3, "expected at least 3 errors for missing fields");
        String joined = String.join(";", errors).toLowerCase();
        assertTrue(joined.contains("plugin_id"));
        assertTrue(joined.contains("version"));
        assertTrue(joined.contains("runtime"));
    }

    @Test
    void validate_shouldFailOnInvalidVersionFormat() {
        PluginManifest manifest = new PluginManifest();
        manifest.setPluginId("face-detector");
        manifest.setVersion("1.0");
        manifest.setRuntime("rk3588_rknn");
        manifest.setCapabilities(Arrays.asList("inference"));

        List<String> errors = PluginManifestValidator.validate(manifest);

        assertEquals(1, errors.size());
        String msg = errors.get(0).toLowerCase();
        assertTrue(msg.contains("version"));
        assertTrue(msg.contains("x.y.z"));
    }

    @Test
    void validate_shouldFailWhenCapabilitiesMissing() {
        PluginManifest manifest = new PluginManifest();
        manifest.setPluginId("face-detector");
        manifest.setVersion("1.0.0");
        manifest.setRuntime("rk3588_rknn");
        manifest.setCapabilities(Arrays.asList(" ", null));

        List<String> errors = PluginManifestValidator.validate(manifest);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).toLowerCase().contains("capabilities"));
    }
}
