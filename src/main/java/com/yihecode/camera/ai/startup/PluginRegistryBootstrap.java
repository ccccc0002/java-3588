package com.yihecode.camera.ai.startup;

import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.plugin.PluginManifest;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.PluginRegistrationService;
import com.yihecode.camera.ai.service.PluginRegistryRecord;
import com.yihecode.camera.ai.service.PluginRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class PluginRegistryBootstrap {

    private static final String DEFAULT_PLUGIN_ID = "yolov8n";
    private static final String DEFAULT_PLUGIN_VERSION = "0.1.0";
    private static final String DEFAULT_PLUGIN_RUNTIME = "rk3588_rknn";

    @Autowired
    private ConfigService configService;

    @Autowired(required = false)
    private PluginRegistrationService pluginRegistrationService;

    @Autowired(required = false)
    private PluginRegistryService pluginRegistryService;

    @PostConstruct
    public void init() {
        bootstrapDefaultPlugin();
    }

    void bootstrapDefaultPlugin() {
        if (pluginRegistrationService == null || pluginRegistryService == null) {
            return;
        }
        String baseUrl = trimToNull(configService.getByValTag("infer_service_url"));
        if (baseUrl == null) {
            return;
        }

        String pluginId = firstNonBlank(configService.getByValTag("infer_default_plugin_id"), DEFAULT_PLUGIN_ID);
        if (isPluginRegistered(pluginId)) {
            return;
        }

        PluginManifest manifest = new PluginManifest();
        manifest.setPluginId(pluginId);
        manifest.setVersion(firstNonBlank(configService.getByValTag("infer_default_plugin_version"), DEFAULT_PLUGIN_VERSION));
        manifest.setRuntime(firstNonBlank(configService.getByValTag("infer_default_plugin_runtime"), DEFAULT_PLUGIN_RUNTIME));
        manifest.setCapabilities(resolveCapabilities(configService.getByValTag("infer_default_plugin_capabilities")));
        manifest.setInferUrl(joinUrl(baseUrl, "/v1/infer"));

        String healthUrl = joinUrl(baseUrl, "/health");
        try {
            pluginRegistrationService.register(UUID.randomUUID().toString(), manifest, healthUrl);
            log.info("bootstrapped plugin registration, plugin_id={}, infer_url={}", pluginId, manifest.getInferUrl());
        } catch (Exception ex) {
            log.warn("bootstrap plugin registration failed, plugin_id={}, infer_service_url={}", pluginId, baseUrl, ex);
        }
    }

    private boolean isPluginRegistered(String pluginId) {
        for (PluginRegistryRecord record : pluginRegistryService.list()) {
            if (record != null && pluginId.equalsIgnoreCase(String.valueOf(record.getPluginId()))) {
                return true;
            }
        }
        return false;
    }

    private List<String> resolveCapabilities(String configured) {
        List<String> capabilities = new ArrayList<>();
        String text = trimToNull(configured);
        if (text == null) {
            capabilities.add("inference");
            capabilities.add("object_detection");
            return capabilities;
        }
        for (String item : text.split(",")) {
            String trimmed = trimToNull(item);
            if (trimmed != null && !capabilities.contains(trimmed)) {
                capabilities.add(trimmed);
            }
        }
        if (capabilities.isEmpty()) {
            capabilities.add("inference");
        }
        return capabilities;
    }

    private String joinUrl(String baseUrl, String suffix) {
        String base = trimToNull(baseUrl);
        if (base == null) {
            return null;
        }
        if (base.endsWith("/health")) {
            base = base.substring(0, base.length() - "/health".length());
        }
        if (base.endsWith("/v1/infer")) {
            base = base.substring(0, base.length() - "/v1/infer".length());
        }
        return StrUtil.removeSuffix(base, "/") + suffix;
    }

    private String firstNonBlank(String value, String fallback) {
        return trimToNull(value) == null ? fallback : value.trim();
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
