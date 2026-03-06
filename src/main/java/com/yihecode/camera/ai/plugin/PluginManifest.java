package com.yihecode.camera.ai.plugin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PluginManifest {

    /**
     * Unique plugin identifier, stable across versions.
     */
    private String pluginId;

    /**
     * Semantic version in x.y.z format.
     */
    private String version;

    /**
     * Runtime environment hint (e.g. rk3588_rknn).
     */
    private String runtime;

    /**
     * Declared capabilities of this plugin (e.g. inference, alert).
     */
    private List<String> capabilities = new ArrayList<>();
}
