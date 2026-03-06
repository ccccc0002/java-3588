package com.yihecode.camera.ai.service;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PluginRegistryRecord {

    private String registrationId;
    private String pluginId;
    private String version;
    private String runtime;
    private List<String> capabilities = new ArrayList<>();
    private String healthUrl;
    private Boolean healthy;
    private String status;
    private String storageMode;
    private String traceId;
    private Long updatedAtMs;
}
