package com.yihecode.camera.ai.plugin;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class PluginManifestValidator {

    private static final Pattern SEMVER_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    private PluginManifestValidator() {
    }

    public static List<String> validate(PluginManifest manifest) {
        List<String> errors = new ArrayList<>();
        if (manifest == null) {
            errors.add("plugin manifest is required");
            return errors;
        }

        if (StrUtil.isBlank(manifest.getPluginId())) {
            errors.add("plugin_id is required");
        }

        String version = manifest.getVersion();
        if (StrUtil.isBlank(version)) {
            errors.add("version is required");
        } else if (!SEMVER_PATTERN.matcher(version.trim()).matches()) {
            errors.add("version must be in x.y.z format");
        }

        if (StrUtil.isBlank(manifest.getRuntime())) {
            errors.add("runtime is required");
        }

        Set<String> normalizedCapabilities = new LinkedHashSet<>();
        if (manifest.getCapabilities() != null) {
            for (String capability : manifest.getCapabilities()) {
                if (StrUtil.isNotBlank(capability)) {
                    normalizedCapabilities.add(capability.trim());
                }
            }
        }
        if (normalizedCapabilities.isEmpty()) {
            errors.add("capabilities must contain at least one non-blank value");
        }

        return errors;
    }
}
